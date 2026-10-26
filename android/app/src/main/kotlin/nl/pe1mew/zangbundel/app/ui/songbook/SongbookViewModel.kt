// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.songbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import nl.pe1mew.zangbundel.app.ZangbundelApp
import nl.pe1mew.zangbundel.app.library.SetlistBook
import nl.pe1mew.zangbundel.app.library.SongbookFilter
import nl.pe1mew.zangbundel.core.songformat.LibraryFileParser
import nl.pe1mew.zangbundel.platform.storage.InstallPlan
import nl.pe1mew.zangbundel.platform.storage.Songbook
import nl.pe1mew.zangbundel.platform.storage.SongbookEntry
import java.util.Locale

/** The *Zangbundel* screen (guiDesign D-52): the combined songbook of the enabled libraries, searched, filtered and sorted. */
class SongbookViewModel(private val songbook: Songbook, private val book: SetlistBook, private val appVersion: String) : ViewModel() {

    /** A library as its filter chip shows it: name and D-51 colour. */
    data class LibraryChip(val id: String, val name: String, val colour: Int)

    /** Editing mode (D-54): the setlist being filled, its song count and which songs it holds, as `libraryId/songId`. */
    data class Editing(val uid: Long, val title: String, val songs: Int, val keys: Set<String>)

    data class State(
        val loading: Boolean = true,
        val editing: Editing? = null,
        val entries: List<SongbookEntry> = emptyList(),
        val filter: SongbookFilter = SongbookFilter(),
        val textHits: Set<String>? = null,
        val rows: List<SongbookFilter.Row> = emptyList(),
        val libraries: List<LibraryChip> = emptyList(),
        val languages: List<String> = emptyList(),
        /** Tag → label in the app's language, from the libraries' own files (D-77). */
        val tags: Map<String, String> = emptyMap(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    private var search: Job? = null

    /** Called whenever the screen is shown, so a library added or switched off meanwhile is reflected. */
    fun load() = viewModelScope.launch {
        val entries = songbook.entries()
        val libraries = entries.distinctBy { it.libraryId }.map { LibraryChip(it.libraryId, it.libraryName, it.colour) }.sortedBy { it.name }
        val used = entries.flatMap { it.tags }.toSortedSet()
        val labels = HashMap<String, String>()
        for (e in entries.distinctBy { it.libraryId }) {
            val library = runCatching { LibraryFileParser.parse(songbook.read(e.libraryId, e.directory, InstallPlan.LIBRARY_FILE), appVersion).library }.getOrNull()
            library?.tags?.forEach { (tag, byLanguage) -> labels.putIfAbsent(tag, label(byLanguage) ?: tag) }
        }
        val s = _state.value.copy(editing = editing())
        val filter = s.filter.copy(libraries = s.filter.libraries.filterTo(HashSet()) { id -> libraries.any { it.id == id } })
        _state.value = s.copy(
            loading = false, entries = entries, filter = filter, libraries = libraries,
            languages = entries.map { it.language }.distinct().sorted(), tags = used.associateWith { labels[it] ?: it },
        ).recomputed()
        if (filter.inText) searchText(filter.query)
    }

    fun set(filter: SongbookFilter) {
        val before = _state.value.filter
        _state.value = _state.value.copy(filter = filter).recomputed()
        if (filter.inText && (filter.query != before.query || !before.inText)) searchText(filter.query)
    }

    fun clear() = set(SongbookFilter())

    /** The checkbox of editing mode: adds the song to the setlist being edited, or removes it. */
    fun toggle(entry: SongbookEntry) = viewModelScope.launch {
        val uid = _state.value.editing?.uid ?: return@launch
        book.toggle(uid, entry)
        _state.value = _state.value.copy(editing = editing())
    }

    private suspend fun editing(): Editing? {
        val uid = book.editing.value ?: return null
        val setlist = book.setlist(uid) ?: return null
        val keys = book.entries(uid).mapNotNullTo(HashSet()) { it.songKey }
        return Editing(uid, setlist.title, setlist.songs, keys)
    }

    /** Full-text search after a short pause in typing, so every keystroke does not hit the index. */
    private fun searchText(query: String) {
        search?.cancel()
        search = viewModelScope.launch {
            delay(250)
            val hits = songbook.searchText(query)
            _state.value = _state.value.copy(textHits = hits).recomputed()
        }
    }

    private fun State.recomputed() = copy(rows = filter.apply(entries, textHits))

    private fun label(byLanguage: Map<String, String>): String? =
        byLanguage[Locale.getDefault().language] ?: byLanguage["en"] ?: byLanguage.values.firstOrNull()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ZangbundelApp).container
                SongbookViewModel(container.storage.songbook, container.setlists, container.versionName)
            }
        }
    }
}
