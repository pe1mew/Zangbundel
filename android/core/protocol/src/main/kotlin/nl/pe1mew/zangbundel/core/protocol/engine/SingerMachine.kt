// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.engine

import nl.pe1mew.zangbundel.core.model.GatheringId
import nl.pe1mew.zangbundel.core.model.SingerState
import nl.pe1mew.zangbundel.core.protocol.ProtocolConstants
import nl.pe1mew.zangbundel.core.protocol.frame.Carrier
import nl.pe1mew.zangbundel.core.protocol.payload.DocumentType
import nl.pe1mew.zangbundel.core.protocol.payload.JoinResponse
import kotlin.random.Random

/**
 * The singer's state machine (ble-protocol 3.3) with the procedures that drive it: discover,
 * verify and register (10.2), follow the carousel and repair (10.3, 10.4, 7.6: the rate-based rule
 * of v0.21 for erasure-coded documents), loss of contact
 * (10.9), ended and leaving (10.11).
 *
 * Pure: every [Event] carries the time, [handle] returns the [Effect]s the engine must carry out,
 * and nothing here touches the radio or a clock. Signatures, hashes and frames are checked before
 * events are made: [Event.StateApplied] means a state record verified with the pinned key.
 */
class SingerMachine(private val random: Random = Random.Default) {

    sealed interface Event {
        val now: Long
        data class DiscoveryStarted(override val now: Long) : Event
        /** A beacon (6.1) of any gathering while discovering, or of the joined one afterwards. */
        data class BeaconSeen(override val now: Long, val gid: GatheringId, val seq: Int, val ended: Boolean, val busy: Boolean) : Event
        /** The user picked a gathering; [pk] when it came from the join QR code (6.5), which pins it at once. */
        data class Selected(override val now: Long, val gid: GatheringId, val carrier: Carrier, val pk: ByteArray? = null) : Event
        /** Info collected and `SHA-256(pk)[0..5] == gid` checked (9.2). */
        data class InfoVerified(override val now: Long, val pk: ByteArray, val major: Int) : Event
        /** A state record verified against the pinned key and newer by `ts` (9.4). */
        data class StateApplied(override val now: Long, val seq: Int, val ended: Boolean, val songIsLocal: Boolean) : Event
        /**
         * A new distinct frame of a document version arrived: [have] distinct frames of the [needed]
         * (`k`) that decode it (7.3). Drives the repair rule of 7.6.
         */
        data class FrameProgress(override val now: Long, val doc: Int, val dv: Int, val have: Int, val needed: Int) : Event
        data class DocumentComplete(override val now: Long, val doc: Int, val dv: Int) : Event
        data class JoinAnswered(override val now: Long, val result: Int) : Event
        /** The user entered the join code again after result 1. */
        data class CodeEntered(override val now: Long) : Event
        /** A connection for registration or repair failed (status 133, timeout) or the leader answered `0x84`. */
        data class ConnectionFailed(override val now: Long) : Event
        data class RepairDone(override val now: Long) : Event
        data class Tick(override val now: Long) : Event
        data class LeaveRequested(override val now: Long) : Event
    }

    sealed interface Effect {
        data object StartScan : Effect
        data object StopScan : Effect
        /** Connect after [delayMs], write the sealed Join, read the answer (10.2). */
        data class Register(val delayMs: Long) : Effect
        /** Connect after [delayMs] and fetch the missing ranges of these (`doc`, `dv`) groups (10.4). */
        data class Repair(val groups: List<Pair<Int, Int>>, val delayMs: Long) : Effect
        data class ShowJoinResult(val result: Int) : Effect
        data object ShowUpdateRequired : Effect
        data object ShowReconnecting : Effect
        data object ShowReconnected : Effect
        data object ShowEnded : Effect
        /** Best-effort Control op 10 before leaving (10.11). */
        data object SendLeave : Effect
        /** FR-DATA-01, FR-DATA-06: everything received during the gathering. */
        data object DeleteGatheringData : Effect
        /** FR-DATA-03: the memory from the last verified setlist. */
        data object WriteGatheringMemory : Effect
    }

