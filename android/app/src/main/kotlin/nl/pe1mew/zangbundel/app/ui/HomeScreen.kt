// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.app.ZangbundelApp
import nl.pe1mew.zangbundel.app.diagnostics.Diagnostics
import nl.pe1mew.zangbundel.app.diagnostics.DiagnosticsEntry
import nl.pe1mew.zangbundel.app.gathering.BluetoothAccess
import nl.pe1mew.zangbundel.app.gathering.Gathering
import nl.pe1mew.zangbundel.app.ui.theme.Palette

/**
 * The home screen (guiDesign D-21 to D-24): it scans while shown, and what it finds is the screen.
 * Gatherings in range are cards to join with one tap; with none, a quiet search and the start
 * button large. Without Bluetooth or its permission a card explains why it is needed and asks only
 * on a tap (FR-GEN-05). Starting waits for 5 seconds of listening and is refused while a gathering is
 * in range (FR-GATH-16, D-23); a phone that cannot advertise says so (D-24). Below, the icon row.
 */
@Composable
fun HomeScreen(onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val gathering = remember { (context.applicationContext as ZangbundelApp).container.gathering }
    var ready by remember { mutableStateOf(BluetoothAccess.ready(context)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { ready = BluetoothAccess.ready(context) }
    val switchOn = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { ready = BluetoothAccess.ready(context) }
    DisposableEffect(ready) {
        if (ready) gathering.startScanning()
        onDispose { gathering.stopScanning() }
    }
    val nearby by gathering.nearby.collectAsState()
    val since by gathering.scanningSince.collectAsState()
    var now by remember { mutableLongStateOf(gathering.now()) }
    LaunchedEffect(Unit) { while (true) { now = gathering.now(); delay(500) } }
    var refusedBy by remember { mutableStateOf<Gathering.Nearby?>(null) }

    ConfigurationScreen {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(48.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(32.dp))
            // Join area (top).
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    !ready -> Column(Modifier.fillMaxWidth().textPanel().padding(16.dp)) {
                        Text(stringResource(R.string.home_bluetooth_why))
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            if (!BluetoothAccess.granted(context)) ask.launch(BluetoothAccess.permissions())
                            else switchOn.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(if (BluetoothAccess.granted(context)) R.string.home_bluetooth_on else R.string.home_bluetooth_allow))
                        }
                    }
                    nearby.isEmpty() -> Column(Modifier.fillMaxWidth().textPanel().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.home_searching))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.home_qr_help), style = MaterialTheme.typography.bodySmall, color = Palette.OffWhite)
                    }
                    else -> {
                        Text(stringResource(R.string.home_nearby), style = MaterialTheme.typography.titleMedium)
                        for (g in nearby) NearbyCard(g) { gathering.join(g) }
                    }
                }
            }
            // Start area (bottom).
            when {
                ready && !gathering.canLead -> Text(stringResource(R.string.home_cannot_lead), color = Palette.OffWhite, modifier = Modifier.padding(8.dp))
                nearby.isNotEmpty() -> TextButton(onClick = { refusedBy = nearby.first() }) {
                    Text(stringResource(R.string.home_start_self), color = Palette.OffWhite)
                }
                else -> {
                    val waited = since?.let { now - it } ?: 0L
                    val left = ((Gathering.LISTEN_BEFORE_START_MS - waited + 999) / 1000).coerceAtLeast(0)
                    Button(onClick = { onOpen(Routes.START) }, enabled = ready && since != null && left == 0L, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text(if (ready && left > 0) stringResource(R.string.home_listening, left) else stringResource(R.string.home_start))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                IconEntry(R.drawable.ic_library, R.string.home_songbook) { onOpen(Routes.SONGBOOK) }
                IconEntry(R.drawable.ic_memories, R.string.home_memories) { onOpen(Routes.MEMORIES) }
                IconEntry(R.drawable.ic_settings, R.string.home_settings) { onOpen(Routes.SETTINGS) }
                IconEntry(R.drawable.ic_about, R.string.home_about) { onOpen(Routes.ABOUT) }
            }
            // Debug and field-test builds only; the release stand-in draws nothing.
            if (Diagnostics.AVAILABLE) DiagnosticsEntry(onOpen)
        }
    }
    // D-23: refused with the gathering's name and a join button.
    refusedBy?.let { g ->
        AlertDialog(
            onDismissRequest = { refusedBy = null },
            title = { Text(stringResource(R.string.home_refused_title)) },
            text = { Text(stringResource(R.string.home_refused_text, g.name)) },
            confirmButton = { TextButton(onClick = { refusedBy = null; gathering.join(g) }) { Text(stringResource(R.string.home_join_named, g.name)) } },
            dismissButton = { TextButton(onClick = { refusedBy = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** A gathering in range (D-22): tall, full width, its name and how well it is heard [FR-GATH-04]; tapping joins. */
@Composable
private fun NearbyCard(g: Gathering.Nearby, onJoin: () -> Unit) {
    val signal = when {
        g.rssi >= -65 -> R.string.home_signal_strong
        g.rssi >= -80 -> R.string.home_signal_fair
        else -> R.string.home_signal_weak
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(RoundedCornerShape(12.dp)).textPanel().clickable(onClick = onJoin).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(g.name, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(signal), style = MaterialTheme.typography.bodySmall, color = Palette.OffWhite)
        }
        if (g.codeRequired) Text("🔒", modifier = Modifier.padding(start = 8.dp))
    }
}

/** One entry of D-21's icon row: the icon with its name under it, small; [onClick] null while the screen is not built. */
@Composable
private fun IconEntry(icon: Int, label: Int, onClick: (() -> Unit)?) {
    val alpha = if (onClick == null) 0.38f else 1f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = onClick != null) { onClick?.invoke() }.padding(8.dp).alpha(alpha),
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(28.dp))
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall)
    }
}
