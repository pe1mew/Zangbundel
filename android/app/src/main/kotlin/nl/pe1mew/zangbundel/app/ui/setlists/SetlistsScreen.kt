// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.setlists

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.app.ZangbundelApp
import nl.pe1mew.zangbundel.app.library.LibraryRepository
import nl.pe1mew.zangbundel.app.library.SetlistBook
import nl.pe1mew.zangbundel.app.library.SetlistTransfer
import nl.pe1mew.zangbundel.app.library.readPicked
import nl.pe1mew.zangbundel.app.ui.ConfigurationScreen
import nl.pe1mew.zangbundel.app.ui.library.ReportText
import nl.pe1mew.zangbundel.app.ui.songbook.ColourSquare
import nl.pe1mew.zangbundel.app.ui.textPanel
import nl.pe1mew.zangbundel.app.ui.theme.Palette
import nl.pe1mew.zangbundel.core.songformat.Limits
import nl.pe1mew.zangbundel.platform.storage.LibrarySetlist
import nl.pe1mew.zangbundel.platform.storage.PreparedSetlist
import nl.pe1mew.zangbundel.platform.storage.Songbook

class SetlistsViewModel(private val book: SetlistBook, private val transfer: SetlistTransfer, private val songbook: Songbook) : ViewModel() {
    /** *Importeren* (D-59): choosing a file or address, busy, or what came of it. */
    sealed interface Import {
        data object Choose : Import
        data object Busy : Import
        data class Result(val result: SetlistTransfer.Imported) : Import
    }

    data class State(
        val setlists: List<PreparedSetlist>? = null,
        val shipped: List<LibrarySetlist> = emptyList(),
        val import: Import? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun load() = viewModelScope.launch { _state.value = _state.value.copy(setlists = book.all(), shipped = songbook.librarySetlists()) }

    suspend fun create(title: String): Long = book.create(title).also { load() }
    fun rename(uid: Long, title: String) = viewModelScope.launch { book.rename(uid, title); load() }
    fun duplicate(uid: Long, title: String) = viewModelScope.launch { book.duplicate(uid, title); load() }
    fun delete(uid: Long) = viewModelScope.launch { book.delete(uid); load() }

    /** *Dupliceren* on a library's setlist; null when its file could not be read. */
    suspend fun duplicate(shipped: LibrarySetlist, title: String): Long? = transfer.duplicate(shipped, title).also { load() }

    suspend fun export(uid: Long): Pair<String, String>? = transfer.export(uid)

    fun startImport() { _state.value = _state.value.copy(import = Import.Choose) }
    fun endImport() { _state.value = _state.value.copy(import = null) }

    fun importFile(resolver: ContentResolver, uri: Uri) = importing {
        val (_, bytes) = readPicked(resolver, uri, Limits.SONG_FILE_MAX_BYTES)
        transfer.import(bytes)
    }

    fun importUrl(url: String) = importing { transfer.importUrl(url) }

    private fun importing(step: suspend () -> SetlistTransfer.Imported) = viewModelScope.launch {
        _state.value = _state.value.copy(import = Import.Busy)
        val result = step()
        _state.value = _state.value.copy(import = Import.Result(result))
        load()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ZangbundelApp).container
                SetlistsViewModel(container.setlists, container.setlistTransfer, container.storage.songbook)
            }
        }
    }
}

/**
 * The *Setlists* screen (guiDesign D-54, D-59; FR-LEAD-02): the prepared setlists with name, number of
 * songs and last use; *Nieuwe setlist* and *Importeren* (a `.zbs` file or its HTTPS address); per row
 * *Naam wijzigen*, *Dupliceren*, *Exporteren* (through the share sheet) and *Verwijderen* with a
 * confirmation. Below them the setlists the enabled libraries ship, read-only, with *Dupliceren*.
 */
