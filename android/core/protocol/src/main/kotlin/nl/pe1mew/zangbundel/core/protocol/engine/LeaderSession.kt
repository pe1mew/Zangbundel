// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.pe1mew.zangbundel.core.model.ContentHash
import nl.pe1mew.zangbundel.core.model.GatheringId
import nl.pe1mew.zangbundel.core.model.LibraryId
import nl.pe1mew.zangbundel.core.model.SongId
import nl.pe1mew.zangbundel.core.model.SongRef
import nl.pe1mew.zangbundel.core.protocol.Crypto
import nl.pe1mew.zangbundel.core.protocol.ProtocolConstants
import nl.pe1mew.zangbundel.core.protocol.Transport
import nl.pe1mew.zangbundel.core.protocol.beacon.Beacon
import nl.pe1mew.zangbundel.core.protocol.beacon.BeaconCodec
import nl.pe1mew.zangbundel.core.protocol.carousel.Carousel
import nl.pe1mew.zangbundel.core.protocol.frame.FrameCodec
import nl.pe1mew.zangbundel.core.protocol.gatt.LeaderGattService
import nl.pe1mew.zangbundel.core.protocol.payload.Deflate
import nl.pe1mew.zangbundel.core.protocol.payload.DocEntry
import nl.pe1mew.zangbundel.core.protocol.payload.DocumentType
import nl.pe1mew.zangbundel.core.protocol.payload.Info
import nl.pe1mew.zangbundel.core.protocol.payload.SetlistDocument
import nl.pe1mew.zangbundel.core.protocol.payload.SignedState
import nl.pe1mew.zangbundel.core.protocol.payload.SongDocument
import nl.pe1mew.zangbundel.core.protocol.payload.StateBody
import nl.pe1mew.zangbundel.core.protocol.payload.gatheringIdOf
import kotlin.random.Random

/**
 * One running gathering on the leader (ble-protocol 10.1 steps 2–5, 10.5, 10.10): its keys and `gid`,
 * Info, the signed state, the carousel on the data sets the radio grants, and [LeaderGattService]
 * behind them, kept in step on every [push].
 *
 * The setlist's songs go out ahead from the start (7.4 rule 7, FR-BLE-18), so a push only switches.
 * Not wired in yet: [LeaderMachine]'s lifecycle (pre-start scan, resume), messages and requests.
 */
