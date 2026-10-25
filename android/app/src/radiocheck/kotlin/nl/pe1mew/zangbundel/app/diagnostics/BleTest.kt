// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.diagnostics

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.pe1mew.zangbundel.app.Log
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.core.model.GatheringId
import nl.pe1mew.zangbundel.core.protocol.ProtocolConstants
import nl.pe1mew.zangbundel.core.protocol.Transport
import nl.pe1mew.zangbundel.core.protocol.beacon.Beacon
import nl.pe1mew.zangbundel.core.protocol.beacon.BeaconCodec
import nl.pe1mew.zangbundel.core.protocol.carousel.Carousel
import nl.pe1mew.zangbundel.core.protocol.frame.Carrier
import nl.pe1mew.zangbundel.core.protocol.frame.FrameCodec
import nl.pe1mew.zangbundel.core.protocol.frame.Framer
import nl.pe1mew.zangbundel.core.protocol.frame.Reassembler
import nl.pe1mew.zangbundel.core.protocol.payload.DocumentType
import nl.pe1mew.zangbundel.platform.ble.AndroidTransport
import kotlin.random.Random

/**
 * Two-phone broadcast test (debug and fieldtest builds only; assessment SP-24). One phone leads: a
 * beacon named [TEST_NAME] and the real carousel over the data sets it can open, pushing a new
 * synthetic document of random bytes (never lyrics) every [PUSH_INTERVAL_MS]. The other listens:
 * the real codecs and reassembler, timing each push from the beacon's new `seq` to the complete
 * document. Nothing is stored; results are on screen and in the debug log. *Lead extended only*
 * opens no legacy data sets, to tell whether a phone's extended set suffers from the legacy sets
 * beside it (D-05 run 12). *Probe the extended set* steps the extended set through secondary PHY,
 * data-update rate and frame size, a minute each, for a listener to count (D-05 run 15).
 */
class BleTestRunner(private val transport: AndroidTransport) {
    private val _lines = MutableStateFlow(listOf<String>())
    val lines: StateFlow<List<String>> = _lines

    private fun show(vararg rows: String) { _lines.value = rows.toList() }
    private fun log(line: String) = Log.i("bletest $line")

