// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.app.ZangbundelApp
import nl.pe1mew.zangbundel.app.ui.ConfigurationScreen
import nl.pe1mew.zangbundel.app.ui.textPanel
import nl.pe1mew.zangbundel.app.ui.theme.Palette
import nl.pe1mew.zangbundel.platform.storage.AppSettings

/**
 * Settings (guiDesign D-44, D-76; FR-LANG-03, FR-SING-01, FR-DATA-07): the group *Taal*, the group *Zingen* with *Naam voor de leider*,
 * then, as buttons on the backdrop like those on About, *Statistieken van kampvuren* (D-60, D-62) and
 * *Alle gegevens verwijderen*. The
 * lyric view's theme, text size and keep-screen-on are set from the singer's toolbar (D-17); vibration,
 * volume keys and the not-found time follow with their features.
 */
@Composable
fun SettingsScreen(onStatistics: () -> Unit, onAllRemoved: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as ZangbundelApp).container }
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val languages = stringArrayResource(R.array.app_languages)
    // Null is Automatisch: the phone's language, English when the app does not have it (D-76).
    var language by remember { mutableStateOf(AppCompatDelegate.getApplicationLocales()[0]?.language) }
    LaunchedEffect(Unit) { name = container.storage.settings.displayName.first() }
    ConfigurationScreen {
        Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)
            // D-76 (FR-LANG-03): first, so it is found also by someone who cannot read the current language.
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
            Column(Modifier.fillMaxWidth().textPanel().padding(16.dp)) {
                val auto = stringResource(R.string.settings_language_auto)
                LanguageMenu(
                    choices = listOf<String?>(null) + languages,
                    selected = language,
                    nameOf = { it?.let(::ownName) ?: auto },
                    onChoose = { language = it; applyLanguage(it) },
                )
            }
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
                    Text(stringResource(R.string.settings_name_note), style = MaterialTheme.typography.bodySmall, color = Palette.OffWhite, modifier = Modifier.padding(top = 8.dp))
                }
            }
            // Buttons stand on the backdrop without a panel or heading, as on About (D-44 as of v0.59).
            // D-60: the opt-in lives on its own screen, which says exactly what is kept; no switch here (P7).
            OutlinedButton(onClick = onStatistics, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_statistics)) }
            OutlinedButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_remove_all)) }
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

/**
 * The app language through AppCompat's per-app locale (architecture A-16): the platform keeps it on Android 13
 * and later, AppCompat's `autoStoreLocales` on 8 to 12. The activity is recreated in the new language.
 */
private fun applyLanguage(tag: String?) {
    AppCompatDelegate.setApplicationLocales(if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag))
}

/** A language in its own name, so it is found whatever the app's language is: *Nederlands*, *English*, *Deutsch*. */
private fun ownName(tag: String): String = Locale.forLanguageTag(tag).let { it.getDisplayLanguage(it).replaceFirstChar { c -> c.titlecase(it) } }

/** D-76: one field showing the current choice; a tap opens the list, so a longer list of languages stays one line. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageMenu(choices: List<String?>, selected: String?, nameOf: (String?) -> String, onChoose: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = nameOf(selected),
            onValueChange = {},
            readOnly = true,
            // Wraps rather than cuts *Automatisch (taal van de telefoon)* (A52, 2026-10-28).
            maxLines = 2,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { tag ->
                DropdownMenuItem(
                    text = { Text(nameOf(tag)) },
                    onClick = { open = false; if (tag != selected) onChoose(tag) },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