    var state: SingerState = SingerState.IDLE
        private set
    var gid: GatheringId? = null
        private set
    var registered: Boolean = false
        private set
    var pinnedPk: ByteArray? = null
        private set

    private var carrier = Carrier.LEGACY
    private var lastHeard = 0L
    private var lastSeq: Int? = null
    private var busy = false
    private var songIsLocal = false
    private var stateSeen = false
    private var registerAsked = false
    private var registrationRetryAt: Long? = null
    private var repairRetryAt: Long? = null
    private var backoffMs = BACKOFF_START_MS
    /** Progress of one document version for the repair rule of 7.6. */
    private class Collecting(val since: Long) {
        var lastNew = since
        var have = 0
        var needed: Int? = null
        val samples = ArrayDeque<Pair<Long, Int>>()   // (time, have) over the last RATE_WINDOW_MS
    }

    private val collecting = LinkedHashMap<Pair<Int, Int>, Collecting>()
    private val repairing = ArrayList<Pair<Int, Int>>()

    private val inGathering: Boolean
        get() = state == SingerState.REGISTERING || state == SingerState.LISTENING ||
            state == SingerState.REPAIRING || state == SingerState.RECONNECTING

    fun handle(event: Event): List<Effect> {
        val fx = ArrayList<Effect>()
        when (event) {
            is Event.DiscoveryStarted -> if (state == SingerState.IDLE || state == SingerState.ENDED) { state = SingerState.IDLE; fx += Effect.StartScan }
            is Event.BeaconSeen -> beacon(event, fx)
            is Event.Selected -> select(event)
            is Event.InfoVerified -> info(event, fx)
            is Event.StateApplied -> applied(event, fx)
            is Event.FrameProgress -> if (inGathering) progress(event)
            is Event.DocumentComplete -> if (inGathering) { heard(event.now); collecting.remove(event.doc to event.dv) }
            is Event.JoinAnswered -> if (inGathering && registerAsked) joinAnswered(event, fx)
            is Event.CodeEntered -> if (state == SingerState.REGISTERING) fx += register()
            is Event.ConnectionFailed -> connectionFailed(event)
            is Event.RepairDone -> if (state == SingerState.REPAIRING) {
                state = SingerState.LISTENING
                backoffMs = BACKOFF_START_MS
                repairRetryAt = null
                repairing.clear()
            }
            is Event.Tick -> tick(event.now, fx)
            is Event.LeaveRequested -> when {
                inGathering -> leave(fx)
                state != SingerState.IDLE -> { fx += Effect.StopScan; state = SingerState.IDLE }   // any, user leaves: IDLE (3.3)
            }
        }
        return fx
    }

    // ---- events ----

    private fun beacon(e: Event.BeaconSeen, fx: MutableList<Effect>) {
        if (state == SingerState.IDLE) { state = SingerState.DISCOVERED; return }
        if (!inGathering || e.gid != gid) return
        if (e.ended) { end(fx); return }
        busy = e.busy
        if (state == SingerState.RECONNECTING) { state = if (registered || stateSeen) SingerState.LISTENING else SingerState.REGISTERING; fx += Effect.ShowReconnected }
        heard(e.now)
        if (lastSeq != null && e.seq != lastSeq) {
            // 10.3 step 1: a new seq means a new state record; the repair rule watches it from now.
            collecting.getOrPut(DocumentType.STATE to e.seq) { Collecting(e.now) }
        }
        lastSeq = e.seq
    }

    private fun select(e: Event.Selected) {
        if (state != SingerState.IDLE && state != SingerState.DISCOVERED) return
        state = SingerState.REGISTERING
        gid = e.gid
        carrier = e.carrier
        pinnedPk = e.pk?.copyOf()
        lastHeard = e.now
    }

    private fun info(e: Event.InfoVerified, fx: MutableList<Effect>) {
        if (!inGathering) return
        heard(e.now)
        if (e.major != ProtocolConstants.PROTOCOL_MAJOR) {
            // 10.2: another major version: do not register, show the update message.
            fx += Effect.ShowUpdateRequired
            reset(fx)
            return
        }
        if (pinnedPk == null) pinnedPk = e.pk.copyOf()   // a pk from the QR code is never replaced
    }

