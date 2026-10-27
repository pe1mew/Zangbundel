// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.gathering

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import nl.pe1mew.zangbundel.app.Log
import nl.pe1mew.zangbundel.app.diagnostics.FieldLog
import nl.pe1mew.zangbundel.app.library.SetlistBook
import nl.pe1mew.zangbundel.core.model.GatheringId
import nl.pe1mew.zangbundel.core.model.LibraryId
import nl.pe1mew.zangbundel.core.model.SingerState
import nl.pe1mew.zangbundel.core.model.SongId
import nl.pe1mew.zangbundel.core.model.SongRef
import nl.pe1mew.zangbundel.core.protocol.Crypto
import nl.pe1mew.zangbundel.core.protocol.Transport
import nl.pe1mew.zangbundel.core.protocol.beacon.BeaconCodec
import nl.pe1mew.zangbundel.core.protocol.beacon.Fingerprint
import nl.pe1mew.zangbundel.core.protocol.beacon.JoinLink
import nl.pe1mew.zangbundel.core.protocol.payload.JoinResponse
import nl.pe1mew.zangbundel.core.protocol.engine.LeaderSession
import nl.pe1mew.zangbundel.core.protocol.engine.ResumeRecord
import nl.pe1mew.zangbundel.core.protocol.payload.gatheringIdOf
import nl.pe1mew.zangbundel.core.protocol.engine.SingerEngine
import nl.pe1mew.zangbundel.core.protocol.frame.Reassembler
import nl.pe1mew.zangbundel.core.protocol.payload.DocumentType
import nl.pe1mew.zangbundel.core.protocol.payload.SongDocument
import nl.pe1mew.zangbundel.core.protocol.ProtocolConstants
import nl.pe1mew.zangbundel.core.songformat.ArchiveImport
import nl.pe1mew.zangbundel.core.songformat.LibraryFileParser
import nl.pe1mew.zangbundel.core.songformat.SetlistResolver
import nl.pe1mew.zangbundel.core.songformat.Song
import nl.pe1mew.zangbundel.core.songformat.SongParser
import nl.pe1mew.zangbundel.platform.ble.AndroidTransport
import nl.pe1mew.zangbundel.platform.storage.AppSettings
import nl.pe1mew.zangbundel.platform.storage.Memories
import nl.pe1mew.zangbundel.platform.storage.MemorySong
import nl.pe1mew.zangbundel.platform.storage.PreparedEntry
import nl.pe1mew.zangbundel.platform.storage.ResumeStore
import nl.pe1mew.zangbundel.platform.storage.Statistics
import nl.pe1mew.zangbundel.platform.storage.Songbook
import nl.pe1mew.zangbundel.platform.storage.SongbookEntry

/**
 * The one gathering on this phone (architecture A-06, FR-GEN-02): scanning for gatherings nearby
 * (guiDesign D-22), leading one (D-25, D-55, D-56) or singing in one (D-26, D-27, D-29, D-34), never
 * two at once. It lives as long as the process; [GatheringService] keeps the process alive and shows
 * the neutral notification while a gathering runs.
 *
 * A leader's gathering survives the app dying (FR-GATH-13, ble-protocol 10.9, guiDesign D-67): the
 * resume record is written on start, on every push, when the singer count changes and every 30 s;
 * [resumeOffer] reads it at the next start and [resume] carries the gathering on.
 *
 * Not yet: requests and likes, delegates (phase 2).
 */
