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
import nl.pe1mew.zangbundel.core.protocol.frame.Reassembler
import nl.pe1mew.zangbundel.core.protocol.payload.DocumentType
import nl.pe1mew.zangbundel.platform.ble.AndroidTransport
import kotlin.random.Random

/**
 * Two-phone broadcast test (debug and fieldtest builds only; assessment SP-24). One phone leads: a
 * beacon named [TEST_NAME] and the real carousel over the data sets it can open, pushing a new
 * synthetic document of random bytes (never lyrics) every [PUSH_INTERVAL_MS]. The other listens:
 * the real codecs and reassembler, timing each push from the beacon's new `seq` to the complete
 * document. Nothing is stored; results are on screen and in the debug log.
 */
class BleTestRunner(private val transport: AndroidTransport) {
    private val _lines = MutableStateFlow(listOf<String>())
    val lines: StateFlow<List<String>> = _lines

    private fun show(vararg rows: String) { _lines.value = rows.toList() }
    private fun log(line: String) = Log.i("bletest $line")

    suspend fun lead() {
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
            val legacy = transport.openLegacyDataSets(Carousel.MAX_LEGACY_DATA_SETS)
            val extended = transport.openExtendedDataSet()
            if (legacy == 0) {
                log("lead legacy=0 beacon only")
                show("leader  $TEST_NAME", "legacy_data_sets  0", "degraded mode: beacon only, 10 per second")
                while (kotlinx.coroutines.currentCoroutineContext().isActive) delay(1_000)
                return
            }
            val flags = ProtocolConstants.FLAG_BCAST or (if (extended) ProtocolConstants.FLAG_EXT else 0)
            transport.setBeacon(BeaconCodec.encodeA(Beacon(1, gid, seq, flags, 0, TEST_NAME)))
            val carousel = Carousel(gid16, legacy, extended)
            fun documents(songSeed: Int) = listOf(
                Carousel.Document(DocumentType.STATE, seq, random.nextBytes(240)),
                Carousel.Document(DocumentType.INFO, 0x1111, ByteArray(150) { 0x11 }),
                Carousel.Document(DocumentType.SONG, songSeed and 0xFFFF, random.nextBytes(TEST_DOCUMENT_BYTES)),
            )
            carousel.publish(documents(random.nextInt()))
            log("lead legacy=$legacy extended=$extended gid16=$gid16 scannable_data_sets=${transport.scannableDataSets}")
            var pushes = 0
            var frames = 0L
            var nextPush = SystemClock.elapsedRealtime() + PUSH_INTERVAL_MS
            val laneIndex = carousel.lanes.associateWith { lane ->
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
            OutlinedButton(onClick = { job?.cancel(); job = null }) { Text(stringResource(R.string.bletest_stop)) }
            message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            lines?.value?.forEach { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
