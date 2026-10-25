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
import nl.pe1mew.zangbundel.core.protocol.payload.DocEntry
import nl.pe1mew.zangbundel.core.protocol.payload.DocumentType
import nl.pe1mew.zangbundel.core.protocol.payload.Info
import nl.pe1mew.zangbundel.core.protocol.payload.SignedState
import nl.pe1mew.zangbundel.core.protocol.payload.StateBody
import nl.pe1mew.zangbundel.core.protocol.payload.gatheringIdOf
import kotlin.random.Random

/**
 * One running gathering on the leader (ble-protocol 10.1 steps 2–5, 10.5): its keys and `gid`,
 * Info, the signed state, the carousel on the data sets the radio grants, and [LeaderGattService]
 * behind them, kept in step on every [push].
 *
 * Not wired in yet: [LeaderMachine]'s lifecycle (pre-start scan, resume, end), the setlist,
 * messages and requests. The song is published as given, raw (`enc` 0).
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
    private val signing = crypto.generateSigningKeyPair()
    val gid: GatheringId = gatheringIdOf(signing.publicKey, crypto)
    private val gid16 = ((gid.bytes[0].toInt() and 0xFF) shl 8) or (gid.bytes[1].toInt() and 0xFF)
    private val ek = crypto.generateBoxKeyPair()
    private val mutex = Mutex()
    private var flags = 0
    private var busy = false
    var seq: Int = random.nextInt(0x10000)
        private set
    private var ts = 0L
    private val startedAt = clock()
    private var carousel: Carousel? = null
    private var loop: Job? = null

    val service = LeaderGattService(gid, ek, crypto, scope, clock, maxSingers = maxSingers, onBusyChanged = { b -> scope.launch { busyChanged(b) } })

    var legacySets: Int = 0
        private set
    var extendedSet: Boolean = false
        private set
    var framesSent: Long = 0
        private set

    /** 10.1 step 4: GATT server, beacon, data sets; then the carousel. Throws if the beacon is refused. */
    suspend fun start(song: ByteArray) = mutex.withLock {
        transport.startServer(service)
        transport.setBeacon(beaconBytes())
        legacySets = if (extendedOnly) 0 else transport.openLegacyDataSets(Carousel.MAX_LEGACY_DATA_SETS)
        extendedSet = transport.openExtendedDataSet()
        flags = (if (legacySets > 0 || extendedSet) ProtocolConstants.FLAG_BCAST else 0) or (if (extendedSet) ProtocolConstants.FLAG_EXT else 0)
        // The carousel needs a legacy lane; with extended only it is built but never sent.
        val c = Carousel(gid16, legacySets.coerceAtLeast(1), extendedSet)
        carousel = c
        publish(song)
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

    /** 10.5: a new song, `seq` + 1, a new signed state; carousel, GATT and beacon follow. */
    suspend fun push(song: ByteArray) = mutex.withLock { publish(song) }

    suspend fun stop() {
        loop?.cancel()
        mutex.withLock {
            transport.stopAdvertising()
            transport.stopServer()
        }
    }

    private suspend fun publish(song: ByteArray) {
        seq = (seq + 1) and 0xFFFF
        ts = maxOf(ts + 1, clock() - startedAt)          // 9.4: strictly increasing
        val songEntry = DocEntry.of(song, ENC_RAW, crypto)
        val ref = SongRef(LibraryId(TEST_LIBRARY), SongId(TEST_SONG), ContentHash(crypto.sha256(song).copyOf(ContentHash.SIZE)))
        val body = StateBody(
            gid = gid, seq = seq, ts = ts, status = StateBody.STATUS_RUNNING, flags = flags, cur = ref,
            singers = service.singerCount.toLong(), docs = mapOf(DocumentType.SONG to songEntry), rseq = 0, ek = ek.publicKey,
            pt = carousel?.songPassTimeMs(),
        )
        val state = SignedState.sign(body, signing.secretKey, crypto)
        val info = Info(
            ProtocolConstants.PROTOCOL_MAJOR, ProtocolConstants.PROTOCOL_MINOR, gid, name, flags, service.singerCount.toLong(),
            signing.publicKey, nsets = legacySets, ext = extendedSet, ek = ek.publicKey,
        ).encode()
        val infoDv = DocEntry.of(info, ENC_RAW, crypto).dv
        val stateBytes = state.encode()
        carousel?.publish(
            listOf(
                Carousel.Document(DocumentType.STATE, seq, stateBytes),
                Carousel.Document(DocumentType.INFO, infoDv, info),
                Carousel.Document(DocumentType.SONG, songEntry.dv, song),
            ),
        )
        service.publish(
            info, stateBytes, seq,
            listOf(
                LeaderGattService.Served(DocumentType.STATE, seq, stateBytes, ENC_RAW, crypto = crypto),
                LeaderGattService.Served(DocumentType.INFO, infoDv, info, ENC_RAW, crypto = crypto),
                LeaderGattService.Served(DocumentType.SONG, songEntry.dv, song, ENC_RAW, ref.hash.bytes, crypto),
            ),
        )
        transport.setBeacon(beaconBytes())
    }

    private suspend fun busyChanged(b: Boolean) = mutex.withLock {
        busy = b
        transport.setBeacon(beaconBytes())
    }

    private fun beaconBytes() = BeaconCodec.encodeA(
        Beacon(1, gid, seq, flags or (if (busy) ProtocolConstants.FLAG_BUSY else 0), 0, name),
    )

    companion object {
        private const val ENC_RAW = 0
        const val TEST_LIBRARY = "test"
        const val TEST_SONG = "test-document"
    }
}
