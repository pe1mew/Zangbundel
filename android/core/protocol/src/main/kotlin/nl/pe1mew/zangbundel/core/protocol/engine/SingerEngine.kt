// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import nl.pe1mew.zangbundel.core.model.GatheringId
import nl.pe1mew.zangbundel.core.model.SongRef
import nl.pe1mew.zangbundel.core.protocol.Crypto
import nl.pe1mew.zangbundel.core.protocol.ProtocolConstants
import nl.pe1mew.zangbundel.core.protocol.Transport
import nl.pe1mew.zangbundel.core.protocol.beacon.Beacon
import nl.pe1mew.zangbundel.core.protocol.beacon.BeaconCodec
import nl.pe1mew.zangbundel.core.protocol.frame.Carrier
import nl.pe1mew.zangbundel.core.protocol.frame.FrameCodec
import nl.pe1mew.zangbundel.core.protocol.frame.Reassembler
import nl.pe1mew.zangbundel.core.protocol.gatt.GattClient
import nl.pe1mew.zangbundel.core.protocol.payload.ByteRange
import nl.pe1mew.zangbundel.core.protocol.payload.ControlCommand
import nl.pe1mew.zangbundel.core.protocol.payload.Deflate
import nl.pe1mew.zangbundel.core.protocol.payload.DocEntry
import nl.pe1mew.zangbundel.core.protocol.payload.DocumentType
import nl.pe1mew.zangbundel.core.protocol.payload.FieldSizes
import nl.pe1mew.zangbundel.core.protocol.payload.Info
import nl.pe1mew.zangbundel.core.protocol.payload.JoinRequest
import nl.pe1mew.zangbundel.core.protocol.payload.PayloadLimits
import nl.pe1mew.zangbundel.core.protocol.payload.SignedState
import nl.pe1mew.zangbundel.core.protocol.payload.StateBody
import nl.pe1mew.zangbundel.core.protocol.payload.TransferRequest
import kotlin.random.Random

/**
 * Runs [SingerMachine] against a [Transport] (ble-protocol 7.5, 10.2–10.4): beacons and frames from
 * the scan go to the reassembler; completed documents are verified (info by `gid`, state by
 * signature, the rest by `dh` against the newest verified manifest) before the machine hears of
 * them; its Register and Repair effects become GATT connections through [GattClient].
 *
 * [chooses] picks the gathering to join from the beacons seen, standing in for the user's tap. The
 * engine keeps the song as bytes; parsing and showing it is the app's.
 */