    private fun applied(e: Event.StateApplied, fx: MutableList<Effect>) {
        if (!inGathering) return
        heard(e.now)
        stateSeen = true
        lastSeq = e.seq
        songIsLocal = e.songIsLocal
        collecting.keys.removeAll { it.first == DocumentType.STATE }
        if (songIsLocal) collecting.keys.removeAll { it.first == DocumentType.SONG }
        if (e.ended) { end(fx); return }
        // 10.2: Join needs `ek` from a verified state, so registration starts only now.
        if (state == SingerState.REGISTERING && !registerAsked) fx += register()
    }

    private fun progress(e: Event.FrameProgress) {
        heard(e.now)
        if (e.doc == DocumentType.SONG && songIsLocal) return     // 7.6: a singer with the song never repairs it
        val c = collecting.getOrPut(e.doc to e.dv) { Collecting(e.now) }
        c.needed = e.needed
        if (e.have > c.have) { c.have = e.have; c.lastNew = e.now }
        c.samples.addLast(e.now to c.have)
        while (c.samples.size > 1 && c.samples[1].first <= e.now - RATE_WINDOW_MS) c.samples.removeFirst()
    }

    /**
     * 7.6 (v0.21): repair when nothing new has arrived for [NO_PROGRESS_MS], or when, from the rate of
     * new distinct frames over the last [RATE_WINDOW_MS], `k` will not be reached within the carrier's
     * limit; the estimate is first made [RATE_WINDOW_MS] after the first frame.
     */
    private fun repairDue(c: Collecting, now: Long): Boolean {
        if (now - c.lastNew >= NO_PROGRESS_MS) return true
        val needed = c.needed ?: return false
        if (now - c.since < RATE_WINDOW_MS) return false
        val (t0, h0) = c.samples.firstOrNull() ?: return false
        val span = (now - t0).coerceAtLeast(1)
        val rate = (c.have - h0).toDouble() / span                // frames per millisecond
        if (rate <= 0.0) return false                              // covered by NO_PROGRESS_MS
        val etaMs = (needed - c.have) / rate
        return etaMs > etaLimitMs()
    }

    private fun etaLimitMs(): Long = if (carrier == Carrier.EXTENDED) EXTENDED_ETA_LIMIT_MS else LEGACY_ETA_LIMIT_MS

    private fun joinAnswered(e: Event.JoinAnswered, fx: MutableList<Effect>) {
        heard(e.now)
        registerAsked = false
        backoffMs = BACKOFF_START_MS
        when (e.result) {
            JoinResponse.ACCEPTED -> {
                registered = true
                registrationRetryAt = null
                if (state == SingerState.REGISTERING) state = SingerState.LISTENING
            }
            JoinResponse.FULL, JoinResponse.TRY_LATER -> {
                // 10.2: keep listening, retry registration every 30–60 s in the background.
                if (state == SingerState.REGISTERING) state = SingerState.LISTENING
                registrationRetryAt = e.now + random.nextLong(REGISTRATION_RETRY_MIN_MS, REGISTRATION_RETRY_MAX_MS + 1)
                fx += Effect.ShowJoinResult(e.result)
            }
            JoinResponse.WRONG_CODE -> fx += Effect.ShowJoinResult(e.result)   // waits for CodeEntered
            JoinResponse.VERSION_NOT_SUPPORTED -> { fx += Effect.ShowUpdateRequired; reset(fx) }
            JoinResponse.ENDED -> end(fx)
        }
    }

    private fun connectionFailed(e: Event.ConnectionFailed) {
        // 10.4: exponential backoff from 300 ms, doubling, capped at 2 s, ±25 % jitter.
        val delay = jitter(backoffMs)
        backoffMs = minOf(backoffMs * 2, BACKOFF_CAP_MS)
        when {
            state == SingerState.REGISTERING -> { registerAsked = false; registrationRetryAt = e.now + delay }
            registerAsked && !registered -> {
                // A background registration retry (results 2 and 5) failed to connect: next long retry.
                registerAsked = false
                registrationRetryAt = e.now + random.nextLong(REGISTRATION_RETRY_MIN_MS, REGISTRATION_RETRY_MAX_MS + 1)
            }
            state == SingerState.REPAIRING -> repairRetryAt = e.now + delay
        }
    }

