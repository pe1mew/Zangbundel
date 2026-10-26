// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.app.ZangbundelApp
import nl.pe1mew.zangbundel.app.ui.ConfigurationScreen
import nl.pe1mew.zangbundel.app.ui.textPanel
import nl.pe1mew.zangbundel.app.ui.theme.Palette
import nl.pe1mew.zangbundel.platform.storage.AppSettings

/**
 * Settings (guiDesign D-44, D-81; FR-SING-01, FR-DATA-07): the group *Zingen* with *Naam voor de
 * leider* and *Ontvangst tonen*, and the group *Gegevens* with *Alle gegevens verwijderen*. Vibration,
 * volume keys, the not-found time, the theme and the statistics follow with their features.
 */
@Composable
fun SettingsScreen(onPrivacy: () -> Unit, onAllRemoved: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as ZangbundelApp).container }
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf<String?>(null) }
    val reception by container.singerSettings.receptionBar.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { name = container.storage.settings.displayName.first() }
    ConfigurationScreen {
        Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.settings_singing), style = MaterialTheme.typography.titleMedium)
            Column(Modifier.fillMaxWidth().textPanel().padding(16.dp)) {
                name?.let { current ->
                    OutlinedTextField(
                        value = current,
                        onValueChange = { v -> val n = v.take(AppSettings.DISPLAY_NAME_MAX); name = n; scope.launch { container.storage.settings.setDisplayName(n) } },
                        singleLine = true,
                        label = { Text(stringResource(R.string.settings_name)) },
                        placeholder = { Text(stringResource(R.string.settings_name_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.settings_name_note), style = MaterialTheme.typography.bodySmall, color = Palette.OffWhite)
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.reception_setting))
                        Text(stringResource(R.string.settings_reception_note), style = MaterialTheme.typography.bodySmall, color = Palette.OffWhite)
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = reception, onCheckedChange = { container.singerSettings.receptionBar.value = it })
                }
            }
            Text(stringResource(R.string.settings_data), style = MaterialTheme.typography.titleMedium)
            Column(Modifier.fillMaxWidth().textPanel().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPrivacy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_privacy)) }
                OutlinedButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_remove_all)) }
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(stringResource(R.string.settings_remove_all_title)) },
        text = { Text(stringResource(R.string.settings_remove_all_text)) },
        confirmButton = {
            TextButton(onClick = {
                confirm = false
                scope.launch { container.removeAllData(); onAllRemoved() }
            }) { Text(stringResource(R.string.settings_remove_all_confirm)) }
        },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
    )
}
