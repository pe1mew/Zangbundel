// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.library

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.app.library.DefaultLibrary
import nl.pe1mew.zangbundel.app.library.LibraryRepository
import nl.pe1mew.zangbundel.app.ui.ConfigurationScreen
import nl.pe1mew.zangbundel.app.ui.textPanel
import nl.pe1mew.zangbundel.app.ui.theme.Palette
import nl.pe1mew.zangbundel.core.songformat.ReportEntry
import nl.pe1mew.zangbundel.core.songformat.ReportLevel
import nl.pe1mew.zangbundel.platform.storage.Changes
import nl.pe1mew.zangbundel.platform.storage.LibraryInstaller
import nl.pe1mew.zangbundel.platform.storage.LibraryRow
import java.text.DateFormat
import java.util.Date

/**
 * The *Bibliotheek* screen (guiDesign D-50): the installed libraries with their colour (D-51), and
 * *Bibliotheek toevoegen*, online or from a file, through the found release, the licence and the
 * import report; per library *Bijwerken* with what changed, *Aan* / *Uit* and *Verwijderen*.
 */
@Composable
fun LibraryScreen(model: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)) {
    val state by model.state.collectAsState()
    val step = state.step
    BackHandler(enabled = step != null && step !is LibraryViewModel.Step.Busy) { model.cancel() }
    ConfigurationScreen {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(stringResource(R.string.library_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            if (step == null) Libraries(state.libraries, onAdd = model::add, onAddDefault = model::addDefault, onUpdate = model::update,
                onEnabled = model::setEnabled, onRemove = model::askRemove)
            else Column(Modifier.fillMaxWidth().textPanel().padding(16.dp)) { AddStep(step, model, state.offerDefault) }
        }
    }
    state.confirmRemove?.let { library ->
        AlertDialog(
            onDismissRequest = { model.askRemove(null) },
            title = { Text(stringResource(R.string.library_remove_title, library.name)) },
            text = { Text(stringResource(R.string.library_remove_text)) },
            confirmButton = { TextButton(onClick = { model.remove(library) }) { Text(stringResource(R.string.library_remove)) } },
            dismissButton = { TextButton(onClick = { model.askRemove(null) }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ColumnScope.Libraries(
    libraries: List<LibraryRow>,
    onAdd: () -> Unit,
    onAddDefault: () -> Unit,
    onUpdate: (LibraryRow) -> Unit,
    onEnabled: (LibraryRow, Boolean) -> Unit,
    onRemove: (LibraryRow) -> Unit,
) {
    if (libraries.isEmpty()) {
        // FR-LIB-02: the default library is pre-configured and offered here; it is added only on this tap (FR-LIB-12).
        Column(Modifier.fillMaxWidth().textPanel().padding(16.dp)) {
            Text(stringResource(R.string.library_empty))
            Spacer(Modifier.height(16.dp))
            DefaultOffer(onAddDefault)
        }
        Spacer(Modifier.weight(1f))
    } else {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(libraries, key = { it.id }) { LibraryItem(it, onUpdate, onEnabled, onRemove) }
        }
    }
    Spacer(Modifier.height(16.dp))
    if (libraries.isEmpty()) OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.library_add)) }
    else Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.library_add)) }
}

/** The default library (FR-LIB-02), with where it comes from; the release, size and licence follow as for any library. */
@Composable
private fun DefaultOffer(onAddDefault: () -> Unit) {
    Text(stringResource(R.string.library_default), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.library_default_hint, DefaultLibrary.NAME), style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    Button(onClick = onAddDefault, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.library_add_named, DefaultLibrary.NAME)) }
}