class Gathering(
    private val context: Context,
    private val transport: () -> Transport?,
    private val crypto: Crypto,
    private val songbook: Songbook,
    private val setlists: SetlistBook,
    private val memories: Memories,
    private val settings: AppSettings,
    /** guiDesign D-60: where a campfire's statistics go when the leader opted in, and the app version they carry. */
    private val statistics: Statistics,
    private val appVersion: String,
    private val resumeStore: ResumeStore,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val wallClock: () -> Long = System::currentTimeMillis,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    /** Orders writes and the delete of the resume record, so a write never revives an ended gathering. */
    private val resumeLock = Mutex()

    /** A gathering heard nearby (D-22): its name and how strongly it is heard [FR-GATH-04]. */
    data class Nearby(val gid: GatheringId, val name: String, val rssi: Int, val codeRequired: Boolean, val seenAt: Long) {
        /** FR-GATH-18, ble-protocol 6.6: the two words the leader can say aloud. */
        val fingerprint: String get() = Fingerprint.of(gid)
    }

    /**
     * How a gathering is joined (FR-GATH-05, FR-GATH-21, guiDesign D-25, D-71): open, with a four-digit code
     * the leader can say, or QR-only with a random eight-character code that exists only in the QR code.
     * Fixed for the gathering.
     */
    sealed interface Protection {
        val code: String?
        data object Open : Protection { override val code: String? = null }
        data class Code(override val code: String) : Protection
        data class QrOnly(override val code: String) : Protection

        companion object {
            private val random = java.security.SecureRandom()
            fun newCode(): Code = Code((0 until 4).map { random.nextInt(10) }.joinToString(""))
            fun newQrOnly(): QrOnly = QrOnly((0 until 8).map { QR_ONLY_ALPHABET[random.nextInt(QR_ONLY_ALPHABET.length)] }.joinToString(""))
            private const val QR_ONLY_ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789"
        }
    }

    /**
     * A line of the leader's setlist: the installed song it names, or null when it cannot be pushed
     * (*niet gevonden*, D-59). [line] keeps the line's identity while the setlist changes (D-58), also
     * when a song stands on it twice.
     */
    data class LeaderSong(val entry: PreparedEntry, val song: SongbookEntry?, val line: Long) {
        val title: String get() = song?.title ?: entry.title
    }

    /** A song a singer received (D-29, D-47): [song] null while it is still coming in, or when it is only shown with its library [FR-LIB-22]. */
    /** A song on the singer's page; [earlier] is its setlist position when it was sung before this singer joined (v0.35). */
    /** [library]: the name of this phone's library the song was shown from, null when it came over the air (D-45). */
    data class ReceivedSong(val ref: SongRef, val title: String, val song: Song?, val libraryOnly: Boolean = false, val earlier: Int? = null, val library: String? = null)

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
            /** How far the current song has gone out (D-56); null before the first push or for a display-only song. */
            val progress: LeaderSession.PushProgress? = null,
            /** D-56 (v0.21): the share of singers helped over GATT in the last 5 s, 0 to 1. */
            val help: Float = 0f,
            /** FR-GATH-18: the two words (D-36, D-61). */
            val fingerprint: String = "",
            val protection: Protection = Protection.Open,
            /** FR-GATH-19, ble-protocol 6.5: the join link the QR code shows (D-57). */
            val joinLink: String = "",
            /** D-62: the evening's maximum number of singers. */
            val peak: Int = 0,
            /** D-62: songs pushed so far, each once. */
            val songsSung: Int = 0,
            /** D-62: how long the song before the current one was sung, from push to push. */
            val lastSongMs: Long? = null,
            /** D-62: the share of singers helped over GATT in the last minute, 0 to 1, for the link quality word. */
            val quality: Float = 0f,
        ) : Mode
        data class Singing(
            val name: String,
            val connection: Connection,
            val songs: List<ReceivedSong>,
            val current: Int?,
            /** D-29 (v0.21): the source blocks of the current song while it is still coming in; null once it is in. */
            val collecting: List<Reassembler.Block>? = null,
            /** D-81: the reception grid's rows, kept current; the page shows them only after a long press (v0.30). */
            val reception: List<SingerEngine.Reception> = emptyList(),
            /** FR-GATH-18 (D-36). */
            val fingerprint: String = "",
            /** Joined from a QR code: the joining screen of D-35 shows until registered. */
            val viaQr: Boolean = false,
            /** The beacon of the chosen gathering was heard (D-35: *Gevonden, controleren…*). */
            val found: Boolean = false,
            /** The leader refused the join code (FR-GATH-05); the singer is asked for it. */
            val needsCode: Boolean = false,
            /** 6.5, D-35: a gathering with the code's id sends states the code's key does not sign. */
            val notTheCampfire: Boolean = false,
            val joiningSince: Long = 0,
            /** D-65: the leader cleared the current song (*Wis*); the text stays, the strip says *gewist*. */
            val cleared: Boolean = false,
        ) : Mode
    }

    private val _mode = MutableStateFlow<Mode>(Mode.Idle)
    val mode: StateFlow<Mode> = _mode

    private val _nearby = MutableStateFlow<List<Nearby>>(emptyList())
    val nearby: StateFlow<List<Nearby>> = _nearby

    /**
     * While leading: the name of another gathering heard nearby (FR-GATH-17, D-68), null when none. Its own
     * flow rather than a field of [Mode.Leading], which the 1-s loop rewrites from a copy.
     */
    private val _otherNearby = MutableStateFlow<String?>(null)
    val otherNearby: StateFlow<String?> = _otherNearby

    /** When the current scan started, for the 5 seconds of listening before a start (FR-GATH-16, D-23); null when not scanning. */
    private val _scanningSince = MutableStateFlow<Long?>(null)
    val scanningSince: StateFlow<Long?> = _scanningSince

    private var scanJob: Job? = null
    /** The leave notice sent in the background after leaving (10.11 v0.29); the next scan waits for it. */
    @Volatile private var leaveJob: Job? = null
    private var session: CoroutineScope? = null
    private var leader: LeaderSession? = null
    private var singer: SingerEngine? = null
    /** The setlist's songs as pushed and sent ahead, by line; null for a line that cannot be broadcast. */
    private val prepared = HashMap<Long, Prepared?>()
    private val lines = java.util.concurrent.atomic.AtomicLong()
    /** Singing: the current song of the latest verified state. */
    private var currentRef: SongRef? = null
    /** BLE 7.5 (v0.43): the song a beacon hinted, until a state confirms it, and whether the hint put it on the page. */
    private var hintedRef: SongRef? = null
    private var hintAdded = false
    /** For the gathering memory (FR-DATA-03): when it started, the songs in the order first pushed, the most singers at once. */
    private var startedWall = 0L
    /** The lines pushed, in the order first pushed (v0.40: by line, as positions move); read by the resume record's writer while a push may add to it. */
    private val pushedLines = java.util.concurrent.CopyOnWriteArrayList<Long>()
    // D-60 (SP-16): what a campfire's statistics are made of, collected only while the opt-in is on. A song event is the
    // line pushed (null for *Wis*) and when, from the start; a singer sample is when, and how many were registered.
    private var statsOn = false
    private val songEvents = java.util.concurrent.CopyOnWriteArrayList<Pair<Long?, Long>>()
    private val singerSamples = java.util.concurrent.CopyOnWriteArrayList<Pair<Long, Int>>()
    private var peakSingers = 0
    /** D-62: when the current song was pushed, for the last song's duration. */
    private var lastPushAt: Long? = null

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
    suspend fun lead(name: String, setlistUid: Long?, protection: Protection = Protection.Open) = mutex.withLock {
        check(_mode.value == Mode.Idle) { "one gathering at a time (FR-GEN-02)" }
        stopScanning(stopRadio = false)
        val t = checkNotNull(transport()) { "no Bluetooth" }
        runCatching { t.stopScan() }
        val installed = songbook.entries()
        val songs = setlistUid?.let { setlists.entries(it) }.orEmpty().map { LeaderSong(it, resolve(it, installed), lines.incrementAndGet()) }
        // FR-BLE-18, ble-protocol 7.4 rule 7: every song of the setlist goes out ahead from the start.
        prepared.clear()
        val ahead = songs.map { preparedOf(it)?.document }
        val s = CoroutineScope(scope.coroutineContext + Job(scope.coroutineContext[Job]))
        val l = LeaderSession(t, crypto, s, clock, name, joinCode = protection.code)
        GatheringService.start(context)
        try {
            l.start(LeaderSession.Publication(ahead = ahead))
        } catch (e: Exception) {
            s.cancel()
            GatheringService.stop(context)
            throw e
        }
        session = s
        leader = l
        startedWall = wallClock()
        pushedLines.clear()
        peakSingers = 0
        lastPushAt = null
        startStatistics()
        _mode.value = Mode.Leading(name, clock(), 0, songs, null, emptySet(), fingerprint = Fingerprint.of(l.gid), protection = protection,
            joinLink = JoinLink(l.gid, l.publicKey, protection.code, name).httpsUrl(testBuild()))
        running(l, s, t)
    }

    /** The loops of a running gathering, started or resumed: diagnostics, the leader's figures, and the resume record. */
    private suspend fun running(l: LeaderSession, s: CoroutineScope, t: Transport, resumed: Boolean = false) {
        persist(l)
        // Architecture A-20: the fieldtest build's local log, once a minute while leading; counts and flags only.
        if (FieldLog.active(context)) s.launch {
            val t0 = clock()
            fun figures() = mapOf(
                "singers" to l.singers, "framesSent" to l.framesSent, "repairsServed" to l.service.transfersServed,
                "bytesRepaired" to l.service.bytesServed, "peakConnections" to l.service.peakConnections,
                "refusedConnections" to l.service.refusedConnections,
            )
            FieldLog.start(context, "leader", fieldStart(t) + mapOf(
                "resumed" to resumed, "legacySets" to l.legacySets, "extendedSet" to l.extendedSet, "maxConnections" to l.service.maxConnections,
            ))
            try {
                while (isActive) {
                    FieldLog.sample(context, clock() - t0, figures())
                    delay(FieldLog.SAMPLE_MS)
                }
            } finally {
                FieldLog.end(context, clock() - t0, figures())
            }
        }
        // FR-GATH-17, ble-protocol 10.1 step 6: listen 2 s every 30 s for another gathering (two scan starts per
        // 30 s, under Android's limit of five); D-68 shows it quietly while it is heard. A resumed gathering's own beacons are ignored.
        s.launch {
            val others = OtherCampfires(l.gid, OTHER_TIMEOUT_MS)
            try {
                launch {
                    t.scanResults().collect { r ->
                        val b = r.manufacturerData?.let(BeaconCodec::decodeA) ?: r.localName?.let(BeaconCodec::decodeB) ?: return@collect
                        others.seen(b.gid, b.name, b.ended, b.relay, clock())
                    }
                }
                while (isActive) {
                    runCatching { t.startScan(Transport.ScanMode.LOW_DUTY) }
                    delay(OTHER_LISTEN_MS)
                    runCatching { t.stopScan() }
                    val other = others.near(clock(), (_mode.value as? Mode.Leading)?.name.orEmpty())
                    if (other != _otherNearby.value) Log.d("leader other gathering nearby: ${other ?: "none"}")
                    _otherNearby.value = other
                    delay(OTHER_EVERY_MS - OTHER_LISTEN_MS)
                }
            } finally {
                // Ended during a listen: the scan must not run on into the home screen's own scan.
                withContext(NonCancellable) { runCatching { t.stopScan() } }
                _otherNearby.value = null
            }
        }
        s.launch {
            // Diagnostics: the send loop's real rate, and what the stack confirmed per legacy set, every 5 s.
            var t0 = clock(); var ticks0 = l.ticks; var work0 = l.tickWorkMs
            var confirmed0 = (t as? AndroidTransport)?.legacyDataConfirmed().orEmpty()
            while (isActive) {
                delay(5_000)
                val t1 = clock(); val ticks = l.ticks - ticks0; val work = l.tickWorkMs - work0
                val confirmed = (t as? AndroidTransport)?.legacyDataConfirmed().orEmpty()
                val perSet = confirmed.mapIndexed { i, c -> "%.1f".format((c - (confirmed0.getOrNull(i) ?: 0)) * 1000.0 / (t1 - t0)) }
                Log.d("leader rate ticks/s=%.1f work/tick=%.1f ms confirmed/s per set=%s".format(ticks * 1000.0 / (t1 - t0), if (ticks > 0) work.toDouble() / ticks else 0.0, perSet))
                t0 = t1; ticks0 = l.ticks; work0 = l.tickWorkMs; confirmed0 = confirmed
            }
        }
        s.launch {
            var persisted = clock()
            var singersPersisted = l.singers
            // A minute back, so the first sample is taken at once (Long.MIN_VALUE overflowed the difference, seen 2026-10-27).
            var sampled = clock() - STATS_SAMPLE_MS
            while (isActive) {
                delay(1_000)
                // D-60: the singer count once a minute, while the opt-in is on.
                (_mode.value as? Mode.Leading)?.let { m ->
                    if (statsOn && clock() - sampled >= STATS_SAMPLE_MS) {
                        sampled = clock()
                        singerSamples += (clock() - m.startedAt) to l.singers
                    }
                }
                // 10.1 step 2 (v0.39): the record follows the singer table and is rewritten every 30 s, its write time marking when the leader was last alive.
                if (l.singers != singersPersisted || clock() - persisted >= RESUME_REWRITE_MS) {
                    singersPersisted = l.singers
                    persisted = clock()
                    persist(l)
                }
                peakSingers = maxOf(peakSingers, l.singers)
                val progress = l.progress()
                val help = l.helpShare()
                val quality = l.helpShare(QUALITY_WINDOW_MS)
                if (progress != (_mode.value as? Mode.Leading)?.progress) Log.d("leader progress passes=${progress?.passesDone} repairs=${progress?.repairs}")
                (_mode.value as? Mode.Leading)?.let { m ->
                    if (m.singers != l.singers || m.progress != progress || m.help != help || m.quality != quality || m.peak != peakSingers) {
                        _mode.value = m.copy(singers = l.singers, progress = progress, help = help, quality = quality, peak = peakSingers)
                    }
                }
            }
        }
    }

    /**
     * *Nu* on a separator (D-56, FR-LEAD-01, FR-LEAD-03): that song becomes the current one for every
     * singer; the one before is marked sung; skipped songs stay unmarked. *Nu* on the current song pushes it again.
     */
    suspend fun push(index: Int) = mutex.withLock {
        val m = _mode.value as? Mode.Leading ?: return@withLock
        val song = m.songs.getOrNull(index)?.takeIf { it.song != null } ?: return@withLock
        Log.d("leader push index=$index repush=${index == m.current}")
        pushLine(m, m.songs, song.line, setlistChanged = false)
    }

    /** *Wis* (D-65, FR-LEAD-06): nothing is being sung; the song that was current counts as sung. */
    suspend fun clear() = mutex.withLock {
        val m = _mode.value as? Mode.Leading ?: return@withLock
        val l = leader ?: return@withLock
        if (m.current == null) return@withLock
        val now = clock()
        val lastSongMs = lastPushAt?.let { now - it } ?: m.lastSongMs
        lastPushAt = null
        if (statsOn) songEvents += null to (now - m.startedAt)
        Log.d("leader clear")
        _mode.value = publish(l, m, m.songs, current = null, setlistChanged = false).copy(lastSongMs = lastSongMs)
        persist(l)
    }

    /** *Toevoegen* (D-58, FR-LEAD-03): the song goes at the end of the setlist and out ahead from now on. */
    suspend fun add(entry: SongbookEntry) = mutex.withLock {
        val m = _mode.value as? Mode.Leading ?: return@withLock
        val l = leader ?: return@withLock
        val songs = m.songs + LeaderSong(PreparedEntry(entry.libraryId, entry.songId, entry.title), entry, lines.incrementAndGet())
        Log.d("leader add ${entry.libraryId}/${entry.songId}")
        _mode.value = publish(l, m, songs, currentLine(m), setlistChanged = true)
        persist(l)
    }

    /**
     * *Nu zingen* (D-58, FR-LEAD-01): the song goes into the setlist right after the current one, at the
     * end when none is current, and is pushed in the same state (ble-protocol 10.5, v0.40).
     */
    suspend fun singNow(entry: SongbookEntry) = mutex.withLock {
        val m = _mode.value as? Mode.Leading ?: return@withLock
        val song = LeaderSong(PreparedEntry(entry.libraryId, entry.songId, entry.title), entry, lines.incrementAndGet())
        val at = m.current?.plus(1) ?: m.songs.size
        val songs = m.songs.toMutableList().also { it.add(at, song) }
        Log.d("leader sing now ${entry.libraryId}/${entry.songId} at=$at")
        pushLine(m, songs, song.line, setlistChanged = true)
    }

    /**
     * The setlist as the leader's sheet left it (D-58: a song moved, removed, or put back): the current
     * song and every sung one stay in it; anything else is refused.
     */
    suspend fun edit(songs: List<LeaderSong>) = mutex.withLock {
        val m = _mode.value as? Mode.Leading ?: return@withLock
        val l = leader ?: return@withLock
        val kept = songs.map { it.line }.toSet()
        val fixed = pushedLines + listOfNotNull(currentLine(m))
        if (!kept.containsAll(fixed) || kept.size != songs.size || songs.map { it.line } == m.songs.map { it.line }) return@withLock
        Log.d("leader setlist edited ${m.songs.size} -> ${songs.size}")
        _mode.value = publish(l, m, songs, currentLine(m), setlistChanged = true)
        persist(l)
    }

    private fun currentLine(m: Mode.Leading): Long? = m.current?.let { m.songs.getOrNull(it)?.line }

    /** A push of [line] in [songs], which may be a changed setlist; the last song's duration of D-62 follows. */
    private suspend fun pushLine(m: Mode.Leading, songs: List<LeaderSong>, line: Long, setlistChanged: Boolean) {
        val l = leader ?: return
        val previous = currentLine(m)
        if (line !in pushedLines) pushedLines += line
        // D-62: the song before this one lasted from its push until now (a re-push of the same song does not end it).
        val now = clock()
        val lastSongMs = if (line != previous && lastPushAt != null) now - lastPushAt!! else m.lastSongMs
        if (line != previous) lastPushAt = now
        if (statsOn && line != previous) songEvents += line to (now - m.startedAt)
        _mode.value = publish(l, m, songs, line, setlistChanged).copy(lastSongMs = lastSongMs)
        persist(l)
    }

    /**
     * The state for [songs] with [current] (a line) as the current song (ble-protocol 10.5): `cpos` and
     * `sung` are positions in this setlist (9.4, v0.40), renumbered whenever it changes; with
     * [setlistChanged] the setlist document and the songs ahead follow (7.4 rule 7).
     */
    private suspend fun publish(l: LeaderSession, m: Mode.Leading, songs: List<LeaderSong>, current: Long?, setlistChanged: Boolean): Mode.Leading {
        val cur = current?.let { c -> songs.indexOfFirst { it.line == c } }?.takeIf { it >= 0 }
        val p = cur?.let { preparedOf(songs[it]) }
        val sung = pushedLines.mapNotNull { line -> songs.indexOfFirst { it.line == line }.takeIf { it >= 0 } }.toSet()
        val ahead = if (setlistChanged) songs.map { preparedOf(it)?.document } else null
        l.push(LeaderSession.Publication(p?.ref, p?.title, p?.document, ahead = ahead, cpos = cur?.let { it + 1 }, sung = sung.map { it + 1 }.toSet()))
        return m.copy(songs = songs, current = cur, sung = sung - setOfNotNull(cur), progress = l.progress(), songsSung = pushedLines.size)
    }

    /** The prepared song of a line, read once; null when it cannot be pushed or broadcast. */
    private suspend fun preparedOf(song: LeaderSong): Prepared? {
        if (song.line in prepared) return prepared[song.line]
        val p = song.song?.let { runCatching { prepare(it) }.getOrNull() }
        prepared[song.line] = p
        return p
    }

    // ---- resume after the app died (FR-GATH-13, ble-protocol 10.9, guiDesign D-67) ----

    /** What the resume screen shows: the gathering's name and two words, and when its record was last written. */
    data class ResumeOffer(val name: String, val fingerprint: String, val stoppedWall: Long)

    private var pendingResume: ResumeRecord? = null

    /**
     * Called once at the start of the process, before the welcome screen. It is also the startup sweep
     * (architecture 5.4, FR-DATA-08): a record older than the window, or one that cannot be read, is deleted.
     */
    suspend fun resumeOffer(): ResumeOffer? = withContext(Dispatchers.IO) {
        if (_mode.value != Mode.Idle) return@withContext null
        resumeLock.withLock {
            if (!resumeStore.exists) return@withLock null
            val record = resumeStore.read()?.let { runCatching { ResumeRecord.decode(it) }.getOrNull() }
            val age = record?.let { wallClock() - it.writtenWall }
            if (record == null || age == null || age < 0 || age > RESUME_WINDOW_MS) {
                resumeStore.delete()
                Log.d("resume record deleted: ${if (record == null) "unreadable" else "older than the window"}")
                return@withLock null
            }
            pendingResume = record
            ResumeOffer(record.name, Fingerprint.of(gatheringIdOf(record.session.signing.publicKey, crypto)), record.writtenWall)
        }
    }

    /** *Opnieuw beginnen* (D-67): the record goes, and the app starts as usual. */
    suspend fun discardResume() {
        pendingResume = null
        withContext(Dispatchers.IO) { resumeLock.withLock { resumeStore.delete() } }
    }

    /**
     * *Hervatten* (D-67, 10.9): the same keys, `gid`, singers, `seq` and `ts`, and the setlist, the current
     * song and the sung ones as they were, the setlist resolved anew against the songbook. Throws when the
     * radio cannot start, leaving the record for another try.
     */
    suspend fun resume() = mutex.withLock {
        val r = checkNotNull(pendingResume) { "nothing to resume" }
        check(_mode.value == Mode.Idle) { "one gathering at a time (FR-GEN-02)" }
        stopScanning(stopRadio = false)
        val t = checkNotNull(transport()) { "no Bluetooth" }
        runCatching { t.stopScan() }
        val installed = songbook.entries()
        val songs = r.setlist.map { p ->
            val key = p.songKey?.split('/', limit = 2)
            val entry = PreparedEntry(key?.getOrNull(0), key?.getOrNull(1), p.title)
            LeaderSong(entry, resolve(entry, installed), lines.incrementAndGet())
        }
        prepared.clear()
        val ahead = songs.map { preparedOf(it)?.document }
        val current = r.current?.takeIf { songs.getOrNull(it)?.let { s -> prepared[s.line] } != null }
        val cur = current?.let { prepared[songs[it].line] }
        val protection = when (r.protection) {
            ResumeRecord.PROTECTION_CODE -> r.session.joinCode?.let { Protection.Code(it) } ?: Protection.Open
            ResumeRecord.PROTECTION_QR_ONLY -> r.session.joinCode?.let { Protection.QrOnly(it) } ?: Protection.Open
            else -> Protection.Open
        }
        val s = CoroutineScope(scope.coroutineContext + Job(scope.coroutineContext[Job]))
        val l = LeaderSession(t, crypto, s, clock, r.name, joinCode = r.session.joinCode, resume = r.session)
        GatheringService.start(context)
        try {
            l.start(
                LeaderSession.Publication(
                    cur?.ref, cur?.title, cur?.document, ahead = ahead,
                    cpos = current?.let { it + 1 }, sung = r.pushed.map { it + 1 }.toSet(),
                ),
            )
        } catch (e: Exception) {
            s.cancel()
            GatheringService.stop(context)
            throw e
        }
        session = s
        leader = l
        pendingResume = null
        startedWall = r.startedWall
        pushedLines.clear()
        pushedLines += r.pushed.map { songs[it].line }
        peakSingers = r.peak
        lastPushAt = null
        startStatistics()   // D-60: what came before the crash was in memory only; the record covers the campfire from here
        Log.d("leader resumed singers=${l.singers} current=$current pushed=${r.pushed} stoppedAgoMs=${wallClock() - r.writtenWall}")
        _mode.value = Mode.Leading(
            r.name, clock() - (wallClock() - r.startedWall).coerceAtLeast(0), l.singers, songs, current, r.pushed.toSet() - setOfNotNull(current),
            fingerprint = Fingerprint.of(l.gid), protection = protection,
            joinLink = JoinLink(l.gid, l.publicKey, protection.code, r.name).httpsUrl(testBuild()),
            peak = r.peak, songsSung = r.pushed.size,
        )
        running(l, s, t, resumed = true)
    }

    /** Writes the resume record for [l] (10.1 step 2); nothing once [l] is no longer the running gathering. */
    private suspend fun persist(l: LeaderSession) {
        val m = _mode.value as? Mode.Leading ?: return
        val record = ResumeRecord(
            l.snapshot(), m.name,
            when (m.protection) {
                Protection.Open -> ResumeRecord.PROTECTION_OPEN
                is Protection.Code -> ResumeRecord.PROTECTION_CODE
                is Protection.QrOnly -> ResumeRecord.PROTECTION_QR_ONLY
            },
            m.songs.map { ResumeRecord.Position(it.entry.songKey, it.entry.title) },
            pushedLines.mapNotNull { line -> m.songs.indexOfFirst { it.line == line }.takeIf { it >= 0 } }, m.current, startedWall, wallClock(), maxOf(peakSingers, m.singers),
        ).encode()
        withContext(Dispatchers.IO) {
            resumeLock.withLock {
                if (leader === l) runCatching { resumeStore.write(record) }.onFailure { Log.d("resume record not written: ${it.message}") }
            }
        }
    }

    /** A setlist song as it is pushed and as it is sent ahead; [document] is null for a song that may not be broadcast (FR-LIB-22). */
    private class Prepared(val ref: SongRef, val title: String, val document: SongDocument?)

    /** ble-protocol 9.5: the same document for the push and for sending ahead, so both frame to the same bytes. */
    private suspend fun prepare(e: SongbookEntry): Prepared? {
        val file = songbook.read(e.libraryId, e.directory, e.fileName)
        val song = SongParser.parse(file).song ?: return null
        val ref = SongRef(LibraryId(e.libraryId), SongId(e.songId), song.hash)
        return Prepared(ref, song.meta.title, if (song.meta.redistribute) SongDocument(ref, file, song.meta.licenceSummary(e.libraryName)) else null)
    }

    /** D-60: reads the opt-in at the start of a campfire; nothing is collected while it is off. */
    private suspend fun startStatistics() {
        statsOn = settings.statisticsOn.first()
        songEvents.clear()
        singerSamples.clear()
    }

    /**
     * D-60's record of the campfire that ends now, [endMs] after its start: identifiers, times from the start,
     * counts and the leader's own radio figures, and the day; no name, no device, no clock time (SP-16).
     */
    private fun campfireRecord(m: Mode.Leading, l: LeaderSession, endMs: Long): CampfireRecord {
        val byLine = m.songs.associateBy { it.line }
        val events = songEvents.toList()
        val pushes = events.mapIndexedNotNull { i, (line, at) ->
            val song = line?.let { byLine[it]?.song } ?: return@mapIndexedNotNull null
            val until = events.getOrNull(i + 1)?.second ?: endMs
            CampfireRecord.Push(song.libraryId, song.songId, at, until - at)
        }
        val pushed = events.mapNotNull { it.first }.toSet()
        val skipped = m.songs.filter { it.line !in pushed }.mapNotNull { s -> s.song?.let { it.libraryId to it.songId } ?: s.entry.songKey?.split('/', limit = 2)?.let { it[0] to it.getOrElse(1) { "" } } }
        val date = java.time.Instant.ofEpochMilli(startedWall).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
        return CampfireRecord(
            date, endMs, pushes, skipped, singerSamples.toList(), maxOf(peakSingers, m.singers),
            CampfireRecord.Radio(l.legacySets, l.extendedSet, l.framesSent, l.service.transfersServed, l.service.bytesServed,
                l.service.peakConnections, l.service.maxConnections, l.service.refusedConnections),
            CampfireRecord.Versions(appVersion, LibraryFileParser.IMPLEMENTED_VERSION, ArchiveImport.IMPLEMENTED_ARCHIVE_VERSION,
                "${ProtocolConstants.PROTOCOL_MAJOR}.${ProtocolConstants.PROTOCOL_MINOR}"),
        )
    }

    /** D-69: what the leader sees after ending a gathering; null once dismissed. */
    data class Summary(val name: String, val startedWall: Long, val durationMs: Long, val songs: Int, val singers: Int)

    private val _summary = MutableStateFlow<Summary?>(null)
    val summary: StateFlow<Summary?> = _summary

    fun dismissSummary() {
        _summary.value = null
    }

    /**
     * *Afsluiten* (10.10): singers see the end at once; the radio lingers 60 s in the background, then
     * stops. The leader gets the summary of D-69, set before the mode changes so the app can show it.
     */
    fun end() {
        val l = leader ?: return
        val s = session
        val m = _mode.value as? Mode.Leading
        leader = null
        session = null
        // Architecture 5.4 (v0.13): the record goes at once, so a leader that dies while ending is not offered an ended gathering.
        scope.launch(Dispatchers.IO) { resumeLock.withLock { resumeStore.delete() } }
        if (m != null) _summary.value = Summary(m.name, startedWall, clock() - m.startedAt, pushedLines.size, maxOf(peakSingers, m.singers))
        _mode.value = Mode.Idle
        if (m != null) {
            val sung = pushedLines.mapNotNull { line -> m.songs.firstOrNull { it.line == line }?.song?.let { MemorySong(it.libraryId, it.songId, it.title) } }
            remember(m.name, led = true, singers = maxOf(peakSingers, m.singers), songs = sung)
            if (statsOn) {
                val record = campfireRecord(m, l, clock() - m.startedAt)
                scope.launch(Dispatchers.IO) {
                    runCatching { statistics.add(record.date, record.durationMs, record.pushes.size, record.maxSingers, record.json()) }
                        .onFailure { Log.d("statistics not kept: ${it.message}") }
                }
            }
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

    /** From the list (D-22, D-26); [code] when the gathering needs one (FR-GATH-05). */
    fun join(nearby: Nearby, code: String? = null) = sing(nearby.gid, nearby.name, pinnedKey = null, code = code, viaQr = false)

    /**
     * From a join QR code (FR-GATH-19, FR-GATH-20, ble-protocol 6.5, guiDesign D-35): the key is pinned
     * from the code before anything is heard, and the code's join code is used without asking.
     */
    /** D-35: a join link that came in while Bluetooth was not ready; the home screen joins it once it is. */
    val pendingLink = MutableStateFlow<JoinLink?>(null)

    fun join(link: JoinLink) {
        if (_mode.value is Mode.Singing && singer != null) return
        if (_mode.value != Mode.Idle) return
        sing(link.gid, link.name ?: Fingerprint.of(link.gid), pinnedKey = link.pk, code = link.code, viaQr = true)
    }

    /** After a wrong join code: tries again with [code]. */
    fun enterCode(code: String) {
        val engine = singer ?: return
        (_mode.value as? Mode.Singing)?.let { _mode.value = it.copy(needsCode = false) }
        scope.launch { engine.enterCode(code) }
    }

    private fun sing(gid: GatheringId, name: String, pinnedKey: ByteArray?, code: String?, viaQr: Boolean) {
        if (_mode.value != Mode.Idle) return
        stopScanning(stopRadio = false)
        val t = transport() ?: return
        val s = CoroutineScope(scope.coroutineContext + Job(scope.coroutineContext[Job]))
        session = s
        currentRef = null
        startedWall = wallClock()
        _mode.value = Mode.Singing(name, Connection.CONNECTING, emptyList(), null, fingerprint = Fingerprint.of(gid), viaQr = viaQr, joiningSince = clock())
        s.launch {
            // The discovery scan stops before the engine starts its own; a stop that came later would silence the singer.
            runCatching { t.stopScan() }
            val local = songbook.entries().filter { it.hash.isNotEmpty() }.associateBy { it.hash }
            // FR-SING-01, D-44: the name the singer chose in settings, sealed to the leader; none when empty.
            val singerName = settings.displayName().ifEmpty { null }
            val engine = SingerEngine(t, crypto, s, clock, chooses = { it.gid == gid }, name = singerName, hasSong = { it.hash.toHex() in local },
                pinnedKey = pinnedKey, code = code)
            singer = engine
            GatheringService.start(context)
            // Architecture A-20: the fieldtest build's local log, once a minute while singing; counts and flags only.
            var repairs = 0
            var repairsFailed = 0
            if (FieldLog.active(context)) launch {
                val t0 = clock()
                fun figures() = mapOf("repairs" to repairs, "repairsFailed" to repairsFailed, "carrier" to engine.carrier.name.lowercase(), "state" to engine.machine.state.name.lowercase())
                FieldLog.start(context, "singer", fieldStart(t))
                try {
                    while (isActive) {
                        FieldLog.sample(context, clock() - t0, figures())
                        delay(FieldLog.SAMPLE_MS)
                    }
                } finally {
                    FieldLog.end(context, clock() - t0, figures())
                }
            }
            launch {
                engine.events.collect { e ->
                    if (e is SingerEngine.Event.Repaired) { if (e.ok) repairs++ else repairsFailed++ }
                    // A-20: event kinds, codes and times only; never names or song text.
                    when (e) {
                        is SingerEngine.Event.Delivered -> Log.d("singer delivered doc=${e.doc} repair=${e.viaRepair}")
                        is SingerEngine.Event.Repaired -> Log.d("singer repair ok=${e.ok} ms=${e.ms} failure=${e.failure}")
                        is SingerEngine.Event.StateFrame -> Unit        // per frame: too many for the log
                        is SingerEngine.Event.RepairDecided -> Log.d("singer repair decided groups=${e.groups} delay=${e.delayMs}")
                        is SingerEngine.Event.NewSeq -> Log.d("singer newseq ${e.seq}")
                        else -> Log.d("singer ${e::class.simpleName}")
                    }
                    when (e) {
                        is SingerEngine.Event.Joined -> when (e.result) {
                            JoinResponse.ACCEPTED -> { singing { it.copy(needsCode = false) }; connection(Connection.CONNECTED) }
                            JoinResponse.WRONG_CODE -> singing { it.copy(needsCode = true) }
                            else -> Unit
                        }
                        is SingerEngine.Event.NotTheCampfire -> singing { it.copy(notTheCampfire = true) }
                        // The beacon's new seq comes before the state that names the song; the verified state moves the page.
                        is SingerEngine.Event.Delivered -> when (e.doc) {
                            DocumentType.STATE -> current(engine)
                            DocumentType.SONG -> received(e)
                        }
                        is SingerEngine.Event.LocalSong -> local[e.ref.hash.toHex()]?.let { entry -> localSong(e.ref, entry) }
                        is SingerEngine.Event.EarlierSong -> earlier(e)
                        is SingerEngine.Event.HintedSong -> { Log.d("singer hint ${e.song.ref.song.value}"); hinted(e) }
                        is SingerEngine.Event.HintWithdrawn -> { Log.d("singer hint withdrawn ${e.ref.song.value}"); unhinted(e, engine) }
                        is SingerEngine.Event.Ended -> connection(Connection.ENDED)
                        else -> Unit
                    }
                }
            }
            launch {
                // D-29 (v0.21): the song bar on the divider of the song still coming in.
                while (isActive) {
                    delay(COLLECTING_REFRESH_MS)
                    val m = _mode.value as? Mode.Singing ?: continue
                    val waiting = m.current?.let { m.songs.getOrNull(it) }?.let { it.song == null && !it.libraryOnly } == true
                    val rows = engine.reception()
                    val blocks = if (waiting) rows.firstOrNull { it.doc == DocumentType.SONG }?.blocks else null
                    val island = rows
                    if (blocks != m.collecting || island.isNotEmpty() || m.reception.isNotEmpty()) {
                        (_mode.value as? Mode.Singing)?.let { _mode.value = it.copy(collecting = blocks, reception = island) }
                    }
                }
            }
            launch {
                var last: SingerState? = null
                while (isActive) {
                    delay(1_000)
                    if (engine.machine.state != last) { last = engine.machine.state; Log.d("singer state=$last") }
                    if (engine.machine.state != SingerState.IDLE) singing { if (it.found) it else it.copy(found = true) }
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
    suspend fun leave(keepService: Boolean = false) {
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
        // [keepService]: the caller (the app swiped away) keeps the process alive until the notice is out.
        if (!keepService) GatheringService.stop(context)
        if (notify) leaveJob = scope.launch {
            val t0 = clock()
            val sent = runCatching { engine!!.leave() }.getOrDefault(false)
            Log.d("singer leave sent=$sent ms=${clock() - t0}")
        }
    }

    /** Waits for the leave notice started by [leave], at most its 10-second budget (ble-protocol 10.11). */
    suspend fun awaitLeave() {
        leaveJob?.join()
    }

    /** FR-DATA-03: titles and identifiers only; a gathering in which nothing was sung leaves no memory. */
    private fun remember(name: String, led: Boolean, singers: Int?, songs: List<MemorySong>) {
        if (songs.isEmpty()) return
        val started = startedWall
        val ended = wallClock()
        scope.launch { runCatching { memories.add(name, started, ended, led, singers, songs) }.onFailure { Log.w("memory not written: ${it::class.simpleName}") } }
    }

    /**
     * ble-protocol 6.5: a test build (application id `….debug`, `….fieldtest`) marks its join link with its
     * name, so the join page opens that build instead of looking for the release app; a release build is null.
     */
    private fun testBuild(): String? = context.packageName.removePrefix(RELEASE_ID).removePrefix(".").ifEmpty { null }

    private fun singing(change: (Mode.Singing) -> Mode.Singing) {
        (_mode.value as? Mode.Singing)?.let { m -> change(m).takeIf { it != m }?.let { _mode.value = it } }
    }

    private fun connection(c: Connection) {
        val m = _mode.value as? Mode.Singing ?: return
        if (m.connection == Connection.ENDED || m.connection == c) return
        _mode.value = m.copy(connection = c)
    }

    /** A new state: the current song moves; a song not yet received gets its place with the state's title (D-29). */
    private fun current(engine: SingerEngine) {
        val body = engine.state?.body ?: return
        hintedRef = null      // the engine confirmed or withdrew any hint before delivering this state
        val cur = body.cur
        if (cur == null) {
            // D-65, FR-LEAD-06: cleared; nothing is current, the songs stay on the page.
            if (currentRef != null) { currentRef = null; singing { it.copy(current = null, cleared = true) } }
            return
        }
        currentRef = cur
        val m = _mode.value as? Mode.Singing ?: return
        val at = m.songs.indexOfFirst { it.ref == cur }
        _mode.value = if (at >= 0) m.copy(current = at, cleared = false)
        else m.copy(songs = m.songs + ReceivedSong(cur, body.title ?: cur.song.value, null, libraryOnly = DocumentType.SONG !in body.docs), current = m.songs.size, cleared = false)
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
        SongParser.parse(file).song?.let { place(ref, it, entry.libraryName) }
    }

    /**
     * D-47 (v0.29): a song sung before this singer joined, held ahead: placed before the songs it saw
     * pushed, in setlist order among the earlier ones; nothing when it is on the page already.
     */
    private fun earlier(e: SingerEngine.Event.EarlierSong) {
        val m = _mode.value as? Mode.Singing ?: return
        if (m.songs.any { it.ref == e.song.ref }) return
        val song = SongParser.parse(e.song.file).song ?: return
        val at = m.songs.indexOfFirst { it.earlier == null || it.earlier > e.position }.takeIf { it >= 0 } ?: m.songs.size
        val songs = m.songs.toMutableList().also { it.add(at, ReceivedSong(e.song.ref, song.meta.title, song, earlier = e.position)) }
        val current = songs.indexOfFirst { it.ref == currentRef }.takeIf { it >= 0 }
        _mode.value = m.copy(songs = songs, current = current)
    }

    /** BLE 7.5 (v0.43): the beacon named a song held ahead; it is the current song now, until the state says otherwise. */
    private fun hinted(e: SingerEngine.Event.HintedSong) {
        val m = _mode.value as? Mode.Singing ?: return
        val song = SongParser.parse(e.song.file).song ?: return
        hintAdded = m.songs.none { it.ref == e.song.ref }
        hintedRef = e.song.ref
        currentRef = e.song.ref
        place(e.song.ref, song)
        singing { it.copy(cleared = false) }
    }

    /** The hint was not confirmed: the page as the applied state has it, without the hinted song if the hint brought it. */
    private fun unhinted(e: SingerEngine.Event.HintWithdrawn, engine: SingerEngine) {
        if (hintedRef != e.ref) return
        hintedRef = null
        val m = _mode.value as? Mode.Singing ?: return
        val songs = if (hintAdded) m.songs.filterNot { it.ref == e.ref } else m.songs
        currentRef = engine.state?.body?.cur
        _mode.value = m.copy(songs = songs, current = songs.indexOfFirst { it.ref == currentRef }.takeIf { it >= 0 })
    }

    private fun place(ref: SongRef, song: Song, library: String? = null) {
        val m = _mode.value as? Mode.Singing ?: return
        val at = m.songs.indexOfFirst { it.ref == ref }
        val item = ReceivedSong(ref, song.meta.title, song, library = library)
        val songs = if (at >= 0) m.songs.toMutableList().also { it[at] = item } else m.songs + item
        // The song the latest verified state names is the current one, whichever arrived first.
        val current = songs.indexOfFirst { it.ref == currentRef }.takeIf { it >= 0 } ?: m.current ?: songs.lastIndex
        _mode.value = m.copy(songs = songs, current = current)
    }

    /** A-20: what every field-log session starts with besides the platform's own values: the radio's flags and the versions. */
    private fun fieldStart(t: Transport): Map<String, Any?> = mapOf(
        "caps" to mapOf(
            "peripheral" to t.capabilities.peripheralMode, "multipleSets" to t.capabilities.multipleAdvertisingSets,
            "extended" to t.capabilities.extendedAdvertising, "maxDataLength" to t.capabilities.maxAdvertisingDataLength,
        ),
        "app" to appVersion, "protocol" to "${ProtocolConstants.PROTOCOL_MAJOR}.${ProtocolConstants.PROTOCOL_MINOR}",
    )

    private fun resolve(e: PreparedEntry, installed: List<SongbookEntry>): SongbookEntry? =
        e.songKey?.let { key -> installed.firstOrNull { it.libraryId + "/" + it.songId == key } }
            ?: if (e.songKey == null) installed.filter { SetlistResolver.fold(it.title) == SetlistResolver.fold(e.title) }.singleOrNull() else null

    companion object {
        /** A gathering not heard for this long leaves the list (D-22). */
        const val NEARBY_TIMEOUT_MS = 10_000L
        /** guiDesign D-60: the singer count is kept once a minute. */
        private const val STATS_SAMPLE_MS = 60_000L
        /** ble-protocol 10.9, FR-GATH-13: how long after the record's last write a resume is offered (D-67; its setting is not built). */
        const val RESUME_WINDOW_MS = 10 * 60_000L
        /** 10.1 step 2 (v0.39): the record is rewritten at least this often while leading. */
        private const val RESUME_REWRITE_MS = 30_000L
        /** D-62: the link quality looks back this far. */
        private const val QUALITY_WINDOW_MS = 60_000L
        /** ble-protocol 10.1 step 6: while leading, listen this long, this often. */
        private const val OTHER_LISTEN_MS = 2_000L
        private const val OTHER_EVERY_MS = 30_000L
        /** D-68: another gathering not heard in two listens in a row is no longer shown. */
        private const val OTHER_TIMEOUT_MS = 65_000L
        /** FR-GATH-16: listen this long before a gathering may start (D-23). */
        const val LISTEN_BEFORE_START_MS = 5_000L
        private const val COLLECTING_REFRESH_MS = 500L
        /** The release application id (RUNBOOK Decisions); test builds add a suffix to it. */
        private const val RELEASE_ID = "nl.pe1mew.zangbundel.app"
    }
}