    suspend fun lead(extendedOnly: Boolean = false) {
        val random = Random(SystemClock.elapsedRealtimeNanos())
        val gid = GatheringId(random.nextBytes(GatheringId.SIZE))
        val gid16 = ((gid.bytes[0].toInt() and 0xFF) shl 8) or (gid.bytes[1].toInt() and 0xFF)
        var seq = random.nextInt(0x10000)
        try {
            // The beacon first: a phone with one advertising instance (degraded mode, 7.8) still sends it.
            try {
                transport.setBeacon(BeaconCodec.encodeA(Beacon(1, gid, seq, 0, 0, TEST_NAME)))
            } catch (e: IllegalStateException) {
                // Seen on the Huawei with Wi-Fi off: its single advertising instance refused (status 2).
                log("lead beacon_refused ${e.message?.replace(' ', '_')}")
                show("leader  $TEST_NAME", "beacon refused by the phone: ${e.message}")
                return
            }
            val legacy = if (extendedOnly) 0 else transport.openLegacyDataSets(Carousel.MAX_LEGACY_DATA_SETS)
            val extended = transport.openExtendedDataSet()
            if (extendedOnly && !extended) {
                log("lead extended_only refused")
                show("leader  $TEST_NAME", "extended_set  false", "this phone did not open an extended set")
                return
            }
            if (legacy == 0 && !extendedOnly) {
                log("lead legacy=0 beacon only")
                show("leader  $TEST_NAME", "legacy_data_sets  0", "degraded mode: beacon only, 10 per second")
                while (kotlinx.coroutines.currentCoroutineContext().isActive) delay(1_000)
                return
            }
            val flags = ProtocolConstants.FLAG_BCAST or (if (extended) ProtocolConstants.FLAG_EXT else 0)
            transport.setBeacon(BeaconCodec.encodeA(Beacon(1, gid, seq, flags, 0, TEST_NAME)))
            // The carousel needs a legacy set; with extended only its legacy lane is built but never sent.
            val carousel = Carousel(gid16, legacy.coerceAtLeast(1), extended)
            fun documents(songSeed: Int) = listOf(
                Carousel.Document(DocumentType.STATE, seq, random.nextBytes(240)),
                Carousel.Document(DocumentType.INFO, 0x1111, ByteArray(150) { 0x11 }),
                Carousel.Document(DocumentType.SONG, songSeed and 0xFFFF, random.nextBytes(TEST_DOCUMENT_BYTES)),
            )
            carousel.publish(documents(random.nextInt()))
            log("lead legacy=$legacy extended=$extended extended_only=$extendedOnly gid16=$gid16 scannable_data_sets=${transport.scannableDataSets}")
            var pushes = 0
            var frames = 0L
            var nextPush = SystemClock.elapsedRealtime() + PUSH_INTERVAL_MS
            val laneIndex = carousel.lanes.filter { !extendedOnly || it == Carousel.Lane.Extended }.associateWith { lane ->
                when (lane) {
                    Carousel.Lane.Small -> 0
                    is Carousel.Lane.Large -> lane.index
                    Carousel.Lane.Extended -> Transport.EXTENDED_SET
                }
            }
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                val now = SystemClock.elapsedRealtime()
                if (now >= nextPush) {
                    seq = (seq + 1) and 0xFFFF
                    pushes++
                    carousel.publish(documents(random.nextInt()))
                    transport.setBeacon(BeaconCodec.encodeA(Beacon(1, gid, seq, flags, 0, TEST_NAME)))
                    log("lead push=$pushes seq=$seq")
                    nextPush = now + PUSH_INTERVAL_MS
                }
                for ((lane, index) in laneIndex) {
                    carousel.next(lane)?.let { transport.setFrame(index, FrameCodec.encode(it)); frames++ }
                }
                show(
                    "leader  $TEST_NAME  gid16=$gid16",
                    "legacy_data_sets  $legacy",
                    "extended_set  $extended",
                    "seq  $seq",
                    "pushes  $pushes",
                    "frames_sent  $frames",
                    "next_push_in_s  ${(nextPush - now) / 1000}",
                )
                delay(Carousel.ADVERTISING_INTERVAL_MS)
            }
        } finally {
            withContext(NonCancellable) { transport.stopAdvertising() }
        }
    }

    /** One step of [probeExtended]: secondary PHY, data update interval (null: set once), frame data size. */
    private class ProbeStep(val phy2M: Boolean, val updateMs: Long?, val fs: Int) {
        override fun toString() = "phy=${if (phy2M) "2M" else "1M"} update=${updateMs?.let { "${it}ms" } ?: "fixed"} fs=$fs"
    }

    suspend fun probeExtended() {
        val random = Random(SystemClock.elapsedRealtimeNanos())
        val gid = GatheringId(random.nextBytes(GatheringId.SIZE))
        val gid16 = ((gid.bytes[0].toInt() and 0xFF) shl 8) or (gid.bytes[1].toInt() and 0xFF)
        val max = ProtocolConstants.EXTENDED_FRAME_MAX_DATA_BYTES
        val steps = listOf(
            ProbeStep(true, 100, max), ProbeStep(true, null, max), ProbeStep(true, 1_000, max),
            ProbeStep(false, null, max), ProbeStep(false, 100, max), ProbeStep(true, 100, 60),
        )
        log("probe start gid16=$gid16 max_adv_data=${transport.capabilities.maxAdvertisingDataLength}")
        try {
            for ((n, step) in steps.withIndex()) {
                transport.stopAdvertising()
                transport.extendedSecondaryPhy2M = step.phy2M
                transport.setBeacon(BeaconCodec.encodeA(Beacon(1, gid, n, ProtocolConstants.FLAG_BCAST or ProtocolConstants.FLAG_EXT, 0, TEST_NAME)))
                if (!transport.openExtendedDataSet()) {
                    log("probe step=${n + 1} $step refused")
                    continue
                }
                val frames = Framer.extended(gid16, DocumentType.SONG, n, random.nextBytes(TEST_DOCUMENT_BYTES), fs = step.fs)
                log("probe step=${n + 1} $step start")
                val end = SystemClock.elapsedRealtime() + PROBE_STEP_MS
                var i = 0
                transport.setFrame(Transport.EXTENDED_SET, FrameCodec.encode(frames[0]))
                while (SystemClock.elapsedRealtime() < end) {
                    val interval = step.updateMs ?: 1_000L
                    delay(interval)
                    if (step.updateMs != null) transport.setFrame(Transport.EXTENDED_SET, FrameCodec.encode(frames[++i % frames.size]))
                    val r = transport.extendedDataResults()
                    show(
                        "probe  step ${n + 1} of ${steps.size}",
                        "$step",
                        "data_set_ok  ${r?.first}",
                        "data_set_failed  ${r?.second}  last_status ${r?.third}",
                        "seconds_left  ${(end - SystemClock.elapsedRealtime()) / 1000}",
                    )
                }
                val r = transport.extendedDataResults()
                log("probe step=${n + 1} end data_ok=${r?.first} data_failed=${r?.second} last_status=${r?.third}")
            }
            show("probe  done")
        } finally {
            withContext(NonCancellable) {
                transport.stopAdvertising()
                transport.extendedSecondaryPhy2M = true
            }
        }
    }

    suspend fun listen() {
        val carrier = if (transport.capabilities.extendedAdvertising) Carrier.EXTENDED else Carrier.LEGACY
        var gid: GatheringId? = null
        var reassembler: Reassembler? = null
        var lastSeq: Int? = null
        var pushSeenAt: Long? = null
        var lastSongDv: Int? = null
        val deliveries = ArrayList<Long>()
        var beacons = 0
        var frames = 0
        var rssi = 0
        val started = SystemClock.elapsedRealtime()
        try {
            transport.startScan(Transport.ScanMode.GATHERING)
            log("listen carrier=$carrier hardware_filters=${transport.hardwareFilters}")
            transport.scanResults().collect { r ->
                val data = r.manufacturerData ?: return@collect
                val now = SystemClock.elapsedRealtime()
                val beacon = BeaconCodec.decodeA(data)
                if (beacon != null) {
                    if (beacon.name != TEST_NAME) return@collect
                    if (gid == null) {
                        gid = beacon.gid
                        reassembler = Reassembler(((beacon.gid.bytes[0].toInt() and 0xFF) shl 8) or (beacon.gid.bytes[1].toInt() and 0xFF), carrier)
                    }
                    if (beacon.gid != gid) return@collect
                    beacons++
                    rssi = r.rssi
                    if (lastSeq != null && beacon.seq != lastSeq) { pushSeenAt = now; log("listen seq=${beacon.seq}") }
                    lastSeq = beacon.seq
                } else {
                    val frame = FrameCodec.decode(data) ?: return@collect
                    val result = reassembler?.accept(frame) ?: return@collect
                    frames++
                    if (result is Reassembler.Result.Complete && result.doc == DocumentType.SONG && result.dv != lastSongDv) {
                        val since = pushSeenAt
                        if (since != null && lastSongDv != null) {
                            deliveries += now - since
                            log("listen delivery_ms=${now - since} size=${result.bytes.size}")
                        }
                        lastSongDv = result.dv
                        pushSeenAt = null
                    }
                }
                val elapsed = ((now - started) / 1000).coerceAtLeast(1)
                show(
                    "singer  carrier=$carrier  hardware_filters=${transport.hardwareFilters}",
                    "gathering  ${if (gid == null) "searching" else TEST_NAME}",
                    "rssi  $rssi",
                    "beacons  $beacons  (${"%.1f".format(java.util.Locale.ROOT, beacons * 1000.0 / (now - started).coerceAtLeast(1))}/s)",
                    "frames  $frames  (${frames / elapsed}/s)",
                    "deliveries  ${deliveries.size}",
                    "last_ms  ${deliveries.lastOrNull() ?: "-"}",
                    "median_ms  ${deliveries.sorted().getOrNull(deliveries.size / 2) ?: "-"}",
                    "max_ms  ${deliveries.maxOrNull() ?: "-"}",
                    "scan_error  ${transport.scanFailure ?: "-"}",
                )
            }
        } finally {
            withContext(NonCancellable) { transport.stopScan() }
        }
    }

    companion object {
        const val TEST_NAME = "Testvuur"
        const val PUSH_INTERVAL_MS = 60_000L
        const val PROBE_STEP_MS = 60_000L
        /** About a compressed song of 4 kB (NFR-PERF-01). Random bytes: incompressible, never lyrics. */
        const val TEST_DOCUMENT_BYTES = 2_000
    }
}

