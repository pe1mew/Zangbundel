// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.songbook

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
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
import nl.pe1mew.zangbundel.app.library.SetlistBook
import nl.pe1mew.zangbundel.app.ui.KeepTopLine
import nl.pe1mew.zangbundel.app.ui.LineKeeper
import nl.pe1mew.zangbundel.app.ui.LyricBody
import nl.pe1mew.zangbundel.app.ui.LyricColors
import nl.pe1mew.zangbundel.app.ui.gathering.SongInfoSheet
import nl.pe1mew.zangbundel.app.ui.setlists.NameDialog
import nl.pe1mew.zangbundel.core.songformat.Song
import nl.pe1mew.zangbundel.core.songformat.SongParser
import nl.pe1mew.zangbundel.platform.storage.PreparedSetlist
import nl.pe1mew.zangbundel.platform.storage.Songbook
import nl.pe1mew.zangbundel.platform.storage.SongbookEntry

/**
 * Opens songs of the songbook: the one tapped, and, when it was opened from the songbook, the others of the
 * songbook as it was shown, to swipe to (guiDesign D-85). Each installed file was checked at import; it is
 * parsed again when its page is first shown, because files are untrusted at rest too.
 */
class SongViewModel(
    private val songbook: Songbook,
    private val book: SetlistBook,
    /** The songs a swipe walks, as (library id, song id), in the songbook's order; one song when not opened from it. */
    val order: List<Pair<String, String>>,
    /** Where the tapped song stands in [order]. */
    val start: Int,
) : ViewModel() {
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
        data class Shown(val entry: SongbookEntry, val song: Song) : State
        data object Unreadable : State
    }

    private val _choices = MutableStateFlow<List<PreparedSetlist>?>(null)
    /** The setlists offered by *Toevoegen aan setlist…*; null while the question is not open. */
    val choices: StateFlow<List<PreparedSetlist>?> = _choices

    private val _pages = MutableStateFlow<Map<Int, State>>(emptyMap())
    /** Each page's song by its place in [order]; a page not yet asked for is absent. */
    val pages: StateFlow<Map<Int, State>> = _pages

    private val _target = MutableStateFlow<Target?>(null)
    /** D-53's button, for the song on screen; null while it is still loading or cannot be read. */
    val target: StateFlow<Target?> = _target

    private var current = start
    private var entries: List<SongbookEntry>? = null

    /** Reads page [index] once; the pager asks for the page on screen and its neighbours. */
    fun load(index: Int) {
        if (index !in order.indices || index in _pages.value) return
        _pages.value += index to State.Loading
        viewModelScope.launch {
            val (libraryId, songId) = order[index]
            val all = entries ?: songbook.entries().also { entries = it }
            val entry = all.firstOrNull { it.libraryId == libraryId && it.songId == songId }
            val song = entry?.let { e ->
                runCatching { songbook.read(e.libraryId, e.directory, e.fileName) }.getOrNull()
                    ?.let { bytes -> withContext(Dispatchers.Default) { SongParser.parse(bytes).song } }
            }
            _pages.value += index to (if (entry != null && song != null) State.Shown(entry, song) else State.Unreadable)
            if (index == current) refreshTarget()
        }
    }

    /** The page on screen changed (a swipe). */
    fun select(index: Int) {
        current = index
        _target.value = null
        load(index)
        viewModelScope.launch { refreshTarget() }
    }

    private fun shown() = _pages.value[current] as? State.Shown

    private suspend fun refreshTarget(chosen: String? = null) {
        val entry = shown()?.entry ?: return
        val shownAt = current
        val t = target(entry, chosen)
        if (current == shownAt) _target.value = t
    }

    private suspend fun target(entry: SongbookEntry, chosen: String? = null): Target {
        val uid = book.editing.value ?: return Target.Choose(chosen)
        val setlist = book.setlist(uid) ?: return Target.Choose(chosen)
        return Target.Editing(uid, setlist.title, book.contains(uid, entry))
    }

    fun addToEditing() = viewModelScope.launch {
        val s = shown() ?: return@launch
        val t = _target.value as? Target.Editing ?: return@launch
        book.add(t.uid, s.entry)
        refreshTarget()
    }

    fun choose() = viewModelScope.launch { _choices.value = book.all() }
    fun dismissChoice() { _choices.value = null }

    fun addTo(setlist: PreparedSetlist) = viewModelScope.launch {
        val s = shown() ?: return@launch
        book.add(setlist.uid, s.entry)
        _choices.value = null
        refreshTarget(setlist.title)
    }

    fun addToNew(title: String) = viewModelScope.launch {
        val s = shown() ?: return@launch
        val uid = book.create(title)
        book.add(uid, s.entry)
        _choices.value = null
        refreshTarget(book.setlist(uid)?.title)
    }

    companion object {
        const val LIBRARY = "libraryId"
        const val SONG = "songId"
        /** Opened from the songbook: swipe through its songs as shown (D-85). */
        const val LIST = "list"

        val Factory = viewModelFactory {
            initializer {
                val container = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ZangbundelApp).container
                val args = createSavedStateHandle()
                val tapped = args.get<String>(LIBRARY)!! to args.get<String>(SONG)!!
                // The songbook's list when opened from it and it still holds the song; otherwise the one song.
                val list = container.readingOrder.takeIf { args.get<Boolean>(LIST) == true && tapped in it } ?: listOf(tapped)
                SongViewModel(container.storage.songbook, container.setlists, list, list.indexOf(tapped))
            }
        }
    }
}


