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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
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
import nl.pe1mew.zangbundel.app.ZangbundelApp
import nl.pe1mew.zangbundel.app.ui.singer.ReceptionIsland
import nl.pe1mew.zangbundel.core.model.GatheringId
import nl.pe1mew.zangbundel.core.protocol.Crypto
import nl.pe1mew.zangbundel.core.protocol.ProtocolConstants
import nl.pe1mew.zangbundel.core.protocol.Transport
import nl.pe1mew.zangbundel.core.protocol.beacon.Beacon
import nl.pe1mew.zangbundel.core.protocol.beacon.BeaconCodec
import nl.pe1mew.zangbundel.core.protocol.engine.LeaderSession
import nl.pe1mew.zangbundel.core.protocol.engine.SingerEngine
import nl.pe1mew.zangbundel.core.protocol.frame.FrameCodec
import nl.pe1mew.zangbundel.core.protocol.frame.Framer
import nl.pe1mew.zangbundel.core.protocol.payload.DocumentType
import nl.pe1mew.zangbundel.platform.ble.AndroidTransport
import nl.pe1mew.zangbundel.platform.crypto.SodiumCrypto
import kotlin.random.Random

/**
 * Two-phone broadcast test (debug and fieldtest builds only; assessment SP-24). One phone leads: a
 * beacon named [TEST_NAME] and the real carousel over the data sets it can open, pushing a new
 * synthetic document of random bytes (never lyrics) every [PUSH_INTERVAL_MS], with a signed state,
 * Info and the GATT service ([LeaderSession]). The other listens with [SingerEngine]: it registers
 * and repairs over GATT as the protocol says, and each push is timed from the beacon's new `seq` to
 * the verified song, by broadcast or by repair. Nothing is stored; results are on screen and in the debug log. *Lead extended only*
 * opens no legacy data sets, to tell whether a phone's extended set suffers from the legacy sets
 * beside it (D-05 run 12). *Probe the extended set* steps the extended set through secondary PHY,
 * data-update rate and frame size, a minute each, for a listener to count (D-05 run 15).
 */
class BleTestRunner(private val transport: AndroidTransport, private val crypto: Crypto = SodiumCrypto()) {
    private val _lines = MutableStateFlow(listOf<String>())
    val lines: StateFlow<List<String>> = _lines
    private val _reception = MutableStateFlow(listOf<SingerEngine.Reception>())
    /** The reception bar's rows while listening (D-81). */
    val reception: StateFlow<List<SingerEngine.Reception>> = _reception

    private fun show(vararg rows: String) { _lines.value = rows.toList() }
    private fun log(line: String) = Log.i("bletest $line")