private fun neededPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

@Composable
fun BleTestScreen() {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val scope = rememberCoroutineScope()
    val adapter = remember { (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter }
    val runner = remember { adapter?.let { BleTestRunner(AndroidTransport(context, it)) } }
    var job by remember { mutableStateOf<Job?>(null) }
    var message by remember { mutableStateOf<Int?>(null) }
    var pending by remember { mutableStateOf<(suspend BleTestRunner.() -> Unit)?>(null) }
    val lines = runner?.lines?.collectAsState()

    fun start(scope: CoroutineScope, what: suspend BleTestRunner.() -> Unit) {
        val r = runner ?: run { message = R.string.radiocheck_no_bluetooth; return }
        if (adapter?.isEnabled != true) { message = R.string.radiocheck_bluetooth_off; return }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !lm.isLocationEnabled) { message = R.string.bletest_location_off; return }
        }
        message = null
        job?.cancel()
        job = scope.launch { r.what() }
    }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        val what = pending
        pending = null
        if (granted.values.all { it } && what != null) start(scope, what) else message = R.string.radiocheck_permission_denied
    }

    fun request(what: suspend BleTestRunner.() -> Unit) {
        val missing = neededPermissions().filter { context.checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) start(scope, what) else { pending = what; permissions.launch(missing.toTypedArray()) }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.bletest_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.bletest_explanation), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { request { lead() } }) { Text(stringResource(R.string.bletest_lead)) }
                Button(onClick = { request { listen() } }) { Text(stringResource(R.string.bletest_listen)) }
            }
            Button(onClick = { request { lead(extendedOnly = true) } }) { Text(stringResource(R.string.bletest_lead_extended_only)) }
            Button(onClick = { request { probeExtended() } }) { Text(stringResource(R.string.bletest_probe_extended)) }
            OutlinedButton(onClick = { job?.cancel(); job = null }) { Text(stringResource(R.string.bletest_stop)) }
            message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            lines?.value?.forEach { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