/**
 * Reading a song in preparation (guiDesign D-53): the lyric view's styling in the theme the singer chose in
 * the toolbar (D-17; night unless changed, D-53 as amended in v0.48), at the default lyric size of D-18, lines wrapping with a hanging indent of 1 em (D-28); below, D-53's one
 * button that adds the song to a setlist. The song's metadata and licence (D-45, FR-LIB-20) open from the ⓘ
 * beside the title or a long press on the title (D-33). A rotation keeps the line at the top (D-42, FR-DISP-04),
 * as on the lyric pages of a campfire. Opened from the songbook, a swipe to the left shows the next song of the
 * songbook as it was shown, a swipe to the right the previous one; the first and the last song stop there (D-85).
 */
@Composable
fun SongScreen(model: SongViewModel = viewModel(factory = SongViewModel.Factory)) {
    val pages by model.pages.collectAsState()
    val target by model.target.collectAsState()
    val settings = (LocalContext.current.applicationContext as ZangbundelApp).container.storage.settings
    val night by settings.lyricNight.collectAsState(initial = true)
    val colors = if (night) LyricColors.Night else LyricColors.Day
    val pager = rememberPagerState(initialPage = model.start) { model.order.size }
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { model.select(it) } }
    Column(Modifier.fillMaxSize().background(colors.background)) {
        // The system bars keep light icons (MainActivity); a black band under each keeps them readable on white.
        Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Color.Black))
        // D-85: one page per song; the neighbours are read ahead so a swipe shows text at once.
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1, key = { model.order[it].toString() }) { page ->
            LaunchedEffect(page) { model.load(page) }
            SongPage(pages[page], colors)
        }
        target?.let { AddButton(it, model, colors) }
        Spacer(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars).background(Color.Black))
    }
}

/** One song: its heading and lyrics, scrolling on its own, its top line kept through a rotation (D-42). */
@Composable
private fun SongPage(state: SongViewModel.State?, colors: LyricColors) {
    // D-42: item 0 is the title block, item 1 the song, whose lines the keeper follows through a rotation.
    val list = rememberLazyListState()
    val lines = remember { LineKeeper() }
    KeepTopLine(list, lines, keyAt = { i -> if (i == SONG_ITEM) SONG_KEY else null }, indexOf = { k -> if (k == SONG_KEY) SONG_ITEM else -1 })
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp)) {
        when (state) {
            null, SongViewModel.State.Loading -> Unit
            SongViewModel.State.Unreadable -> item { Text(stringResource(R.string.song_unreadable), color = colors.lyric) }
            is SongViewModel.State.Shown -> {
                item { Heading(state.entry, state.song, colors) }
                item {
                    LyricBody(state.song, colors, Modifier.onGloballyPositioned { lines.item(SONG_KEY, it) }) { code, c -> lines.line(SONG_KEY, code, c) }
                }
            }
        }
    }
}

/** D-53: one primary button, naming the setlist being edited, or asking which one. */
@Composable
private fun AddButton(target: SongViewModel.Target, model: SongViewModel, colors: LyricColors) {
    val choices by model.choices.collectAsState()
    var naming by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(colors.background)) {
        HorizontalDivider(color = colors.divider)
        val (label, enabled) = when (target) {
            is SongViewModel.Target.Editing ->
                if (target.contains) stringResource(R.string.song_in_setlist) to false else stringResource(R.string.song_add_to, target.title) to true
            is SongViewModel.Target.Choose ->
                target.inChosen?.let { stringResource(R.string.song_in_named, it) to false } ?: (stringResource(R.string.song_add_to_choose) to true)
        }
        Button(
            onClick = { if (target is SongViewModel.Target.Editing) model.addToEditing() else model.choose() },
            enabled = enabled,
            // Disabled reads as a state here (D-53), so it keeps the theme's contrast instead of a faded button.
            colors = ButtonDefaults.buttonColors(disabledContainerColor = if (colors == LyricColors.Night) Color(0xFF2A2A2A) else Color(0xFFEEEEEE), disabledContentColor = colors.secondary),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Heading(entry: SongbookEntry, song: Song, colors: LyricColors) {
    var info by rememberSaveable { mutableStateOf(false) }
    val infoLabel = stringResource(R.string.toolbar_info)
    Row(verticalAlignment = Alignment.Top) {
        Text(song.meta.title, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, color = colors.lyric,
            modifier = Modifier.weight(1f).combinedClickable(onClick = {}, onLongClick = { info = true }, onLongClickLabel = infoLabel))
        Box(Modifier.size(48.dp).clickable(onClickLabel = infoLabel) { info = true }, contentAlignment = Alignment.Center) {
            Text("ⓘ", fontSize = 24.sp, color = colors.secondary)
        }
    }
    Text(listOf(entry.libraryName, entry.language).joinToString(" · "), fontSize = 14.sp, color = colors.secondary)
    if (info) SongInfoSheet(song.meta, entry.libraryName, onDismiss = { info = false })
    Spacer(Modifier.height(20.dp))
}

private const val SONG_ITEM = 1
private const val SONG_KEY = "song"
