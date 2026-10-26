// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.gathering

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.pe1mew.zangbundel.app.Log
import nl.pe1mew.zangbundel.app.library.SetlistBook
import nl.pe1mew.zangbundel.core.model.GatheringId
import nl.pe1mew.zangbundel.core.model.LibraryId
import nl.pe1mew.zangbundel.core.model.SingerState
import nl.pe1mew.zangbundel.core.model.SongId
import nl.pe1mew.zangbundel.core.model.SongRef
import nl.pe1mew.zangbundel.core.protocol.Crypto
import nl.pe1mew.zangbundel.core.protocol.Transport
import nl.pe1mew.zangbundel.core.protocol.beacon.BeaconCodec
import nl.pe1mew.zangbundel.core.protocol.engine.LeaderSession
import nl.pe1mew.zangbundel.core.protocol.engine.SingerEngine
import nl.pe1mew.zangbundel.core.protocol.payload.DocumentType
import nl.pe1mew.zangbundel.core.protocol.payload.SetlistDocument
import nl.pe1mew.zangbundel.core.protocol.payload.SongDocument
import nl.pe1mew.zangbundel.core.songformat.SetlistResolver
import nl.pe1mew.zangbundel.core.songformat.Song
import nl.pe1mew.zangbundel.core.songformat.SongParser
import nl.pe1mew.zangbundel.platform.storage.Memories
import nl.pe1mew.zangbundel.platform.storage.MemorySong
import nl.pe1mew.zangbundel.platform.storage.PreparedEntry
import nl.pe1mew.zangbundel.platform.storage.Songbook
import nl.pe1mew.zangbundel.platform.storage.SongbookEntry

/**
 * The one gathering on this phone (architecture A-06, FR-GEN-02): scanning for gatherings nearby
 * (guiDesign D-22), leading one (D-25, D-55, D-56) or singing in one (D-26, D-27, D-29, D-34), never
 * two at once. It lives as long as the process; [GatheringService] keeps the process alive and shows
 * the neutral notification while a gathering runs.
 *
 * Not yet: join codes and the QR code, fingerprint words, requests and likes, delegates, resume after a
 * restart, follow the leader.
 */
