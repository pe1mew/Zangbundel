// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.songbook

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.app.ZangbundelApp
import nl.pe1mew.zangbundel.app.ui.LyricBody
import nl.pe1mew.zangbundel.app.ui.LyricColors
import nl.pe1mew.zangbundel.app.library.SetlistBook
import nl.pe1mew.zangbundel.app.ui.setlists.NameDialog
import nl.pe1mew.zangbundel.core.songformat.Song
import nl.pe1mew.zangbundel.core.songformat.SongParser
import nl.pe1mew.zangbundel.platform.storage.PreparedSetlist
import nl.pe1mew.zangbundel.platform.storage.Songbook
import nl.pe1mew.zangbundel.platform.storage.SongbookEntry

/** Opens one song of the songbook. The installed file was checked at import; it is parsed again because files are untrusted at rest too. */
class SongViewModel(private val songbook: Songbook, private val book: SetlistBook, private val libraryId: String, private val songId: String) : ViewModel() {
    /**
     * What D-53's button adds to: the setlist being edited, with whether the song is in it already, or a
     * setlist still to choose ([inChosen] names the one it was just added to).
     */
    sealed interface Target {
        data class Editing(val uid: Long, val title: String, val contains: Boolean) : Target
        data class Choose(val inChosen: String? = null) : Target
    }

    sealed interface State {
        data object Loading : State
        data class Shown(val entry: SongbookEntry, val song: Song, val target: Target) : State
        data object Unreadable : State
    }

    private val _choices = MutableStateFlow<List<PreparedSetlist>?>(null)
    /** The setlists offered by *Toevoegen aan setlist…*; null while the question is not open. */
    val choices: StateFlow<List<PreparedSetlist>?> = _choices

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state

    init {
        viewModelScope.launch {
            val entry = songbook.entries().firstOrNull { it.libraryId == libraryId && it.songId == songId }
            val song = entry?.let { e ->
                runCatching { songbook.read(e.libraryId, e.directory, e.fileName) }.getOrNull()
                    ?.let { bytes -> withContext(Dispatchers.Default) { SongParser.parse(bytes).song } }
            }
            _state.value = if (entry != null && song != null) State.Shown(entry, song, target(entry)) else State.Unreadable
        }
    }

    private suspend fun target(entry: SongbookEntry, chosen: String? = null): Target {
        val uid = book.editing.value ?: return Target.Choose(chosen)
        val setlist = book.setlist(uid) ?: return Target.Choose(chosen)
        return Target.Editing(uid, setlist.title, book.contains(uid, entry))
    }

    private fun shown() = _state.value as? State.Shown

    fun addToEditing() = viewModelScope.launch {
        val s = shown() ?: return@launch
        val t = s.target as? Target.Editing ?: return@launch
        book.add(t.uid, s.entry)
        _state.value = s.copy(target = target(s.entry))
    }

    fun choose() = viewModelScope.launch { _choices.value = book.all() }
    fun dismissChoice() { _choices.value = null }

    fun addTo(setlist: PreparedSetlist) = viewModelScope.launch {
        val s = shown() ?: return@launch
        book.add(setlist.uid, s.entry)
        _choices.value = null
        _state.value = s.copy(target = target(s.entry, setlist.title))
    }

    fun addToNew(title: String) = viewModelScope.launch {
        val s = shown() ?: return@launch
        val uid = book.create(title)
        book.add(uid, s.entry)
        _choices.value = null
        _state.value = s.copy(target = target(s.entry, book.setlist(uid)?.title))
    }

    companion object {
        const val LIBRARY = "libraryId"
        const val SONG = "songId"

        val Factory = viewModelFactory {
            initializer {
                val container = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ZangbundelApp).container
                val args = createSavedStateHandle()
                SongViewModel(container.storage.songbook, container.setlists, args.get<String>(LIBRARY)!!, args.get<String>(SONG)!!)
            }
        }
    }
}

/** The day theme of guiDesign D-15: outside a gathering the lyric view reads black on white. */
private val Background = Color.White
private val Lyric = Color.Black
private val Secondary = Color(0xFF444444)
private val Divider = Color(0xFFDDDDDD)

/**
 * Reading a song in preparation (guiDesign D-53): the lyric view's styling in the day theme, at the
 * default lyric size of D-18, lines wrapping with a hanging indent of 1 em (D-28); below, D-53's one
 * button that adds the song to a setlist. The song-info sheet of D-45 is not built yet.
 */
@Composable
fun SongScreen(model: SongViewModel = viewModel(factory = SongViewModel.Factory)) {
    val state by model.state.collectAsState()
    Column(Modifier.fillMaxSize().background(Background)) {
        // The system bars keep light icons (MainActivity); a black band under each keeps them readable on white.
        Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Color.Black))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp)) {
            when (val s = state) {
                SongViewModel.State.Loading -> Unit
                SongViewModel.State.Unreadable -> Text(stringResource(R.string.song_unreadable), color = Lyric)
                is SongViewModel.State.Shown -> Lyrics(s.entry, s.song)
            }
        }
        (state as? SongViewModel.State.Shown)?.let { AddButton(it.target, model) }
        Spacer(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars).background(Color.Black))
    }
}

/** D-53: one primary button, naming the setlist being edited, or asking which one. */
@Composable
private fun AddButton(target: SongViewModel.Target, model: SongViewModel) {
    val choices by model.choices.collectAsState()
    var naming by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(Background)) {
        HorizontalDivider(color = Divider)
        val (label, enabled) = when (target) {
            is SongViewModel.Target.Editing ->
                if (target.contains) stringResource(R.string.song_in_setlist) to false else stringResource(R.string.song_add_to, target.title) to true
            is SongViewModel.Target.Choose ->
                target.inChosen?.let { stringResource(R.string.song_in_named, it) to false } ?: (stringResource(R.string.song_add_to_choose) to true)
        }
        Button(
            onClick = { if (target is SongViewModel.Target.Editing) model.addToEditing() else model.choose() },
            enabled = enabled,
            // Disabled reads as a state here (D-53), so it keeps day-theme contrast instead of the dark theme's faded white.
            colors = ButtonDefaults.buttonColors(disabledContainerColor = Color(0xFFEEEEEE), disabledContentColor = Secondary),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
    choices?.let { list ->
        AlertDialog(
            onDismissRequest = model::dismissChoice,
            title = { Text(stringResource(R.string.song_choose_setlist)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    for (s in list) TextButton(onClick = { model.addTo(s) }, modifier = Modifier.fillMaxWidth()) { Text(s.title, modifier = Modifier.fillMaxWidth()) }
                    TextButton(onClick = { naming = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.setlists_new), modifier = Modifier.fillMaxWidth()) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = model::dismissChoice) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (naming) NameDialog(R.string.setlists_new, "", onDismiss = { naming = false }) { naming = false; model.addToNew(it) }
}

@Composable
private fun Lyrics(entry: SongbookEntry, song: Song) {
    Text(song.meta.title, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, color = Lyric)
    Text(listOf(entry.libraryName, entry.language).joinToString(" · "), fontSize = 14.sp, color = Secondary)
    Spacer(Modifier.height(20.dp))
    LyricBody(song, LyricColors.Day)
}