@Composable
private fun LibraryItem(library: LibraryRow, onUpdate: (LibraryRow) -> Unit, onEnabled: (LibraryRow, Boolean) -> Unit, onRemove: (LibraryRow) -> Unit) {
    Column(Modifier.fillMaxWidth().textPanel().padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // A library that is off stays installed but leaves the songbook (D-50); its row is dimmed.
            Row(Modifier.weight(1f).alpha(if (library.enabled) 1f else 0.5f), verticalAlignment = Alignment.CenterVertically) {
                ColourSquare(library.colour)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(library.name, fontWeight = FontWeight.Bold)
                    Text(pluralStringResource(R.plurals.library_songs, library.songCount, library.songCount), style = MaterialTheme.typography.bodySmall)
                    Text(revisionLine(library), style = MaterialTheme.typography.bodySmall)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Switch(checked = library.enabled, onCheckedChange = { onEnabled(library, it) })
                Text(stringResource(if (library.enabled) R.string.library_on else R.string.library_off), style = MaterialTheme.typography.labelSmall)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onUpdate(library) }) { Text(stringResource(R.string.library_update)) }
            TextButton(onClick = { onRemove(library) }) { Text(stringResource(R.string.library_remove)) }
        }
    }
}

/** D-51: the colour is `colour % 8`; from the ninth library on, the square carries a dot. */
@Composable
private fun ColourSquare(colour: Int) {
    Box(Modifier.size(12.dp).background(Palette.Libraries[colour % Palette.Libraries.size], RoundedCornerShape(2.dp)), contentAlignment = Alignment.Center) {
        if (colour >= Palette.Libraries.size) Box(Modifier.size(4.dp).background(Palette.NightBlueDark, CircleShape))
    }
}

/** The installed version or revision and the archive's date (D-50, FR-LIB-17); a snapshot has neither. */
@Composable
private fun revisionLine(library: LibraryRow): String {
    val date = library.created?.take(10)
    return when {
        library.repository == null -> stringResource(R.string.library_snapshot, library.revision.removePrefix("snapshot-").take(8),
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(library.installedAt)))
        library.version != null -> stringResource(R.string.library_version, library.version!!, date ?: "")
        else -> stringResource(R.string.library_revision, library.revision.take(12), date ?: "")
    }
}

@Composable
private fun ColumnScope.AddStep(step: LibraryViewModel.Step, model: LibraryViewModel, offerDefault: Boolean) {
    when (step) {
        LibraryViewModel.Step.Choose -> Choose(model, offerDefault)
        LibraryViewModel.Step.Busy -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.library_busy))
        }
        is LibraryViewModel.Step.Found -> {
            val release = step.release
            Text(stringResource(R.string.library_found), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            step.installed?.let { Text(stringResource(R.string.library_installed_now, it.version ?: it.revision.take(12))) }
            release.version?.let { Text(stringResource(R.string.library_found_version, it)) }
            Text(release.size?.let { stringResource(R.string.library_found_size, Formatter.formatShortFileSize(LocalContext.current, it)) }
                ?: stringResource(R.string.library_found_size_unknown))
            Text(release.url, style = MaterialTheme.typography.bodySmall)
            Buttons(stringResource(R.string.library_download), { model.download(release) }, model::cancel)
        }
        is LibraryViewModel.Step.Failed -> {
            Text(stringResource(R.string.library_failed), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(failure(step.reason)))
            step.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Buttons(stringResource(R.string.library_try_again), model::add, model::cancel)
        }
        is LibraryViewModel.Step.UpToDate -> {
            Text(stringResource(R.string.library_up_to_date, step.library.name), style = MaterialTheme.typography.titleMedium)
            step.library.version?.let { Text(stringResource(R.string.library_found_version, it)) }
            Spacer(Modifier.height(16.dp))
            Button(onClick = model::cancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.done)) }
        }
        is LibraryViewModel.Step.Licence -> Licence(step.checked, model)
        is LibraryViewModel.Step.Report -> Report(step.checked, step.outcome, model::cancel)
    }
}

@Composable
private fun Choose(model: LibraryViewModel, offerDefault: Boolean) {
    var url by rememberSaveable { mutableStateOf("") }
    val resolver = LocalContext.current.contentResolver
    // A .zbl has no registered type, so the picker shows every file; the archive check decides (FR-LIB-16).
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) model.file(resolver, uri) }
    if (offerDefault) {
        DefaultOffer(model::addDefault)
        Spacer(Modifier.height(24.dp))
    }
    Text(stringResource(R.string.library_online), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.library_online_hint), style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.library_url)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    Button(onClick = { model.find(url.trim()) }, enabled = url.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.library_find)) }
    Spacer(Modifier.height(24.dp))
    Text(stringResource(R.string.library_from_file), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.library_from_file_hint), style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = { pick.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.library_open_file)) }
    Spacer(Modifier.height(16.dp))
    TextButton(onClick = model::cancel) { Text(stringResource(R.string.cancel)) }
}

