// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.setlists

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
import nl.pe1mew.zangbundel.app.library.SetlistBook
import nl.pe1mew.zangbundel.app.ui.ConfigurationScreen
import nl.pe1mew.zangbundel.app.ui.songbook.ColourSquare
import nl.pe1mew.zangbundel.app.ui.textPanel
import nl.pe1mew.zangbundel.app.ui.theme.Palette
import nl.pe1mew.zangbundel.core.songformat.SetlistResolver
import nl.pe1mew.zangbundel.platform.storage.PreparedEntry
import nl.pe1mew.zangbundel.platform.storage.Songbook
import nl.pe1mew.zangbundel.platform.storage.SongbookEntry

class SetlistViewModel(private val book: SetlistBook, private val songbook: Songbook, val uid: Long) : ViewModel() {
    /** A line of the editor; [rowId] keeps a row's identity while it is dragged, also when a song occurs twice. */
    data class Line(val rowId: Int, val entry: PreparedEntry, val song: SongbookEntry?)

    data class State(val title: String = "", val lines: List<Line> = emptyList(), val loaded: Boolean = false)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    private var nextRow = 0

    /** Called whenever the editor is shown: it is also where the songbook's editing mode returns to, so editing ends here. */
    fun load() = viewModelScope.launch {
        book.stopEditing()
        val setlist = book.setlist(uid) ?: return@launch
        _state.value = State(setlist.title, lines(book.entries(uid), songbook.entries()) { nextRow++ }, loaded = true)
    }

    /** Moves a line while it is dragged; stored when the drag ends. */
    fun move(from: Int, to: Int) {
        val lines = _state.value.lines.toMutableList()
        if (from !in lines.indices || to !in lines.indices) return
        lines.add(to, lines.removeAt(from))
        _state.value = _state.value.copy(lines = lines)
    }

    fun store() = viewModelScope.launch { book.reorder(uid, _state.value.lines.map { it.entry }) }

    fun remove(line: Line) {
        _state.value = _state.value.copy(lines = _state.value.lines - line)
        store()
    }

    fun addSongs() = book.startEditing(uid)

    companion object {
        const val UID = "uid"

        /**
         * The lines as shown: a song entry with its installed song, if any; a title-only entry with the one
         * installed song of that title, if exactly one, so a list completes itself as libraries arrive (D-59).
         */
        fun lines(entries: List<PreparedEntry>, installed: List<SongbookEntry>, rowId: () -> Int): List<Line> {
            val byKey = installed.associateBy { it.libraryId + "/" + it.songId }
            val byTitle = installed.groupBy { SetlistResolver.fold(it.title) }
            return entries.map { e ->
                val song = e.songKey?.let(byKey::get) ?: if (e.songKey == null) byTitle[SetlistResolver.fold(e.title)]?.singleOrNull() else null
                Line(rowId(), e, song)
            }
        }

        val Factory = viewModelFactory {
            initializer {
                val container = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ZangbundelApp).container
                SetlistViewModel(container.setlists, container.storage.songbook, createSavedStateHandle().get<Long>(UID)!!)
            }
        }
    }
}

/**
 * One prepared setlist (guiDesign D-54): its songs in order with their colour squares; reordered by
 * dragging the handle, removed by a swipe; *Liederen toevoegen* opens the songbook in editing mode. A
 * song whose library is no longer installed stays in the list as *niet gevonden* (D-59).
 */
@Composable
fun SetlistScreen(onAddSongs: () -> Unit, model: SetlistViewModel = viewModel(factory = SetlistViewModel.Factory)) {
    val state by model.state.collectAsState()
    LaunchedEffect(Unit) { model.load() }
    ConfigurationScreen {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(state.title, style = MaterialTheme.typography.headlineMedium)
            Text(pluralStringResource(R.plurals.library_songs, state.lines.size, state.lines.size), color = Palette.OffWhite)
            Spacer(Modifier.height(16.dp))
            if (state.loaded && state.lines.isEmpty()) {
                Text(stringResource(R.string.setlist_empty), modifier = Modifier.fillMaxWidth().textPanel().padding(16.dp))
                Spacer(Modifier.weight(1f))
            } else {
                Lines(state.lines, model, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { model.addSongs(); onAddSongs() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.setlist_add_songs)) }
        }
    }
}

@Composable
private fun Lines(lines: List<SetlistViewModel.Line>, model: SetlistViewModel, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    var dragged by remember { mutableStateOf<Int?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    LazyColumn(modifier, state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        itemsIndexed(lines, key = { _, l -> l.rowId }) { _, line ->
            val isDragged = dragged == line.rowId
            val swipe = rememberSwipeToDismissBoxState()
            LaunchedEffect(swipe.currentValue) { if (swipe.currentValue != SwipeToDismissBoxValue.Settled) model.remove(line) }
            SwipeToDismissBox(
                state = swipe,
                backgroundContent = {},
                modifier = Modifier.zIndex(if (isDragged) 1f else 0f).graphicsLayer { translationY = if (isDragged) offset else 0f },
            ) {
                LineRow(line, handle = Modifier.pointerInput(line.rowId) {
                    detectDragGestures(
                        onDragStart = { dragged = line.rowId; offset = 0f },
                        onDragEnd = { dragged = null; offset = 0f; model.store() },
                        onDragCancel = { dragged = null; offset = 0f; model.store() },
                    ) { change, amount ->
                        change.consume()
                        offset += amount.y
                        // Swap with the neighbour once the dragged row's centre is over it.
                        val items = listState.layoutInfo.visibleItemsInfo
                        val current = items.firstOrNull { it.key == line.rowId } ?: return@detectDragGestures
                        val centre = current.offset + offset + current.size / 2f
                        val target = items.firstOrNull { it.key != line.rowId && centre.toInt() in it.offset until it.offset + it.size }
                        if (target != null) {
                            offset += current.offset - target.offset
                            model.move(current.index, target.index)
                        }
                    }
                })
            }
        }
    }
}

/** [handle] carries the drag gesture for the handle only, not the row, hence not the usual `modifier`; null for a read-only list. */
@Suppress("ModifierParameter")
@Composable
internal fun LineRow(line: SetlistViewModel.Line, handle: Modifier?) {
    val notFound = stringResource(R.string.setlist_not_found)
    val move = stringResource(R.string.setlist_move)
    Row(Modifier.fillMaxWidth().textPanel().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (handle != null) {
            Box(handle.size(48.dp).semantics { contentDescription = move }, contentAlignment = Alignment.Center) {
                Text("≡", style = MaterialTheme.typography.titleLarge, color = Palette.OffWhite)
            }
        } else {
            Spacer(Modifier.width(16.dp))
        }
        val song = line.song
        if (song != null) ColourSquare(song.colour) else Box(Modifier.size(12.dp).background(Palette.NightBlue, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(song?.title ?: line.entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(song?.let { it.libraryName + " · " + it.language } ?: notFound, style = MaterialTheme.typography.bodySmall, color = Palette.OffWhite)
        }
    }
}