@Composable
fun SetlistsScreen(onOpen: (Long) -> Unit, onOpenShipped: (LibrarySetlist) -> Unit, model: SetlistsViewModel = viewModel(factory = SetlistsViewModel.Factory)) {
    val state by model.state.collectAsState()
    LaunchedEffect(Unit) { model.load() }
    val scope = rememberCoroutineScope()
    val share = rememberShare()
    var creating by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<PreparedSetlist?>(null) }
    var deleting by remember { mutableStateOf<PreparedSetlist?>(null) }
    val copyOf = stringResource(R.string.setlists_copy_of, "%s")
    ConfigurationScreen {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(stringResource(R.string.setlists_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            val list = state.setlists
            if (list != null && list.isEmpty() && state.shipped.isEmpty()) {
                Text(stringResource(R.string.setlists_empty), modifier = Modifier.fillMaxWidth().textPanel().padding(16.dp))
                Spacer(Modifier.weight(1f))
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(list.orEmpty(), key = { "p:" + it.uid }) { s ->
                        SetlistRow(s, onOpen = { onOpen(s.uid) }, onRename = { renaming = s }, onDelete = { deleting = s },
                            onDuplicate = { model.duplicate(s.uid, copyOf.format(s.title)) },
                            onExport = { scope.launch { model.export(s.uid)?.let { (name, text) -> share(name, text) } } })
                    }
                    if (state.shipped.isNotEmpty()) item(key = "shipped") {
                        Text(stringResource(R.string.setlists_from_libraries), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    }
                    items(state.shipped, key = { "l:" + it.libraryId + "/" + it.setlistId }) { s ->
                        ShippedRow(s, onOpen = { onOpenShipped(s) }) {
                            scope.launch { model.duplicate(s, copyOf.format(s.title))?.let(onOpen) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = model::startImport, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.setlists_import)) }
                Button(onClick = { creating = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.setlists_new)) }
            }
        }
    }
    state.import?.let { ImportDialog(it, model, onOpen) }
    if (creating) NameDialog(R.string.setlists_new, "", onDismiss = { creating = false }) { title ->
        creating = false
        scope.launch { onOpen(model.create(title)) }
    }
    renaming?.let { s -> NameDialog(R.string.setlists_rename, s.title, onDismiss = { renaming = null }) { model.rename(s.uid, it); renaming = null } }
    deleting?.let { s ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.setlists_delete_title, s.title)) },
            text = { Text(stringResource(R.string.setlists_delete_text)) },
            confirmButton = { TextButton(onClick = { model.delete(s.uid); deleting = null }) { Text(stringResource(R.string.setlists_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SetlistRow(s: PreparedSetlist, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit, onDuplicate: () -> Unit, onExport: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().textPanel().clickable(onClick = onOpen).padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(s.title, style = MaterialTheme.typography.titleMedium)
            val used = stringResource(R.string.setlists_never_used) // last use arrives with gatherings (D-55)
            Text(pluralStringResource(R.plurals.library_songs, s.songs, s.songs) + " · " + used, style = MaterialTheme.typography.bodySmall, color = Palette.OffWhite)
        }
        Box {
            TextButton(onClick = { menu = true }) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.setlists_rename)) }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text(stringResource(R.string.setlists_duplicate)) }, onClick = { menu = false; onDuplicate() })
                DropdownMenuItem(text = { Text(stringResource(R.string.setlists_export)) }, onClick = { menu = false; onExport() })
                DropdownMenuItem(text = { Text(stringResource(R.string.setlists_delete)) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

/** A setlist a library ships (D-59): its library's name and colour, read-only, with *Dupliceren*. */
@Composable
private fun ShippedRow(s: LibrarySetlist, onOpen: () -> Unit, onDuplicate: () -> Unit) {
    Row(Modifier.fillMaxWidth().textPanel().clickable(onClick = onOpen).padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        ColourSquare(s.colour)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.title, style = MaterialTheme.typography.titleMedium)
            Text(s.libraryName + " · " + pluralStringResource(R.plurals.library_songs, s.entries, s.entries), style = MaterialTheme.typography.bodySmall, color = Palette.OffWhite)
        }
        TextButton(onClick = onDuplicate) { Text(stringResource(R.string.setlists_duplicate)) }
    }
}

/** *Importeren* (D-59): a file from the system picker or an HTTPS address; a refusal names its reasons in words. */
@Composable
private fun ImportDialog(step: SetlistsViewModel.Import, model: SetlistsViewModel, onOpen: (Long) -> Unit) {
    val resolver = LocalContext.current.contentResolver
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { model.importFile(resolver, it) } }
    var url by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (step !is SetlistsViewModel.Import.Busy) model.endImport() },
        title = { Text(stringResource(R.string.setlists_import_title)) },
        text = {
            Column {
                when (step) {
                    SetlistsViewModel.Import.Choose -> {
                        Text(stringResource(R.string.setlists_import_hint))
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = { pick.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.library_open_file)) }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(url, { url = it }, singleLine = true, label = { Text(stringResource(R.string.library_url)) }, modifier = Modifier.fillMaxWidth())
                    }
                    SetlistsViewModel.Import.Busy -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.library_busy))
                    }
                    is SetlistsViewModel.Import.Result -> when (val r = step.result) {
                        is SetlistTransfer.Imported.Done -> {
                            Text(stringResource(R.string.setlists_imported, r.title))
                            if (r.missing > 0) Text(pluralStringResource(R.plurals.setlists_missing, r.missing, r.missing))
                            r.warnings.forEach { w -> ReportText.of(w.code)?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) } }
                        }
                        is SetlistTransfer.Imported.Refused -> {
                            Text(stringResource(R.string.setlists_import_refused))
                            r.report.filter { it.code.startsWith("SL") && !it.code.startsWith("SL1") }.forEach { e ->
                                Text((ReportText.of(e.code)?.let { stringResource(it) } ?: e.code) + (e.line?.let { " ($it)" } ?: ""), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        is SetlistTransfer.Imported.Failed -> Text(stringResource(
                            if (r.reason == LibraryRepository.Reason.UNSUPPORTED_URL) R.string.setlists_import_https else R.string.library_failed_network,
                        ))
                    }
                }
            }
        },
        confirmButton = {
            when (step) {
                SetlistsViewModel.Import.Choose -> TextButton(onClick = { model.importUrl(url) }, enabled = url.isNotBlank()) { Text(stringResource(R.string.setlists_import)) }
                SetlistsViewModel.Import.Busy -> Unit
                is SetlistsViewModel.Import.Result -> {
                    val done = step.result as? SetlistTransfer.Imported.Done
                    TextButton(onClick = { model.endImport(); done?.let { onOpen(it.uid) } }) {
                        Text(stringResource(if (done != null) R.string.setlists_open else R.string.done))
                    }
                }
            }
        },
        dismissButton = {
            if (step is SetlistsViewModel.Import.Choose) TextButton(onClick = model::endImport) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Asks for a setlist's name, at most [SetlistBook.TITLE_MAX] characters. */
@Composable
internal fun NameDialog(title: Int, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            OutlinedTextField(name, { name = it.take(SetlistBook.TITLE_MAX) }, singleLine = true, label = { Text(stringResource(R.string.setlists_name)) })
        },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.setlists_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