class Gathering(
    private val context: Context,
    private val transport: () -> Transport?,
    private val crypto: Crypto,
    private val songbook: Songbook,
    private val setlists: SetlistBook,
    private val memories: Memories,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val wallClock: () -> Long = System::currentTimeMillis,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    /** A gathering heard nearby (D-22): its name and how strongly it is heard [FR-GATH-04]. */
    data class Nearby(val gid: GatheringId, val name: String, val rssi: Int, val codeRequired: Boolean, val seenAt: Long)

    /** A line of the leader's setlist: the installed song it names, or null when it cannot be pushed (*niet gevonden*, D-59). */
    data class LeaderSong(val entry: PreparedEntry, val song: SongbookEntry?) {
        val title: String get() = song?.title ?: entry.title
    }

    /** A song a singer received (D-29, D-47): [song] null while it is still coming in, or when it is only shown with its library [FR-LIB-22]. */
    data class ReceivedSong(val ref: SongRef, val title: String, val song: Song?, val libraryOnly: Boolean = false)

    enum class Connection { CONNECTING, CONNECTED, RECONNECTING, ENDED }

    sealed interface Mode {
        data object Idle : Mode
        data class Leading(
            val name: String,
            val startedAt: Long,
            val singers: Int,
            val songs: List<LeaderSong>,
            val current: Int?,
            val sung: Set<Int>,
        ) : Mode
        data class Singing(val name: String, val connection: Connection, val songs: List<ReceivedSong>, val current: Int?) : Mode
    }

    private val _mode = MutableStateFlow<Mode>(Mode.Idle)
    val mode: StateFlow<Mode> = _mode

    private val _nearby = MutableStateFlow<List<Nearby>>(emptyList())
    val nearby: StateFlow<List<Nearby>> = _nearby

    /** When the current scan started, for the 5 seconds of listening before a start (FR-GATH-16, D-23); null when not scanning. */
    private val _scanningSince = MutableStateFlow<Long?>(null)
    val scanningSince: StateFlow<Long?> = _scanningSince

    private var scanJob: Job? = null
    /** The leave notice sent in the background after leaving (10.11 v0.29); the next scan waits for it. */
    @Volatile private var leaveJob: Job? = null
    private var session: CoroutineScope? = null
    private var leader: LeaderSession? = null
    private var singer: SingerEngine? = null
    private val refs = HashMap<Int, SongRef>()
    /** Singing: the current song of the latest verified state. */
    private var currentRef: SongRef? = null
    /** For the gathering memory (FR-DATA-03): when it started, the songs in the order first pushed, the most singers at once. */
    private var startedWall = 0L
    private val pushedOrder = ArrayList<Int>()
    private var peakSingers = 0

    fun now(): Long = clock()

    /** Whether this phone can lead (FRD constraint 1, D-24). */
    val canLead: Boolean get() = transport()?.capabilities?.peripheralMode == true

    // ---- nearby (D-22) ----

    /** Scans while the home screen is shown; beacons of ended gatherings are not listed. */
    fun startScanning() {
        if (scanJob != null || _mode.value != Mode.Idle) return
        val t = transport() ?: return
        scanJob = scope.launch {
            // A leave notice still under way goes first: on the Huawei a scan started while a direct
            // connection is pending stalls the connection (design/test-results/2026-10-26-D-03-05-07.md).
            leaveJob?.join()
            _scanningSince.value = clock()
            launch {
                t.scanResults().collect { r ->
                    val beacon = r.manufacturerData?.let(BeaconCodec::decodeA) ?: r.localName?.let(BeaconCodec::decodeB) ?: return@collect
                    if (beacon.ended) return@collect
                    val found = Nearby(beacon.gid, beacon.name, r.rssi, beacon.codeRequired, clock())
                    _nearby.value = (_nearby.value.filterNot { it.gid == beacon.gid } + found).sortedByDescending { it.rssi }
                }
            }
            launch {
                while (isActive) {
                    delay(1_000)
                    val cutoff = clock() - NEARBY_TIMEOUT_MS
                    _nearby.value = _nearby.value.filter { it.seenAt >= cutoff }
                }
            }
            t.startScan(Transport.ScanMode.DISCOVERY)
        }
    }

    /**
     * Ends the discovery list. The radio's scan stops only while no gathering runs: the home screen
     * calls this when it leaves, which is after a join has handed the scan to the singer engine, and
     * stopping it then would leave the singer deaf. [stopRadio] false when the caller stops it itself, in order.
     */
    fun stopScanning(stopRadio: Boolean = true) {
        scanJob?.cancel()
        scanJob = null
        _scanningSince.value = null
        _nearby.value = emptyList()
        if (stopRadio && _mode.value == Mode.Idle && session == null) transport()?.let { t -> scope.launch { runCatching { t.stopScan() } } }
    }

    // ---- leading (D-25, D-55, D-56) ----

    /** Starts a gathering with a prepared setlist, or none (D-25); the page starts empty until the first *Nu* (D-55). */
    suspend fun lead(name: String, setlistUid: Long?) = mutex.withLock {
        check(_mode.value == Mode.Idle) { "one gathering at a time (FR-GEN-02)" }
        stopScanning(stopRadio = false)
        val t = checkNotNull(transport()) { "no Bluetooth" }
        runCatching { t.stopScan() }
        val installed = songbook.entries()
        val songs = setlistUid?.let { setlists.entries(it) }.orEmpty().map { LeaderSong(it, resolve(it, installed)) }
        val s = CoroutineScope(scope.coroutineContext + Job(scope.coroutineContext[Job]))
        val l = LeaderSession(t, crypto, s, clock, name)
        GatheringService.start(context)
        try {
            l.start()
        } catch (e: Exception) {
            s.cancel()
            GatheringService.stop(context)
            throw e
        }
        session = s
        leader = l
        refs.clear()
        startedWall = wallClock()
        pushedOrder.clear()
        peakSingers = 0
        _mode.value = Mode.Leading(name, clock(), 0, songs, null, emptySet())
        s.launch {
            while (isActive) {
                delay(1_000)
                peakSingers = maxOf(peakSingers, l.singers)
                (_mode.value as? Mode.Leading)?.let { m -> if (m.singers != l.singers) _mode.value = m.copy(singers = l.singers) }
            }
        }
    }

    /**
     * *Nu* on a separator (D-56, FR-LEAD-01, FR-LEAD-03): that song becomes the current one for every
     * singer; the one before is marked sung; skipped songs stay unmarked. *Nu* on the current song pushes it again.
     */
    suspend fun push(index: Int) = mutex.withLock {
        val m = _mode.value as? Mode.Leading ?: return@withLock
        val l = leader ?: return@withLock
        val e = m.songs.getOrNull(index)?.song ?: return@withLock
        val file = songbook.read(e.libraryId, e.directory, e.fileName)
        val song = SongParser.parse(file).song ?: return@withLock
        val ref = SongRef(LibraryId(e.libraryId), SongId(e.songId), song.hash)
        refs[index] = ref
        val sung = if (m.current != null && m.current != index) m.sung + m.current else m.sung
        // D-47: the setlist singers get holds what was sung and the current song, nothing upcoming.
        val items = m.songs.indices.filter { it in sung || it == index }.mapNotNull { i ->
            refs[i]?.let { r -> SetlistDocument.Item(r, m.songs[i].title, if (i == index) STATUS_CURRENT else STATUS_SUNG) }
        }
        val document = if (song.meta.redistribute) SongDocument(ref, file, song.meta.licenceSummary(e.libraryName)) else null
        l.push(LeaderSession.Publication(ref, song.meta.title, document, SetlistDocument(items)))
        Log.d("leader push index=$index repush=${index == m.current}")
        if (index !in pushedOrder) pushedOrder += index
        _mode.value = m.copy(current = index, sung = sung - index)
    }

    /** *Afsluiten* (10.10): singers see the end at once; the radio lingers 60 s in the background, then stops. */
    fun end() {
        val l = leader ?: return
        val s = session
        val m = _mode.value as? Mode.Leading
        leader = null
        session = null
        _mode.value = Mode.Idle
        if (m != null) {
            val sung = pushedOrder.mapNotNull { i -> m.songs[i].song?.let { MemorySong(it.libraryId, it.songId, it.title) } }
            remember(m.name, led = true, singers = maxOf(peakSingers, m.singers), songs = sung)
        }
        scope.launch {
            try {
                l.end()
            } finally {
                s?.cancel()
                GatheringService.stop(context)
            }
        }
    }

    // ---- singing (D-26, D-27, D-29, D-34) ----

    fun join(nearby: Nearby) {
        if (_mode.value != Mode.Idle) return
        stopScanning(stopRadio = false)
        val t = transport() ?: return
        val s = CoroutineScope(scope.coroutineContext + Job(scope.coroutineContext[Job]))
        session = s
        currentRef = null
        startedWall = wallClock()
        _mode.value = Mode.Singing(nearby.name, Connection.CONNECTING, emptyList(), null)
        s.launch {
            // The discovery scan stops before the engine starts its own; a stop that came later would silence the singer.
            runCatching { t.stopScan() }
            val local = songbook.entries().filter { it.hash.isNotEmpty() }.associateBy { it.hash }
            val engine = SingerEngine(t, crypto, s, clock, chooses = { it.gid == nearby.gid }, hasSong = { it.hash.toHex() in local })
            singer = engine
            GatheringService.start(context)
            launch {
                engine.events.collect { e ->
                    // A-20: event kinds, codes and times only; never names or song text.
                    when (e) {
                        is SingerEngine.Event.Delivered -> Log.d("singer delivered doc=${e.doc} repair=${e.viaRepair}")
                        is SingerEngine.Event.Repaired -> Log.d("singer repair ok=${e.ok} ms=${e.ms} failure=${e.failure}")
                        else -> Log.d("singer ${e::class.simpleName}")
                    }
                    when (e) {
                        is SingerEngine.Event.Joined -> connection(Connection.CONNECTED)
                        // The beacon's new seq comes before the state that names the song; the verified state moves the page.
                        is SingerEngine.Event.Delivered -> when (e.doc) {
                            DocumentType.STATE -> current(engine)
                            DocumentType.SONG -> received(e)
                        }
                        is SingerEngine.Event.LocalSong -> local[e.ref.hash.toHex()]?.let { entry -> localSong(e.ref, entry) }
                        is SingerEngine.Event.Ended -> connection(Connection.ENDED)
                        else -> Unit
                    }
                }
            }
            launch {
                var last: SingerState? = null
                while (isActive) {
                    delay(1_000)
                    if (engine.machine.state != last) { last = engine.machine.state; Log.d("singer state=$last") }
                    val c = when (engine.machine.state) {
                        SingerState.RECONNECTING -> Connection.RECONNECTING
                        SingerState.LISTENING, SingerState.REPAIRING -> Connection.CONNECTED
                        SingerState.ENDED -> Connection.ENDED
                        else -> null
                    }
                    if (c != null) connection(c)
                }
            }
            engine.start()
        }
    }

    /**
     * Leaving (D-34, FR-GATH-07, ble-protocol 10.11 v0.29): everything received goes at once
     * (FR-DATA-01: it was only ever in memory) and the user is home at once; the leader is told in the
     * background, retried for up to 10 s. Also how a singer leaves an ended gathering, with nothing to tell.
     */
    suspend fun leave() {
        val engine = singer
        val m = _mode.value as? Mode.Singing
        val notify = engine != null && m?.connection != Connection.ENDED
        // D-49: the memory is written in the same step that removes the lyrics.
        if (m != null) remember(m.name, led = false, singers = null, songs = m.songs.map { MemorySong(it.ref.library.value, it.ref.song.value, it.title) })
        singer = null
        // The singer's scan stops here, before the home screen starts its own; the engine's later stop must not reach that one.
        transport()?.let { t -> runCatching { t.stopScan() } }
        session?.cancel()
        session = null
        _mode.value = Mode.Idle
        GatheringService.stop(context)
        if (notify) leaveJob = scope.launch {
            val t0 = clock()
            val sent = runCatching { engine!!.leave() }.getOrDefault(false)
            Log.d("singer leave sent=$sent ms=${clock() - t0}")
        }
    }

    /** FR-DATA-03: titles and identifiers only; a gathering in which nothing was sung leaves no memory. */
    private fun remember(name: String, led: Boolean, singers: Int?, songs: List<MemorySong>) {
        if (songs.isEmpty()) return
        val started = startedWall
        val ended = wallClock()
        scope.launch { runCatching { memories.add(name, started, ended, led, singers, songs) }.onFailure { Log.w("memory not written: ${it::class.simpleName}") } }
    }

    private fun connection(c: Connection) {
        val m = _mode.value as? Mode.Singing ?: return
        if (m.connection == Connection.ENDED || m.connection == c) return
        _mode.value = m.copy(connection = c)
    }

    /** A new state: the current song moves; a song not yet received gets its place with the state's title (D-29). */
    private fun current(engine: SingerEngine) {
        val body = engine.state?.body ?: return
        val cur = body.cur ?: return
        currentRef = cur
        val m = _mode.value as? Mode.Singing ?: return
        val at = m.songs.indexOfFirst { it.ref == cur }
        _mode.value = if (at >= 0) m.copy(current = at)
        else m.copy(songs = m.songs + ReceivedSong(cur, body.title ?: cur.song.value, null, libraryOnly = DocumentType.SONG !in body.docs), current = m.songs.size)
    }

    /** FR-BLE-04: shown only when the file matches its reference. */
    private fun received(e: SingerEngine.Event.Delivered) {
        val doc = runCatching { SongDocument.decode(e.document()) }.getOrNull() ?: return
        if (!crypto.sha256(doc.file).copyOf(doc.ref.hash.bytes.size).contentEquals(doc.ref.hash.bytes)) return
        val song = SongParser.parse(doc.file).song ?: return
        place(doc.ref, song)
    }

    /** The current song is in this phone's library (10.3 step 3): shown from there, nothing to collect. */
    private suspend fun localSong(ref: SongRef, entry: SongbookEntry) {
        val file = runCatching { songbook.read(entry.libraryId, entry.directory, entry.fileName) }.getOrNull() ?: return
        SongParser.parse(file).song?.let { place(ref, it) }
    }

    private fun place(ref: SongRef, song: Song) {
        val m = _mode.value as? Mode.Singing ?: return
        val at = m.songs.indexOfFirst { it.ref == ref }
        val item = ReceivedSong(ref, song.meta.title, song)
        val songs = if (at >= 0) m.songs.toMutableList().also { it[at] = item } else m.songs + item
        // The song the latest verified state names is the current one, whichever arrived first.
        val current = songs.indexOfFirst { it.ref == currentRef }.takeIf { it >= 0 } ?: m.current ?: songs.lastIndex
        _mode.value = m.copy(songs = songs, current = current)
    }

    private fun resolve(e: PreparedEntry, installed: List<SongbookEntry>): SongbookEntry? =
        e.songKey?.let { key -> installed.firstOrNull { it.libraryId + "/" + it.songId == key } }
            ?: if (e.songKey == null) installed.filter { SetlistResolver.fold(it.title) == SetlistResolver.fold(e.title) }.singleOrNull() else null

    companion object {
        /** A gathering not heard for this long leaves the list (D-22). */
        const val NEARBY_TIMEOUT_MS = 10_000L
        /** FR-GATH-16: listen this long before a gathering may start (D-23). */
        const val LISTEN_BEFORE_START_MS = 5_000L
        private const val STATUS_CURRENT = 1
        private const val STATUS_SUNG = 2
    }
}