    suspend fun lead(extendedOnly: Boolean = false) = coroutineScope {
        val random = Random(SystemClock.elapsedRealtimeNanos())
        val session = LeaderSession(transport, crypto, this, SystemClock::elapsedRealtime, TEST_NAME, extendedOnly, random)
        try {
            try {
                session.start(random.nextBytes(TEST_DOCUMENT_BYTES))
            } catch (e: IllegalStateException) {
                // Seen on the Huawei with Wi-Fi off: its single advertising instance refused (status 2).
                log("lead refused ${e.message?.replace(' ', '_')}")
                show("leader  $TEST_NAME", "refused by the phone: ${e.message}")
                return@coroutineScope
            }
            if (extendedOnly && !session.extendedSet) {
                log("lead extended_only refused")
                show("leader  $TEST_NAME", "extended_set  false", "this phone did not open an extended set")
                return@coroutineScope
            }
            log("lead legacy=${session.legacySets} extended=${session.extendedSet} extended_only=$extendedOnly gid=${session.gid} gatt=on")
            var pushes = 0
            var nextPush = SystemClock.elapsedRealtime() + PUSH_INTERVAL_MS
            while (isActive) {
                val now = SystemClock.elapsedRealtime()
                if (now >= nextPush) {
                    session.push(random.nextBytes(TEST_DOCUMENT_BYTES))
                    pushes++
                    log("lead push=$pushes seq=${session.seq} singers=${session.service.singerCount} transfers=${session.service.transfersServed}")
                    nextPush = now + PUSH_INTERVAL_MS
                }
                show(
                    "leader  $TEST_NAME",
                    "legacy_data_sets  ${session.legacySets}",
                    "extended_set  ${session.extendedSet}",
                    "seq  ${session.seq}",
                    "pushes  $pushes",
                    "frames_sent  ${session.framesSent}",
                    "singers  ${session.service.singerCount}",
                    "transfers_served  ${session.service.transfersServed}",
                    "next_push_in_s  ${(nextPush - now) / 1000}",
                )
                delay(1_000)
            }
        } finally {
            withContext(NonCancellable) { session.stop() }
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

    suspend fun listen() = coroutineScope {
        _reception.value = emptyList()
        val engine = SingerEngine(transport, crypto, this, SystemClock::elapsedRealtime, { it.name == TEST_NAME })
        val started = SystemClock.elapsedRealtime()
        var pushSeenAt: Long? = null
        var joined = "-"
        val broadcast = ArrayList<Long>()
        val repaired = ArrayList<Long>()
        var repairs = 0
        var repairsFailed = 0
        var repairMs = 0L
        var lastSong: ByteArray? = null
        fun render() {
            val all = (broadcast + repaired).sorted()
            show(
                "singer  carrier=${engine.carrier}  hardware_filters=${transport.hardwareFilters}",
                "joined  $joined",
                "state  ${engine.state?.body?.seq ?: "-"}",
                "deliveries  ${all.size}  (broadcast ${broadcast.size}, repair ${repaired.size})",
                "median_ms  ${all.getOrNull(all.size / 2) ?: "-"}",
                "max_ms  ${all.maxOrNull() ?: "-"}",
                "repairs  $repairs  (failed $repairsFailed, last $repairMs ms)",
                "running_s  ${(SystemClock.elapsedRealtime() - started) / 1000}",
                "scan_error  ${transport.scanFailure ?: "-"}",
            )
        }
        launch(start = CoroutineStart.UNDISPATCHED) {
            engine.events.collect { e ->
                when (e) {
                    is SingerEngine.Event.NewSeq -> { pushSeenAt = e.at; log("listen seq=${e.seq}") }
                    is SingerEngine.Event.FellBackToLegacy -> log("listen fell_back_to_legacy ms=${e.at - started}")
                    is SingerEngine.Event.LocalSong -> {}           // the test pushes random bytes; no singer holds them
                    is SingerEngine.Event.Ended -> log("listen ended ms=${e.at - started}")
                    is SingerEngine.Event.NotTheCampfire -> {}       // the test pins no key
                    is SingerEngine.Event.Joined -> { joined = "result ${e.result} after ${(e.at - started) / 1000} s"; log("listen joined result=${e.result} ms=${e.at - started} carrier=${engine.carrier} extended_scan=${transport.capabilities.extendedAdvertising}") }
                    is SingerEngine.Event.Repaired -> {
                        repairs++
                        if (!e.ok) repairsFailed++
                        repairMs = e.ms
                        log("listen repair groups=${e.groups.joinToString(",") { "${it.first}/${it.second}" }} ok=${e.ok} ms=${e.ms} failure=${e.failure?.replace(' ', '_')}")
                    }
                    is SingerEngine.Event.Delivered -> if (e.doc == DocumentType.SONG && !e.bytes.contentEquals(lastSong)) {
                        lastSong = e.bytes
                        val since = pushSeenAt
                        if (since != null) {
                            (if (e.viaRepair) repaired else broadcast) += e.at - since
                            log("listen delivery_ms=${e.at - since} via=${if (e.viaRepair) "repair" else "broadcast"} size=${e.bytes.size}")
                            pushSeenAt = null
                        }
                    }
                }
                render()
            }
        }
        engine.start()
        log("listen hardware_filters=${transport.hardwareFilters}")
        try {
            while (isActive) {
                render()
                _reception.value = engine.reception()
                delay(RECEPTION_REFRESH_MS)
            }
        } finally {
            withContext(NonCancellable) { transport.stopScan() }
        }
    }

    companion object {
        const val TEST_NAME = "Testvuur"
        const val PUSH_INTERVAL_MS = 60_000L
        const val PROBE_STEP_MS = 60_000L
        const val RECEPTION_REFRESH_MS = 250L
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
    // Settings → Zingen → Ontvangst tonen (D-81) stands on this screen until the settings screen exists.
    val settings = (context.applicationContext as ZangbundelApp).container.singerSettings
    val showReception by settings.receptionBar.collectAsState()
    val reception = runner?.reception?.collectAsState()

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
        Box(Modifier.fillMaxSize().padding(padding)) {
            val rows = reception?.value.orEmpty()
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
                    .padding(bottom = if (showReception && rows.isNotEmpty()) 72.dp else 0.dp),     // the island never covers the text
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Switch(checked = showReception, onCheckedChange = { settings.receptionBar.value = it })
                    Column {
                        Text(stringResource(R.string.reception_setting))
                        Text(stringResource(R.string.bletest_reception_note), style = MaterialTheme.typography.bodySmall)
                    }
                }
                message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                lines?.value?.forEach { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
            }
            if (showReception && rows.isNotEmpty()) {
                ReceptionIsland(rows, onDismiss = { settings.receptionBar.value = false }, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}
