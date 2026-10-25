// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.engine

import nl.pe1mew.zangbundel.core.model.GatheringId
import nl.pe1mew.zangbundel.core.model.LeaderState
import kotlin.random.Random

/**
 * The leader's lifecycle: start with the pre-start scan (10.1), run with `seq` and `ts` advancing
 * on every change (10.5), warn once about another gathering in range (10.1 step 6), resume after a
 * restart (10.9), and end with 60 s of final broadcast (10.10).
 *
 * Pure, like [SingerMachine]: events carry the time, effects say what the engine does. Keys,
 * documents, signing and the carousel are the engine's; this decides when and with which `seq`/`ts`.
 */
class LeaderMachine(private val random: Random = Random.Default) {

    sealed interface Event {
        val now: Long
        /** The user starts a gathering; [canScan] false on a device that cannot scan (10.1 step 1). */
        data class StartRequested(override val now: Long, val canScan: Boolean) : Event
        /** Resume a persisted gathering younger than the resume window (10.9). */
        data class ResumeRequested(override val now: Long, val canScan: Boolean, val gid: GatheringId, val seq: Int, val ts: Long) : Event
        data class BeaconSeen(override val now: Long, val gid: GatheringId, val name: String, val ended: Boolean, val relay: Boolean) : Event
        /** The leader changed something: a song, the setlist, a message, a request status, a re-push (10.5). */
        data class Changed(override val now: Long) : Event
        data class EndRequested(override val now: Long) : Event
        data class Tick(override val now: Long) : Event
    }

    sealed interface Effect {
        data object CannotLead : Effect
        /** Scan for 5 s with the service UUID filter (10.1 step 1). */
        data object StartPreStartScan : Effect
        /** Another gathering is running nearby: do not start; show these names and offer to join. */
        data class Blocked(val names: List<String>) : Effect
        /**
         * Start: new key pairs and `gid` (unless resuming), GATT server, beacon, data sets, carousel,
         * and the low-duty scan of 10.1 step 6.
         */
        data class Begin(val resumed: Boolean, val seq: Int, val ts: Long) : Effect
        /** Sign a new state with these values and burst it (10.5 steps 2–4; 10.10 step 1 when [ended]). */
        data class SignAndBurst(val seq: Int, val ts: Long, val ended: Boolean) : Effect
        data class WarnOtherGathering(val name: String) : Effect
        /** 10.10 step 3: refuse writes with `0x88` from now on. */
        data object RejectWrites : Effect
        /** 10.10 step 2: stop advertising and the GATT server; delete the key pairs and singer table. */
        data object StopAndForget : Effect
    }

    var state: LeaderState = LeaderState.IDLE
        private set
    var seq: Int = 0
        private set
    var ts: Long = 0
        private set

    private var startedAt = 0L
    private var scanUntil = 0L
    private var endsAt = 0L
    private var resumedGid: GatheringId? = null
    private var resumeTs = 0L
    private val blockers = LinkedHashSet<String>()
    private var warned = false

    fun handle(event: Event): List<Effect> {
        val fx = ArrayList<Effect>()
        when (event) {
            is Event.StartRequested -> if (state == LeaderState.IDLE || state == LeaderState.ENDED) {
                resumedGid = null
                preStart(event.now, event.canScan, fx)
            }
            is Event.ResumeRequested -> if (state == LeaderState.IDLE || state == LeaderState.ENDED) {
                resumedGid = event.gid
                seq = event.seq
                resumeTs = event.ts
                preStart(event.now, event.canScan, fx)
            }
            is Event.BeaconSeen -> beacon(event, fx)
            is Event.Changed -> if (state == LeaderState.RUNNING) fx += advance(event.now, ended = false)
            is Event.EndRequested -> if (state == LeaderState.RUNNING) {
                state = LeaderState.ENDING
                endsAt = event.now + ENDING_MS
                fx += advance(event.now, ended = true)
                fx += Effect.RejectWrites
            }
            is Event.Tick -> tick(event.now, fx)
        }
        return fx
    }

    private fun preStart(now: Long, canScan: Boolean, fx: MutableList<Effect>) {
        if (!canScan) { fx += Effect.CannotLead; return }    // 10.1 step 1: no scan, no start
        state = LeaderState.PRE_START_SCAN
        scanUntil = now + PRE_START_SCAN_MS
        blockers.clear()
        warned = false
        fx += Effect.StartPreStartScan
    }

