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
 * Not wired in yet: [LeaderMachine]'s lifecycle (pre-start scan, resume), the join code, messages and
 * requests.
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
) {
    /**
     * What the singers get (9.4, 9.5): the current song and the setlist as far as it has been sung
     * (guiDesign D-47: nothing upcoming). [song] is null for a song its library marks "do not
     * redistribute" [FR-LIB-22]: the state then carries [cur] and [title] only.
     */
    class Publication(
        val cur: SongRef? = null,
        val title: String? = null,
        val song: SongDocument? = null,
        val setlist: SetlistDocument? = null,
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

    val service = LeaderGattService(gid, ek, crypto, scope, clock, maxSingers = maxSingers, onBusyChanged = { b -> scope.launch { busyChanged(b) } })

    /** Singers registered and not left or timed out (10.9). */
    val singers: Int get() = service.singerCount

    var legacySets: Int = 0
        private set
    var extendedSet: Boolean = false
        private set
    var framesSent: Long = 0
        private set

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
        flags = (if (legacySets > 0 || extendedSet) ProtocolConstants.FLAG_BCAST else 0) or (if (extendedSet) ProtocolConstants.FLAG_EXT else 0)
        // The carousel needs a legacy lane; with extended only it is built but never sent.
        val c = Carousel(gid16, legacySets.coerceAtLeast(1), extendedSet)
        carousel = c
        publish(first)
        val lanes = c.lanes.filter { (it == Carousel.Lane.Extended && extendedSet) || (it != Carousel.Lane.Extended && legacySets > 0) }
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
        val docs = LinkedHashMap<Int, Framed>()
        p.song?.let { docs[DocumentType.SONG] = framed(it.encode()) }
        p.setlist?.let { docs[DocumentType.SETLIST] = framed(it.encode()) }
        return Built(p.cur, p.title, docs)
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
    }
}
