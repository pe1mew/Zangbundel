// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.gathering

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.app.ZangbundelApp
import nl.pe1mew.zangbundel.app.gathering.Gathering
import nl.pe1mew.zangbundel.app.ui.ConfigurationScreen
import nl.pe1mew.zangbundel.app.ui.textPanel
import nl.pe1mew.zangbundel.app.ui.theme.Palette
import nl.pe1mew.zangbundel.core.protocol.payload.Info
import nl.pe1mew.zangbundel.platform.storage.PreparedSetlist
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Starting a gathering (guiDesign D-25): the proposed neutral name, never the device's name
 * [FR-GATH-01, assessment SP-01], and the prepared setlist to begin with, or none [FR-LEAD-02];
 * how singers join (FR-GATH-05, FR-GATH-21, D-71): open, with a four-digit code the leader can say, or
 * only through the QR code; *Starten* switches into leader mode. The code is drawn when the fire starts.
 * With battery saver on or the battery low, a line asks for a power bank (D-68).
 */
@Composable
fun StartScreen() {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as ZangbundelApp).container }
    val scope = rememberCoroutineScope()
    val proposed = stringResource(R.string.start_default_name, SimpleDateFormat("d MMM", LocalConfiguration.current.locales[0]).format(Date()))
    var name by rememberSaveable { mutableStateOf(proposed) }
    var chosen by rememberSaveable { mutableStateOf<Long?>(null) }
    var protection by rememberSaveable { mutableIntStateOf(PROTECTION_OPEN) }
    var setlists by remember { mutableStateOf<List<PreparedSetlist>>(emptyList()) }
    var starting by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val power = rememberPower()
    LaunchedEffect(Unit) { setlists = container.setlists.all() }
    ConfigurationScreen {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(stringResource(R.string.start_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(name, { name = it.take(Info.NAME_MAX_CHARS) }, singleLine = true, label = { Text(stringResource(R.string.start_name)) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.start_protection), style = MaterialTheme.typography.titleMedium)
            Choice(stringResource(R.string.start_open), null, protection == PROTECTION_OPEN) { protection = PROTECTION_OPEN }
            Choice(stringResource(R.string.start_code), stringResource(R.string.start_code_detail), protection == PROTECTION_CODE) { protection = PROTECTION_CODE }
            Choice(stringResource(R.string.start_qr_only), stringResource(R.string.start_qr_only_detail), protection == PROTECTION_QR) { protection = PROTECTION_QR }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.start_setlist), style = MaterialTheme.typography.titleMedium)
            LazyColumn(Modifier.weight(1f).fillMaxWidth().textPanel(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                item { Choice(stringResource(R.string.start_no_setlist), null, chosen == null) { chosen = null } }
                items(setlists, key = { it.uid }) { s ->
                    Choice(s.title, pluralStringResource(R.plurals.library_songs, s.songs, s.songs), chosen == s.uid) { chosen = s.uid }
                }
            }
            // D-68: the battery is settled before the fire; starting stays allowed [FR-GATH-15].
            if (power.warnBeforeStart) {
                // The button under the line: beside it the line was squeezed to a narrow column (S23, 2026-10-27).
                Column(Modifier.fillMaxWidth().padding(top = 8.dp).textPanel().padding(start = 12.dp, end = 4.dp, top = 8.dp)) {
                    Text(stringResource(R.string.start_battery), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
                    TextButton(onClick = { openBatterySettings(context) }, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.battery_settings)) }
                }
            }
            if (failed) Text(stringResource(R.string.start_failed), color = Palette.FireOrangeLight, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    starting = true
                    failed = false
                    scope.launch {
                        // On success the mode changes and the app opens the leader's page.
                        failed = runCatching { 
                            val p = when (protection) {
                                PROTECTION_CODE -> Gathering.Protection.newCode()
                                PROTECTION_QR -> Gathering.Protection.newQrOnly()
                                else -> Gathering.Protection.Open
                            }
                            container.gathering.lead(name.trim().ifEmpty { proposed }, chosen, p)
                        }.isFailure
                        starting = false
                    }
                },
                enabled = !starting,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(stringResource(R.string.start_start)) }
        }
    }
}

@Composable
private fun Choice(title: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(title)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.OffWhite) }
        }
    }
}

private const val PROTECTION_OPEN = 0
private const val PROTECTION_CODE = 1
private const val PROTECTION_QR = 2