class SingerEngine(
    private val transport: Transport,
    private val crypto: Crypto,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val chooses: (Beacon) -> Boolean,
    private val name: String? = null,
    /** Whether the singer's library holds this song (10.3 step 3); the app answers from its index. */
    private val hasSong: (SongRef) -> Boolean = { false },
    private val random: Random = Random.Default,
    /**
     * The leader's key from a join QR code (6.5): pinned before anything is heard, so only a state signed
     * by it is accepted and a key from Info is never used. Null when joining from the list.
     */
    pinnedKey: ByteArray? = null,
    /** The join code (FR-GATH-05), from the QR code or typed; sent inside the sealed Join. */
    @Volatile var code: String? = null,
) {
    private val pinnedKey: ByteArray? = pinnedKey?.copyOf()
    /** States heard with the right `gid` that the pinned key does not sign (6.5); reported once (D-35). */
    private var foreignStates = 0
    sealed interface Event {
        val at: Long
        data class NewSeq(override val at: Long, val seq: Int) : Event
        /** No extended frame came within 3 s; the singer collects the legacy carrier now (7.1, v0.23). */
        data class FellBackToLegacy(override val at: Long) : Event
        data class Joined(override val at: Long, val result: Int) : Event
        /** A verified document, by broadcast or by repair. */
        class Delivered(override val at: Long, val doc: Int, val dv: Int, val bytes: ByteArray, val viaRepair: Boolean, val enc: Int = Deflate.ENC_RAW) : Event {
            /** The document itself (7.3): [bytes] as framed, inflated when the manifest says DEFLATE, never past [maxOutput]. */
            fun document(maxOutput: Int = PayloadLimits.DOCUMENT_DECODED): ByteArray = Deflate.unframe(enc, bytes, maxOutput)
        }
        /** 6.5, guiDesign D-35: a gathering with the code's `gid` sends states its key does not sign: not the campfire of the QR code. */
        data class NotTheCampfire(override val at: Long) : Event
        /** 10.11: the gathering ended, by the beacon's `ENDED`, a final state, a refused Join, or 5 minutes of silence. */
        data class Ended(override val at: Long) : Event
        /** The current song is in the singer's own library (FR-BLE-04 with a local copy, 10.3 step 3); nothing to collect. */
        data class LocalSong(override val at: Long, val ref: SongRef) : Event
        /** One repair connection: what it asked for, whether it got through, how long it took, and why not ([failure]). */
        data class Repaired(override val at: Long, val groups: List<Pair<Int, Int>>, val ok: Boolean, val ms: Long, val failure: String? = null) : Event
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 256)
    val events: SharedFlow<Event> = _events

    val machine = SingerMachine(random)
    private val mutex = Mutex()
    /** The carrier this singer collects: extended when the radio scans it and the beacon sets `EXT` (7.5). */
    var carrier = Carrier.LEGACY
        private set
    private var gid: GatheringId? = null
    private var gid16 = 0
    private var selectedAt = 0L
    private var extendedHeard = false
    private var leader: Transport.DeviceAddress? = null
    private var reassembler: Reassembler? = null
    private var pk: ByteArray? = null
    private var lastSeq: Int? = null
    var state: SignedState? = null
        private set
    private var pendingState: Pair<Int, ByteArray>? = null
    private val pendingDocs = LinkedHashMap<Pair<Int, Int>, ByteArray>()
    /** Documents 3–7 delivered, by (`doc`, `dv`). */
    private val held = HashSet<Pair<Int, Int>>()
    private val sid = crypto.randomBytes(FieldSizes.SID)
    private val xk = crypto.generateBoxKeyPair()
    var token: ByteArray? = null
        private set

    /** One row of the reception bar (FR-SING-05, guiDesign D-81): a document and its source blocks. */
    class Reception(val doc: Int, val blocks: List<Reassembler.Block>)

    /**
     * The reception bar's rows: the current song, then the setlist. Each follows the newest version
     * being collected while it is incomplete (a push shows at once, before its state is applied), and
     * otherwise the version the newest verified manifest names. A document held in full, or a song from
     * the singer's own library, is one received block; a version with no frame yet has no blocks. A
     * document neither named nor seen has no row.
     */
    suspend fun reception(): List<Reception> = mutex.withLock {
        val rs = reassembler ?: return@withLock emptyList()
        listOf(DocumentType.SONG, DocumentType.SETLIST).mapNotNull { doc ->
            val s = state
            if (doc == DocumentType.SONG && s?.body?.cur?.let(hasSong) == true) return@mapNotNull Reception(doc, listOf(Reassembler.Block.RECEIVED))
            val latest = rs.latestVersion(doc)?.takeIf { (doc to it) !in held && !rs.isComplete(doc, it) }
            val dv = latest ?: s?.body?.docs?.get(doc)?.dv ?: rs.latestVersion(doc) ?: return@mapNotNull null
            val blocks = rs.blocks(doc, dv)
            when {
                blocks != null -> Reception(doc, blocks)
                (doc to dv) in held -> Reception(doc, listOf(Reassembler.Block.RECEIVED))
                else -> Reception(doc, emptyList())
            }
        }
    }

    /**
     * Leaving on purpose (FR-GATH-07, 10.11 as of v0.29): a registered singer tells the leader with
     * Control op 10 so the count drops at once, retrying after a failed connection until it is through
     * or [budgetMs] have passed; the engine stops listening first. The caller need not wait: the user is
     * home already and has discarded what the gathering left (FR-DATA-01). Returns whether the leader
     * accepted the notice.
     */
    suspend fun leave(budgetMs: Long = LEAVE_BUDGET_MS): Boolean {
        val fx = mutex.withLock { machine.handle(SingerMachine.Event.LeaveRequested(clock())) }
        mutex.withLock { apply(fx.filter { it != SingerMachine.Effect.SendLeave }) }
        if (SingerMachine.Effect.SendLeave !in fx) return false
        val deadline = clock() + budgetMs
        while (true) {
            val left = deadline - clock()
            if (left <= 0) return false
            when (withTimeoutOrNull(left) { sendLeave() }) {
                Sent.YES -> return true
                Sent.REFUSED, null -> return false              // refused (0x80, 0x88), or out of time
                Sent.UNREACHABLE -> delay(LEAVE_RETRY_MS)
            }
        }
    }

    private enum class Sent { YES, REFUSED, UNREACHABLE }

    private suspend fun sendLeave(): Sent {
        val (address, g, tok, ek) = mutex.withLock { listOf(leader, gid, token, state?.body?.ek) }
        if (address !is Transport.DeviceAddress || g !is GatheringId || tok !is ByteArray || ek !is ByteArray) return Sent.REFUSED
        val client = try { GattClient(transport.connect(address), crypto) } catch (e: Transport.GattException) { return Sent.UNREACHABLE }
        return try {
            client.control(ControlCommand(tok, random.nextBytes(FieldSizes.COMMAND_ID), ControlCommand.LEAVE, gid = g), ek)
            Sent.YES
        } catch (e: Transport.GattException) {
            // 10.11: refused (0x80, 0x88) or the link broke; the singer has left anyway and the leader's 10-minute rule catches up.
            if (e.status == Transport.CONNECTION_FAILED) Sent.UNREACHABLE else Sent.REFUSED
        } finally {
            runCatching { client.close() }
        }
    }

    /** After a wrong join code (FR-GATH-05): registers again with [newCode]. */
    suspend fun enterCode(newCode: String) {
        code = newCode
        mutex.withLock { apply(machine.handle(SingerMachine.Event.CodeEntered(clock()))) }
    }

    /** Starts scanning and the 250 ms tick; runs until [scope] is cancelled. */
    fun start() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            transport.scanResults().collect { r -> mutex.withLock { scanned(r) } }
        }
        scope.launch {
            mutex.withLock { apply(machine.handle(SingerMachine.Event.DiscoveryStarted(clock()))) }
            while (isActive) {
                delay(TICK_MS)
                mutex.withLock {
                    apply(machine.handle(SingerMachine.Event.Tick(clock())))
                    fallBack(clock())
                }
            }
        }
    }

    // ---- scan results ----

    private fun scanned(r: Transport.ScanResult) {
        val data = r.manufacturerData ?: return
        val now = clock()
        val beacon = BeaconCodec.decodeA(data)
        if (beacon != null) {
            if (gid == null) {
                if (!chooses(beacon)) return
                gid = beacon.gid
                gid16 = ((beacon.gid.bytes[0].toInt() and 0xFF) shl 8) or (beacon.gid.bytes[1].toInt() and 0xFF)
                selectedAt = now
                carrier = if (transport.capabilities.extendedAdvertising && beacon.has(ProtocolConstants.FLAG_EXT)) Carrier.EXTENDED else Carrier.LEGACY
                reassembler = Reassembler(gid16, carrier)
                apply(machine.handle(SingerMachine.Event.BeaconSeen(now, beacon.gid, beacon.seq, beacon.ended, beacon.busy, beacon.has(ProtocolConstants.FLAG_BCAST))))
                pinnedKey?.let { pk = it.copyOf() }
                apply(machine.handle(SingerMachine.Event.Selected(now, beacon.gid, carrier, pinnedKey)))
            }
            if (beacon.gid != gid) return
            leader = r.address
            if (lastSeq != null && beacon.seq != lastSeq) _events.tryEmit(Event.NewSeq(now, beacon.seq))
            lastSeq = beacon.seq
            apply(machine.handle(SingerMachine.Event.BeaconSeen(now, beacon.gid, beacon.seq, beacon.ended, beacon.busy, beacon.has(ProtocolConstants.FLAG_BCAST))))
            return
        }
        val rs = reassembler ?: return
        val frame = FrameCodec.decode(data) ?: return
        if (frame.carrier == Carrier.EXTENDED && frame.gid16 == gid16) extendedHeard = true
        when (val result = rs.accept(frame)) {
            is Reassembler.Result.Stored -> rs.progress(frame.doc, frame.dv)?.let { (have, needed) ->
                apply(machine.handle(SingerMachine.Event.FrameProgress(now, frame.doc, frame.dv, have, needed)))
            }
            is Reassembler.Result.Complete -> completed(result, viaRepair = false)
            else -> {}
        }
    }

    /**
     * 7.1 (v0.23, Q23): on the extended carrier, no extended frame of the gathering within
     * [CARRIER_FALLBACK_MS] of selecting it means the leader's extended set is not heard; collect the
     * legacy carrier for the rest of the gathering.
     */
    private fun fallBack(now: Long) {
        if (carrier != Carrier.EXTENDED || extendedHeard || gid == null || now - selectedAt < CARRIER_FALLBACK_MS) return
        carrier = Carrier.LEGACY
        reassembler = Reassembler(gid16, Carrier.LEGACY)
        apply(machine.handle(SingerMachine.Event.CarrierChanged(now, Carrier.LEGACY)))
        _events.tryEmit(Event.FellBackToLegacy(now))
    }

    // ---- verification (7.5) ----

    private fun completed(c: Reassembler.Result.Complete, viaRepair: Boolean) {
        val now = clock()
        when (c.doc) {
            DocumentType.INFO -> {
                val info = try { Info.decode(c.bytes) } catch (e: Exception) { null }
                if (info == null || info.gid != gid || !info.pkMatchesGid(crypto)) { reassembler?.reset(c.doc, c.dv); return }
                // 6.5: with a key from the QR code, an Info naming another key belongs to another gathering.
                if (pinnedKey != null && !info.pk.contentEquals(pinnedKey)) { reassembler?.reset(c.doc, c.dv); return }
                if (pk == null) pk = info.pk.copyOf()
                apply(machine.handle(SingerMachine.Event.InfoVerified(now, info.pk, info.major)))
                deliver(c, viaRepair)
                pendingState?.let { (dv, bytes) -> pendingState = null; state(dv, bytes, viaRepair) }
            }
            DocumentType.STATE -> if (pk == null) pendingState = c.dv to c.bytes else state(c.dv, c.bytes, viaRepair)
            else -> {
                val manifest = state?.body?.docs?.get(c.doc)
                if (manifest == null) { pendingDocs[c.doc to c.dv] = c.bytes; return }
                if (!DocEntry.hashOf(c.bytes, crypto).contentEquals(manifest.dh)) { reassembler?.reset(c.doc, c.dv); return }
                held += c.doc to c.dv
                deliver(c, viaRepair)
            }
        }
    }

    private fun state(dv: Int, bytes: ByteArray, viaRepair: Boolean) {
        val s = try { SignedState.verify(bytes, pk!!, gid!!, crypto) } catch (e: Exception) {
            reassembler?.reset(DocumentType.STATE, dv)
            if (pinnedKey != null && ++foreignStates == FOREIGN_STATES_TO_REPORT) _events.tryEmit(Event.NotTheCampfire(clock()))
            return
        }
        if (!s.supersedes(state?.body?.ts)) return
        val wasLocal = state?.body?.cur?.let(hasSong) == true && state?.body?.cur == s.body.cur
        state = s
        val local = s.body.cur?.let(hasSong) == true
        if (local && !wasLocal) _events.tryEmit(Event.LocalSong(clock(), s.body.cur!!))
        val missing = s.body.docs.map { (doc, entry) -> doc to entry.dv }.filter { it !in held && it !in pendingDocs.keys }
        apply(machine.handle(SingerMachine.Event.StateApplied(clock(), s.body.seq, s.body.status == StateBody.STATUS_ENDED, songIsLocal = local, missing = missing)))
        deliver(Reassembler.Result.Complete(DocumentType.STATE, s.body.seq, bytes), viaRepair)
        // Documents completed before the manifest that lists them.
        val waiting = pendingDocs.entries.toList()
        pendingDocs.clear()
        waiting.forEach { (k, v) -> completed(Reassembler.Result.Complete(k.first, k.second, v), viaRepair) }
    }

    private fun deliver(c: Reassembler.Result.Complete, viaRepair: Boolean) {
        val now = clock()
        val enc = state?.body?.docs?.get(c.doc)?.enc ?: Deflate.ENC_RAW // states and Info are never framed
        _events.tryEmit(Event.Delivered(now, c.doc, c.dv, c.bytes, viaRepair, enc))
        apply(machine.handle(SingerMachine.Event.DocumentComplete(now, c.doc, c.dv)))
    }

    // ---- effects ----

    private fun apply(effects: List<SingerMachine.Effect>) {
        for (e in effects) when (e) {
            SingerMachine.Effect.StartScan -> scope.launch { transport.startScan(Transport.ScanMode.GATHERING) }
            SingerMachine.Effect.StopScan -> scope.launch { transport.stopScan() }
            is SingerMachine.Effect.Register -> scope.launch { delay(e.delayMs); register() }
            is SingerMachine.Effect.Repair -> scope.launch { delay(e.delayMs); repair(e.groups) }
            SingerMachine.Effect.ShowEnded -> _events.tryEmit(Event.Ended(clock()))
            else -> {}        // what the user sees is the app's
        }
    }

    /**
     * 10.2: connect; read Info and State first if the carousel has not delivered them (the state must
     * be verified before Join, because `ek` comes from it); write the sealed Join, read the answer.
     */
    private suspend fun register() {
        val (address, g) = mutex.withLock { leader to gid }
        if (address == null || g == null) return
        val caps = JoinRequest.CAP_LEGACY or (if (transport.capabilities.extendedAdvertising) JoinRequest.CAP_EXTENDED else 0)
        val request = JoinRequest(ProtocolConstants.PROTOCOL_MAJOR, ProtocolConstants.PROTOCOL_MINOR, sid, name = name, code = code, caps = caps, gid = g, xk = xk.publicKey)
        val client = try { GattClient(transport.connect(address), crypto) } catch (e: Transport.GattException) { failed(); return }
        try {
            if (mutex.withLock { pk == null }) {
                val info = client.readInfo()
                mutex.withLock { if (pk == null) completed(Reassembler.Result.Complete(DocumentType.INFO, DocEntry.hashOf(info, crypto).let { ((it[0].toInt() and 0xFF) shl 8) or (it[1].toInt() and 0xFF) }, info), viaRepair = true) }
            }
            var stateRead = false
            if (mutex.withLock { state == null }) {
                val bytes = client.readState()
                stateRead = mutex.withLock { if (state == null && pk != null) state(lastSeq ?: 0, bytes, viaRepair = true); state != null }
            }
            val s = mutex.withLock { state }
            if (s == null) { failed(); return }
            val response = client.join(request, s.body.ek, xk)
            mutex.withLock {
                if (response.tok != null) token = response.tok
                apply(machine.handle(SingerMachine.Event.JoinAnswered(clock(), response.result)))
            }
            _events.tryEmit(Event.Joined(clock(), response.result))
            // 10.2 (v0.23): the state came on this connection; fetch here what its manifest names and no frame of has come.
            if (stateRead) transferAll(client, mutex.withLock { unheardDocuments() })
        } catch (e: Transport.GattException) {
            failed()
        } finally {
            runCatching { client.close() }
        }
    }

    /** 10.4: one connection, one Transfer per document still incomplete, only the ranges still needed. */
    private suspend fun repair(groups: List<Pair<Int, Int>>) {
        val started = clock()
        val address = mutex.withLock { leader } ?: return
        val client = try { GattClient(transport.connect(address), crypto) } catch (e: Transport.GattException) { failed(); return }
        var ok = true
        var failure: String? = null
        try {
            failure = transferAll(client, groups)
            ok = failure == null
        } catch (e: Transport.GattException) {
            ok = false
            failure = "status ${e.status}: ${e.message}"
        } finally {
            runCatching { client.close() }
        }
        _events.tryEmit(Event.Repaired(clock(), groups, ok, clock() - started, failure))
        mutex.withLock {
            apply(machine.handle(if (ok) SingerMachine.Event.RepairDone(clock()) else SingerMachine.Event.ConnectionFailed(clock())))
        }
    }

    /**
     * One Transfer per group (10.4). When a new state arrives on this connection, the documents its
     * manifest names of which no frame is held are fetched on it too (v0.23). Returns why a transfer
     * failed, or null; a version the leader no longer has (`0x86`) is not a failure.
     */
    private suspend fun transferAll(client: GattClient, groups: List<Pair<Int, Int>>): String? {
        val queue = ArrayDeque(groups)
        val asked = HashSet<Pair<Int, Int>>()
        var failure: String? = null
        while (queue.isNotEmpty()) {
            val (doc, dv) = queue.removeFirst()
            if (!asked.add(doc to dv)) continue
            val (before, request) = mutex.withLock { state to requestFor(doc, dv) }
            if (request == null) continue
            when (val t = client.transfer(request)) {
                is GattClient.Transfer.Done -> mutex.withLock {
                    val rs = reassembler ?: return@withLock
                    for ((range, bytes) in t.ranges) {
                        val r = rs.acceptRange(doc, t.header.dv, range.off, bytes, t.header.len)
                        if (r is Reassembler.Result.Complete) completed(r, viaRepair = true)
                    }
                }
                is GattClient.Transfer.Failed -> if (t.code != ProtocolConstants.ERR_UNKNOWN_DOCUMENT) {
                    failure = "doc $doc: " + (t.code?.let { "code 0x%02x ".format(it) } ?: "") + t.why
                }
            }
            mutex.withLock { if (state !== before) queue.addAll(unheardDocuments()) }
        }
        return failure
    }

    /** Documents the newest verified manifest names that the singer neither holds nor has a frame of. */
    private fun unheardDocuments(): List<Pair<Int, Int>> {
        val s = state ?: return emptyList()
        val rs = reassembler ?: return emptyList()
        return s.body.docs.map { (doc, entry) -> doc to entry.dv }
            .filter { it !in held && !rs.isComplete(it.first, it.second) && rs.progress(it.first, it.second) == null }
            .filter { !(it.first == DocumentType.SONG && s.body.cur?.let(hasSong) == true) }
    }

    /** The Transfer for one group, or null when nothing is missing or a song has no `cur.hash` to name. */
    private fun requestFor(doc: Int, dv: Int): TransferRequest? {
        val rs = reassembler ?: return null
        if (rs.isComplete(doc, dv)) return null
        val hash = if (doc == DocumentType.SONG) state?.body?.cur?.hash?.bytes ?: return null else null
        val ranges = rs.missingRanges(doc, dv)?.map { (off, len) -> ByteRange(off, len) }
        return TransferRequest(doc, dv, hash, ranges)
    }

    private suspend fun failed() = mutex.withLock { apply(machine.handle(SingerMachine.Event.ConnectionFailed(clock()))) }

    companion object {
        const val TICK_MS = 250L
        /** A forged state can also be a damaged one; three in a row are reported (D-35). */
        const val FOREIGN_STATES_TO_REPORT = 3
        /** 10.11 (v0.29): the leave notice is tried, in the background, for at most this long. */
        const val LEAVE_BUDGET_MS = 10_000L
        const val LEAVE_RETRY_MS = 300L
        /** 7.1 (v0.23): no extended frame within this long of selecting the gathering: fall back to legacy. */
        const val CARRIER_FALLBACK_MS = 3_000L
    }
}