/** FR-LIB-21: the summary and the full text, before anything is installed. */
@Composable
private fun ColumnScope.Licence(checked: LibraryRepository.Checked, model: LibraryViewModel) {
    val library = checked.library!!
    val songs = checked.result.library?.songs?.size ?: 0
    Text(library.name, style = MaterialTheme.typography.titleMedium)
    Text(pluralStringResource(R.plurals.library_songs, songs, songs), style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.library_licence, library.license.id), fontWeight = FontWeight.Bold)
    Text(library.license.summary)
    Spacer(Modifier.height(8.dp))
    Text(checked.licenseText.orEmpty(), style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()))
    Buttons(stringResource(R.string.library_accept), { model.accept(checked) }, model::cancel)
}

/** FR-LIB-07: imported, skipped by file and reason, and warnings; a refused archive shows why. */
@Composable
private fun ColumnScope.Report(checked: LibraryRepository.Checked, outcome: LibraryInstaller.Outcome?, onDone: () -> Unit) {
    val installed = outcome as? LibraryInstaller.Outcome.Installed
    val report = (outcome as? LibraryInstaller.Outcome.Refused)?.result?.report ?: checked.report
    val skipped = report.filter { it.level == ReportLevel.SONG_ERROR }
    val warnings = report.filter { it.level == ReportLevel.WARNING }
    val refusals = report.filter { it.level == ReportLevel.LIBRARY_ERROR }
    if (installed != null) {
        Text(stringResource(if (installed.previous == null) R.string.library_installed else R.string.library_updated, installed.library.name),
            style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.library_report_counts, installed.songs, skipped.map { it.path }.distinct().size, warnings.size))
    } else {
        Text(stringResource(R.string.library_refused), style = MaterialTheme.typography.titleMedium)
    }
    Spacer(Modifier.height(8.dp))
    LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        installed?.changes?.let { changes(it) }
        reportSection(R.string.library_report_refused, refusals)
        reportSection(R.string.library_report_skipped, skipped)
        reportSection(R.string.library_report_warnings, warnings)
    }
    Spacer(Modifier.height(16.dp))
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.done)) }
}

/** FR-LIB-11: what an update added, changed and removed, by title. */
private fun androidx.compose.foundation.lazy.LazyListScope.changes(changes: Changes) {
    item { Text(stringResource(R.string.library_changes), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
    if (changes.none) item { Text(stringResource(R.string.library_changes_none)) }
    for ((label, titles) in listOf(R.string.library_changes_added to changes.added, R.string.library_changes_changed to changes.changed,
        R.string.library_changes_removed to changes.removed)) {
        if (titles.isNotEmpty()) item { Text(stringResource(label) + ": " + titles.joinToString(", ")) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.reportSection(title: Int, entries: List<ReportEntry>) {
    if (entries.isEmpty()) return
    item { Text(stringResource(title), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
    items(entries) { e ->
        val where = listOfNotNull(e.path.ifEmpty { null }, e.line?.toString()).joinToString(":")
        Column {
            ReportText.of(e.code)?.let { Text(stringResource(it)) }
            Text(listOfNotNull(where.ifEmpty { null }, e.code, e.detail).joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                color = Palette.OffWhite)
        }
    }
}

@Composable
private fun Buttons(primary: String, onPrimary: () -> Unit, onCancel: () -> Unit) {
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        Spacer(Modifier.width(8.dp))
        Button(onClick = onPrimary) { Text(primary) }
    }
}

private fun failure(reason: LibraryRepository.Reason) = when (reason) {
    LibraryRepository.Reason.UNSUPPORTED_URL -> R.string.library_failed_url
    LibraryRepository.Reason.NETWORK -> R.string.library_failed_network
    LibraryRepository.Reason.NOT_AN_INDEX -> R.string.library_failed_index
    LibraryRepository.Reason.NO_RELEASE -> R.string.library_failed_no_release
    LibraryRepository.Reason.TOO_LARGE -> R.string.library_failed_too_large
}