class LeaderSession(
    private val transport: Transport,
    private val crypto: Crypto,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    val name: String,
    private val extendedOnly: Boolean = false,
    random: Random = Random.Default,
    maxSingers: Int = LeaderGattService.DEFAULT_MAX_SINGERS,
    /** FR-GATH-05: when set, a Join must carry it; the beacon sets `CODE` (6.2). Fixed for the gathering (guiDesign D-71). */
    val joinCode: String? = null,
) {
    /**
     * What the singers get (9.4, 9.5): the current song and the setlist as far as it has been sung
     * (guiDesign D-47: nothing upcoming). [song] is null for a song its library marks "do not
     * redistribute" [FR-LIB-22]: the state then carries [cur] and [title] only.
     *
     * [ahead] (v0.32, FR-BLE-18): every song of the setlist in setlist order, null where it may not be
     * broadcast, sent ahead as document 9 for the rest of the gathering; null keeps the list given before.
     * Each must be the document 3 the song is pushed with, so the two frame to the same bytes (9.5).
     */
    class Publication(
        val cur: SongRef? = null,
        val title: String? = null,
        val song: SongDocument? = null,
        val setlist: SetlistDocument? = null,
        val ahead: List<SongDocument?>? = null,
    ) {
        init { require(song == null || song.ref == cur) { "the song document is the current song" } }
    }

    private val signing = crypto.generateSigningKeyPair()
    val gid: GatheringId = gatheringIdOf(signing.publicKey, crypto)
    /** The gathering's public key, for the fingerprint words and the join QR code (FR-GATH-18, ble-protocol 6.5). */
    val publicKey: ByteArray get() = signing.publicKey.copyOf()
    private val gid16 = ((gid.bytes[0].toInt() and 0xFF) shl 8) or (gid.bytes[1].toInt() and 0xFF)
    private val ek = crypto.generateBoxKeyPair()
    private val mutex = Mutex()
    private var flags = 0
    private var busy = false
    private var ended = false
    var seq: Int = random.nextInt(0x10000)
        private set
    private var ts = 0L
    private val startedAt = clock()
    private var carousel: Carousel? = null
    private var loop: Job? = null
    private var last: Built = Built(null, null, emptyMap())
    /** The setlist's songs as documents 9, per setlist position (7.4 rule 7); null where not broadcast. */
    private var songsAhead: List<Framed?> = emptyList()

    val service = LeaderGattService(gid, ek, crypto, scope, clock, maxSingers = maxSingers, joinCode = joinCode, onBusyChanged = { b -> scope.launch { busyChanged(b) } })

    /** Singers registered and not left or timed out (10.9). */
    val singers: Int get() = service.singerCount

    var legacySets: Int = 0
        private set
    var extendedSet: Boolean = false
        private set
    var framesSent: Long = 0
        private set

    /**
     * How far the current song has gone out (guiDesign D-56): the burst passes the carousel has sent
     * since the push, on the slowest set in use, and how often singers fetched missing pieces of it
     * over GATT. Broadcast is unacknowledged, so who received it by air alone is not known. Null when
     * the song is not broadcast (none pushed, or display-only, FR-LIB-22).
     */
    data class PushProgress(val passesDone: Int, val passes: Int, val repairs: Int)

    suspend fun progress(): PushProgress? = mutex.withLock {
        val dv = last.docs[DocumentType.SONG]?.entry?.dv ?: return@withLock null
        val c = carousel ?: return@withLock null
        val passes = activeLanes().minOfOrNull { c.burstPassesDone(it) } ?: Carousel.BURST_PASSES
        PushProgress(passes, Carousel.BURST_PASSES, service.transfersOf(DocumentType.SONG, dv))
    }

    /**
     * guiDesign D-56 (v0.21): the share of registered singers the leader helped over GATT in the last
     * [windowMs], 0 to 1. Not a delivery count: singers who hear the air well never connect.
     */
    fun helpShare(windowMs: Long = HELP_WINDOW_MS): Float {
        val n = service.singerCount
        return if (n == 0) 0f else (service.helpedWithin(windowMs).toFloat() / n).coerceAtMost(1f)
    }

    private fun activeLanes(): List<Carousel.Lane> =
        carousel?.lanes.orEmpty().filter { (it == Carousel.Lane.Extended && extendedSet) || (it != Carousel.Lane.Extended && legacySets > 0) }

    /** 10.1 step 4: GATT server, beacon, data sets; then the carousel. Throws if the beacon is refused. */
    suspend fun start(publication: Publication = Publication()) = start(build(publication))

    /** The two-phone test's form: [song] goes out raw as document 3 under a fixed test reference. */
    suspend fun start(song: ByteArray) = start(raw(song))

    /** 10.5: `seq` + 1, a new signed state; carousel, GATT and beacon follow. The same publication again is a re-push [FR-LEAD-06]. */
    suspend fun push(publication: Publication) = mutex.withLock { publish(build(publication)) }

    suspend fun push(song: ByteArray) = mutex.withLock { publish(raw(song)) }

    /**
     * 10.10: the final state with `status` 1 and `ENDED`, broadcast for [lingerMs] while every write is
     * refused, then all advertising and the GATT server stop. The keys go with this object.
     */
    suspend fun end(lingerMs: Long = END_LINGER_MS) {
        mutex.withLock {
            if (ended) return
            ended = true
            flags = flags or ProtocolConstants.FLAG_ENDED
            service.end()
            publish(last)
        }
        delay(lingerMs)
        stop()
    }

    suspend fun stop() {
        loop?.cancel()
        mutex.withLock {
            transport.stopAdvertising()
            transport.stopServer()
        }
    }

    private suspend fun start(first: Built) = mutex.withLock {
        transport.startServer(service)
        transport.setBeacon(beaconBytes())
        legacySets = if (extendedOnly) 0 else transport.openLegacyDataSets(Carousel.MAX_LEGACY_DATA_SETS)
        extendedSet = transport.openExtendedDataSet()
        flags = (if (legacySets > 0 || extendedSet) ProtocolConstants.FLAG_BCAST else 0) or (if (extendedSet) ProtocolConstants.FLAG_EXT else 0) or
            (if (joinCode != null) ProtocolConstants.FLAG_CODE else 0)
        // The carousel needs a legacy lane; with extended only it is built but never sent.
        val c = Carousel(gid16, legacySets.coerceAtLeast(1), extendedSet)
        carousel = c
        publish(first)
        val lanes = activeLanes()
        loop = scope.launch {
            while (isActive) {
                mutex.withLock {
                    for (lane in lanes) c.next(lane)?.let { f ->
                        val set = when (lane) {
                            Carousel.Lane.Small -> 0
                            is Carousel.Lane.Large -> lane.index
                            Carousel.Lane.Extended -> Transport.EXTENDED_SET
                        }
                        transport.setFrame(set, FrameCodec.encode(f))
                        framesSent++
                    }
                }
                delay(Carousel.ADVERTISING_INTERVAL_MS)
            }
        }
    }

    /** A document framed for the air (7.3: DEFLATE when it saves at least 10 %) with its manifest entry. */
    private class Framed(val enc: Int, val bytes: ByteArray, val entry: DocEntry)

    private class Built(val cur: SongRef?, val title: String?, val docs: Map<Int, Framed>)

    private fun framed(document: ByteArray, raw: Boolean = false): Framed {
        val (enc, bytes) = if (raw) Deflate.ENC_RAW to document else Deflate.frame(document)
        return Framed(enc, bytes, DocEntry.of(bytes, enc, crypto))
    }

    private fun build(p: Publication): Built {
        p.ahead?.let { list -> songsAhead = list.map { it?.let { d -> framed(d.encode()) } } }
        val docs = LinkedHashMap<Int, Framed>()
        p.song?.let { docs[DocumentType.SONG] = framed(it.encode()) }
        // 9.5 key 2: with songs ahead the setlist always goes out, listing their `dh` by position.
        val setlist = if (songsAhead.isEmpty()) p.setlist
        else (p.setlist ?: SetlistDocument(emptyList())).withAhead(songsAhead.map { it?.entry?.dh ?: ByteArray(0) })
        setlist?.let { docs[DocumentType.SETLIST] = framed(it.encode()) }
        return Built(p.cur, p.title, docs)
    }

    /**
     * 7.4 rule 7: the songs ahead in the order to send them, each distinct song once, starting with
     * the one after the current song in setlist order and wrapping around.
     */
    private fun aheadOrder(b: Built): List<Carousel.Document> {
        if (songsAhead.isEmpty()) return emptyList()
        val current = b.docs[DocumentType.SONG]?.entry?.dh
        val at = current?.let { dh -> songsAhead.indexOfFirst { it?.entry?.dh?.contentEquals(dh) == true } } ?: -1
        val seen = HashSet<String>()
        return songsAhead.indices.map { songsAhead[(at + 1 + it) % songsAhead.size] }
            .filterNotNull()
            .filter { seen.add(it.entry.dh.joinToString("") { b -> "%02x".format(b) }) }
            .map { Carousel.Document(DocumentType.AHEAD, it.entry.dv, it.bytes) }
    }

    private fun raw(song: ByteArray): Built {
        val ref = SongRef(LibraryId(TEST_LIBRARY), SongId(TEST_SONG), ContentHash(crypto.sha256(song).copyOf(ContentHash.SIZE)))
        return Built(ref, null, mapOf(DocumentType.SONG to framed(song, raw = true)))
    }

    private suspend fun publish(b: Built) {
        last = b
        seq = (seq + 1) and 0xFFFF
        ts = maxOf(ts + 1, clock() - startedAt)          // 9.4: strictly increasing
        val state = SignedState.sign(body(b), signing.secretKey, crypto) // fits the body to 440 bytes as 9.4 orders
        val info = Info(
            ProtocolConstants.PROTOCOL_MAJOR, ProtocolConstants.PROTOCOL_MINOR, gid, name, flags, service.singerCount.toLong(),
            signing.publicKey, nsets = legacySets, ext = extendedSet, ek = ek.publicKey,
        ).encode()
        val infoDv = DocEntry.of(info, Deflate.ENC_RAW, crypto).dv
        val stateBytes = state.encode()
        carousel?.publish(
            listOf(Carousel.Document(DocumentType.STATE, seq, stateBytes), Carousel.Document(DocumentType.INFO, infoDv, info)) +
                b.docs.map { (doc, f) -> Carousel.Document(doc, f.entry.dv, f.bytes) },
            aheadOrder(b),
        )
        service.publish(
            info, stateBytes, seq,
            listOf(
                LeaderGattService.Served(DocumentType.STATE, seq, stateBytes, Deflate.ENC_RAW, crypto = crypto),
                LeaderGattService.Served(DocumentType.INFO, infoDv, info, Deflate.ENC_RAW, crypto = crypto),
            ) + b.docs.map { (doc, f) ->
                LeaderGattService.Served(doc, f.entry.dv, f.bytes, f.enc, if (doc == DocumentType.SONG) b.cur?.hash?.bytes else null, crypto)
            },
        )
        transport.setBeacon(beaconBytes())
    }

    private fun body(b: Built) = StateBody(
        gid = gid, seq = seq, ts = ts, status = if (ended) StateBody.STATUS_ENDED else StateBody.STATUS_RUNNING, flags = flags,
        cur = b.cur, title = b.title, singers = service.singerCount.toLong(),
        docs = b.docs.mapValues { it.value.entry }, rseq = 0, ek = ek.publicKey, pt = carousel?.songPassTimeMs(),
    )

    private suspend fun busyChanged(b: Boolean) = mutex.withLock {
        busy = b
        transport.setBeacon(beaconBytes())
    }

    private fun beaconBytes() = BeaconCodec.encodeA(
        Beacon(1, gid, seq, flags or (if (busy) ProtocolConstants.FLAG_BUSY else 0), 0, name),
    )

    companion object {
        const val TEST_LIBRARY = "test"
        const val TEST_SONG = "test-document"
        /** 10.10 step 2. */
        const val END_LINGER_MS = 60_000L
        /** guiDesign D-56: the leader's help bar looks back this far. */
        const val HELP_WINDOW_MS = 5_000L
    }
}