    private fun tick(now: Long, fx: MutableList<Effect>) {
        if (!inGathering) return
        val silent = now - lastHeard
        if (state == SingerState.RECONNECTING) {
            if (silent >= ENDED_AFTER_MS) end(fx)      // 10.9: 5 minutes without anything
            return
        }
        if (silent >= RECONNECTING_AFTER_MS) {       // 10.9: nothing for 10 s
            state = SingerState.RECONNECTING
            fx += Effect.ShowReconnecting
            return
        }
        registrationRetryAt?.let { at ->
            if (now >= at && !registered && stateSeen) { registrationRetryAt = null; fx += register() }
        }
        when (state) {
            SingerState.LISTENING -> {
                val due = collecting.filter { (_, c) -> repairDue(c, now) }.keys.toList()
                if (due.isNotEmpty()) {
                    due.forEach { collecting.remove(it) }
                    repairing.clear(); repairing += due
                    state = SingerState.REPAIRING
                    fx += Effect.Repair(due, repairDelay())
                }
            }
            SingerState.REPAIRING -> repairRetryAt?.let { at ->
                if (now >= at) { repairRetryAt = null; fx += Effect.Repair(repairing.toList(), 0) }
            }
            else -> {}
        }
    }

    private fun leave(fx: MutableList<Effect>) {
        if (registered) fx += Effect.SendLeave
        fx += Effect.DeleteGatheringData
        fx += Effect.WriteGatheringMemory
        reset(fx)
    }

    // ---- helpers ----

    private fun end(fx: MutableList<Effect>) {
        fx += Effect.ShowEnded
        fx += Effect.DeleteGatheringData
        fx += Effect.WriteGatheringMemory
        fx += Effect.StopScan
        clear()
        state = SingerState.ENDED
    }

    /** Back to IDLE, forgetting the gathering: token, `sid`, `xk` and the pinned `pk` go with it (10.11). */
    private fun reset(fx: MutableList<Effect>) {
        fx += Effect.StopScan
        clear()
        state = SingerState.IDLE
    }

    private fun clear() {
        gid = null; pinnedPk = null; registered = false; registerAsked = false; stateSeen = false
        lastSeq = null; songIsLocal = false; busy = false
        registrationRetryAt = null; repairRetryAt = null; backoffMs = BACKOFF_START_MS
        collecting.clear(); repairing.clear()
    }

    private fun register(): Effect { registerAsked = true; return Effect.Register(repairDelay()) }

    private fun heard(now: Long) { lastHeard = maxOf(lastHeard, now) }

    /** 7.6: random 0–400 ms before connecting, 0–2 s while the beacon shows `BUSY`. */
    private fun repairDelay(): Long = random.nextLong(0, (if (busy) BUSY_DELAY_MAX_MS else DELAY_MAX_MS) + 1)

    private fun jitter(ms: Long): Long = (ms * random.nextDouble(0.75, 1.25)).toLong()

    companion object {
        const val RECONNECTING_AFTER_MS = 10_000L
        const val ENDED_AFTER_MS = 5 * 60_000L
        const val REGISTRATION_RETRY_MIN_MS = 30_000L
        const val REGISTRATION_RETRY_MAX_MS = 60_000L
        const val BACKOFF_START_MS = 300L
        const val BACKOFF_CAP_MS = 2_000L
        const val DELAY_MAX_MS = 400L
        const val BUSY_DELAY_MAX_MS = 2_000L
        /** 7.6 (v0.21): nothing new for this long means repair. */
        const val NO_PROGRESS_MS = 3_000L
        /** 7.6 (v0.21): the window over which a singer measures its rate of new distinct frames. */
        const val RATE_WINDOW_MS = 2_000L
        /** 7.6 (v0.21): repair when `k` is not expected within this long, by carrier. */
        const val LEGACY_ETA_LIMIT_MS = 10_000L
        const val EXTENDED_ETA_LIMIT_MS = 4_000L
    }
}
