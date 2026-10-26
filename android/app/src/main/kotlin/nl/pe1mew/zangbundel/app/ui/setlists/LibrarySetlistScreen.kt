// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.setlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.app.ZangbundelApp
import nl.pe1mew.zangbundel.app.library.SetlistTransfer
import nl.pe1mew.zangbundel.app.ui.ConfigurationScreen
import nl.pe1mew.zangbundel.app.ui.songbook.ColourSquare
import nl.pe1mew.zangbundel.app.ui.textPanel
import nl.pe1mew.zangbundel.app.ui.theme.Palette
import nl.pe1mew.zangbundel.platform.storage.LibrarySetlist
import nl.pe1mew.zangbundel.platform.storage.Songbook

class LibrarySetlistViewModel(
    private val songbook: Songbook,
    private val transfer: SetlistTransfer,
    private val libraryId: String,
    private val setlistId: String,
) : ViewModel() {
    data class State(val shipped: LibrarySetlist? = null, val lines: List<SetlistViewModel.Line> = emptyList(), val unreadable: Boolean = false)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    init {
        viewModelScope.launch {
            val shipped = songbook.librarySetlists().firstOrNull { it.libraryId == libraryId && it.setlistId == setlistId }
            val entries = shipped?.let { transfer.read(it) }
            var row = 0
            _state.value = if (shipped == null || entries == null) State(unreadable = true)
            else State(shipped, SetlistViewModel.lines(entries, songbook.entries()) { row++ })
        }
    }

    suspend fun duplicate(title: String): Long? = _state.value.shipped?.let { transfer.duplicate(it, title) }

    companion object {
        const val LIBRARY = "libraryId"
        const val SETLIST = "setlistId"

        val Factory = viewModelFactory {
            initializer {
                val container = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ZangbundelApp).container
                val args = createSavedStateHandle()
                LibrarySetlistViewModel(container.storage.songbook, container.setlistTransfer, args.get<String>(LIBRARY)!!, args.get<String>(SETLIST)!!)
            }
        }
    }
}

/** A setlist a library ships (guiDesign D-59): read-only, under its library's name and colour, with *Dupliceren* for an editable copy. */
@Composable
fun LibrarySetlistScreen(onDuplicated: (Long) -> Unit, model: LibrarySetlistViewModel = viewModel(factory = LibrarySetlistViewModel.Factory)) {
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    val shipped = state.shipped
    val copyOf = stringResource(R.string.setlists_copy_of, shipped?.title ?: "")
    ConfigurationScreen {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            if (state.unreadable) {
                Text(stringResource(R.string.song_unreadable), modifier = Modifier.fillMaxWidth().textPanel().padding(16.dp))
                return@Column
            }
            Text(shipped?.title.orEmpty(), style = MaterialTheme.typography.headlineMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                shipped?.let { ColourSquare(it.colour) }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.setlists_read_only, shipped?.libraryName.orEmpty()) + " · " +
                    pluralStringResource(R.plurals.library_songs, state.lines.size, state.lines.size), color = Palette.OffWhite)
            }
            Spacer(Modifier.height(16.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(state.lines, key = { it.rowId }) { LineRow(it, handle = null) }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { scope.launch { model.duplicate(copyOf)?.let(onDuplicated) } }, enabled = shipped != null, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.setlists_duplicate))
            }
        }
    }
}