    private fun beacon(e: Event.BeaconSeen, fx: MutableList<Effect>) {
        // Ended gatherings and relays never block or warn; a resumed gathering ignores its own beacons.
        if (e.ended || e.relay || e.gid == resumedGid) return
        when (state) {
            LeaderState.PRE_START_SCAN -> blockers += e.name   // regardless of gid (10.1 step 1)
            LeaderState.RUNNING -> if (!warned) { warned = true; fx += Effect.WarnOtherGathering(e.name) }
            else -> {}
        }
    }

    private fun tick(now: Long, fx: MutableList<Effect>) {
        when (state) {
            LeaderState.PRE_START_SCAN -> if (now >= scanUntil) {
                if (blockers.isNotEmpty()) {
                    state = LeaderState.IDLE
                    fx += Effect.Blocked(blockers.toList())
                    return
                }
                state = LeaderState.RUNNING
                if (resumedGid != null) {
                    // 10.9: resume with seq + 1 so a burst follows; ts continues from the record.
                    seq = (seq + 1) and 0xFFFF
                    ts = resumeTs + 1
                    startedAt = now - ts
                } else {
                    seq = random.nextInt(0x10000)               // 10.1 step 2: random u16
                    ts = 0
                    startedAt = now
                }
                fx += Effect.Begin(resumed = resumedGid != null, seq = seq, ts = ts)
            }
            LeaderState.ENDING -> if (now >= endsAt) {
                state = LeaderState.ENDED
                resumedGid = null
                fx += Effect.StopAndForget
            }
            else -> {}
        }
    }

    /** `seq` + 1 (wrapping) and a `ts` that is strictly larger than the last (9.4). */
    private fun advance(now: Long, ended: Boolean): Effect {
        seq = (seq + 1) and 0xFFFF
        ts = maxOf(ts + 1, now - startedAt)
        return Effect.SignAndBurst(seq, ts, ended)
    }

    companion object {
        const val PRE_START_SCAN_MS = 5_000L
        const val ENDING_MS = 60_000L
    }
}

/**
 * Connection slots on the leader (10.8): `max_conn` starts at 6, rises by 1 after 10 consecutive
 * successful connection cycles at the maximum, falls by 1 on a stack failure that looks like
 * exhaustion; `BUSY` while open connections ≥ `max_conn` − 1; `stay` for delegates first and
 * otherwise only while open connections ≤ `max_conn` / 2. The value is remembered per device by the
 * caller, which passes it back as [initialMax].
 */
class SlotPolicy(initialMax: Int = DEFAULT_MAX) {
    var maxConn: Int = initialMax.coerceAtLeast(1)
        private set
    var open: Int = 0
        private set
    private var successesAtMax = 0
    private val openIds = HashSet<Long>()
    /** Connections that were open while the count stood at `max_conn`. */
    private val atMaxDuringCycle = HashSet<Long>()
    private var nextId = 0L

    val busy: Boolean get() = open >= maxConn - 1

    /** A connection arrives: its id, or null when it must be disconnected at once (`max_conn` reached). */
    fun accept(): Long? {
        if (open >= maxConn) return null
        open++
        val id = nextId++
        openIds += id
        if (open == maxConn) atMaxDuringCycle += openIds
        return id
    }

    /** A connection ended; [successful] when its cycle completed without a stack failure. */
    fun release(id: Long, successful: Boolean) {
        if (!openIds.remove(id)) return
        open--
        val wasAtMax = atMaxDuringCycle.remove(id)
        if (!successful) { successesAtMax = 0; return }
        if (wasAtMax && ++successesAtMax >= RAISE_AFTER) { maxConn++; successesAtMax = 0 }
    }

    /** A failure that looks like exhaustion of the stack. */
    fun exhausted() {
        maxConn = (maxConn - 1).coerceAtLeast(1)
        successesAtMax = 0
    }

    fun grantStay(isDelegate: Boolean): Boolean = isDelegate || open <= maxConn / 2

    companion object {
        const val DEFAULT_MAX = 6
        const val RAISE_AFTER = 10
    }
}
