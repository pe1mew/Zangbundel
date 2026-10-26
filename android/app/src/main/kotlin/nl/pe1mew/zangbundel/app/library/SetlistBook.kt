// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.library

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import nl.pe1mew.zangbundel.core.model.isValidId
import nl.pe1mew.zangbundel.platform.storage.PreparedEntry
import nl.pe1mew.zangbundel.platform.storage.PreparedSetlist
import nl.pe1mew.zangbundel.platform.storage.SetlistIds
import nl.pe1mew.zangbundel.platform.storage.Setlists
import nl.pe1mew.zangbundel.platform.storage.SongbookEntry

/**
 * Prepared setlists as the screens use them (guiDesign D-53, D-54; FR-LEAD-02): creating, renaming,
 * duplicating and deleting; adding and removing songs; and which setlist is being edited, so the
 * songbook's editing mode and the reading view's button target the same one.
 */
class SetlistBook(private val store: Setlists, private val clock: () -> Long = System::currentTimeMillis) {
    /** Titles are kept to the length song-format 13.6 warns beyond (SL104). */
    companion object { const val TITLE_MAX = 60 }

    private val _editing = MutableStateFlow<Long?>(null)
    /** The setlist whose *Liederen toevoegen* opened the songbook; null outside editing mode. */
    val editing: StateFlow<Long?> = _editing

    fun startEditing(uid: Long) { _editing.value = uid }
    fun stopEditing() { _editing.value = null }

    suspend fun all(): List<PreparedSetlist> = store.all()
    suspend fun setlist(uid: Long): PreparedSetlist? = store.setlist(uid)
    suspend fun entries(uid: Long): List<PreparedEntry> = store.entries(uid)

    suspend fun create(title: String): Long {
        val t = clean(title)
        return store.create(SetlistIds.of(t, store.all().mapTo(HashSet()) { it.id }), t, clock())
    }

    /**
     * A setlist with its songs at once, for an import or a copy of a library's setlist (D-59). It keeps
     * [id] when that is free on this phone, so an exported list comes back under the same id.
     */
    suspend fun createWith(id: String?, title: String, entries: List<PreparedEntry>): Long {
        val t = clean(title)
        val taken = store.all().mapTo(HashSet()) { it.id }
        val uid = store.create(id?.takeIf { isValidId(it) && it !in taken } ?: SetlistIds.of(t, taken), t, clock())
        store.replaceEntries(uid, entries, clock())
        return uid
    }

    suspend fun rename(uid: Long, title: String) = store.rename(uid, clean(title), clock())

    /** A copy under [title] with the same songs; the copy gets its own id. */
    suspend fun duplicate(uid: Long, title: String): Long {
        val copy = create(title)
        store.replaceEntries(copy, store.entries(uid), clock())
        return copy
    }

    suspend fun delete(uid: Long) {
        if (_editing.value == uid) stopEditing()
        store.delete(uid)
    }

    suspend fun reorder(uid: Long, entries: List<PreparedEntry>) = store.replaceEntries(uid, entries, clock())

    suspend fun contains(uid: Long, song: SongbookEntry): Boolean = store.entries(uid).any { it.songKey == key(song) }

    /** Adds the song at the end, unless it is in the setlist already. */
    suspend fun add(uid: Long, song: SongbookEntry) {
        val entries = store.entries(uid)
        if (entries.none { it.songKey == key(song) }) store.replaceEntries(uid, entries + PreparedEntry(song.libraryId, song.songId, song.title), clock())
    }

    /** The checkbox of the songbook's editing mode (D-54): adds the song, or removes every entry of it. Returns whether it is in now. */
    suspend fun toggle(uid: Long, song: SongbookEntry): Boolean {
        val entries = store.entries(uid)
        return if (entries.any { it.songKey == key(song) }) {
            store.replaceEntries(uid, entries.filterNot { it.songKey == key(song) }, clock())
            false
        } else {
            store.replaceEntries(uid, entries + PreparedEntry(song.libraryId, song.songId, song.title), clock())
            true
        }
    }

    private fun key(song: SongbookEntry) = song.libraryId + "/" + song.songId
    private fun clean(title: String) = title.trim().replace(Regex("\\s+"), " ").take(TITLE_MAX).ifEmpty { "Setlist" }
}
