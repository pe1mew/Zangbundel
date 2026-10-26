// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.library

import kotlinx.coroutines.test.runTest
import nl.pe1mew.zangbundel.platform.storage.PreparedEntry
import nl.pe1mew.zangbundel.platform.storage.PreparedSetlist
import nl.pe1mew.zangbundel.platform.storage.Setlists
import nl.pe1mew.zangbundel.platform.storage.SongbookEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SetlistBookTest {
    private class FakeSetlists : Setlists {
        val lists = LinkedHashMap<Long, PreparedSetlist>()
        val entries = HashMap<Long, List<PreparedEntry>>()
        private var next = 1L
        override suspend fun all() = lists.values.map { it.copy(songs = entries[it.uid].orEmpty().size) }
        override suspend fun setlist(uid: Long) = all().firstOrNull { it.uid == uid }
        override suspend fun entries(uid: Long) = entries[uid].orEmpty()
        override suspend fun create(id: String, title: String, now: Long) = next++.also { lists[it] = PreparedSetlist(it, id, title, 0, now, null) }
        override suspend fun rename(uid: Long, title: String, now: Long) { lists[uid] = lists.getValue(uid).copy(title = title, changedAt = now) }
        override suspend fun delete(uid: Long) { lists.remove(uid); entries.remove(uid) }
        override suspend fun replaceEntries(uid: Long, entries: List<PreparedEntry>, now: Long) { this.entries[uid] = entries }
    }

    private fun song(id: String) = SongbookEntry("bieb", id, id.replaceFirstChar { it.uppercase() }, null, "nl", emptyList(), "Bieb", 0, "d", "songs/$id.cho")

    @Test
    fun `create gives each setlist its own id and a clean title`() = runTest {
        val book = SetlistBook(FakeSetlists(), clock = { 7 })
        val a = book.create("  Zomerkamp   2026 ")
        val b = book.create("Zomerkamp 2026")
        assertEquals(listOf("zomerkamp-2026", "zomerkamp-2026-2"), book.all().map { it.id })
        assertEquals("Zomerkamp 2026", book.setlist(a)!!.title)
        assertEquals(SetlistBook.TITLE_MAX, book.setlist(book.create("x".repeat(100)))!!.title.length)
        assertTrue(a != b)
    }

    @Test
    fun `the checkbox adds at the end and removes again`() = runTest {
        val book = SetlistBook(FakeSetlists())
        val uid = book.create("Avond")
        assertTrue(book.toggle(uid, song("advocaatje")))
        assertTrue(book.toggle(uid, song("uil")))
        assertEquals(listOf("bieb/advocaatje", "bieb/uil"), book.entries(uid).map { it.songKey })
        assertFalse(book.toggle(uid, song("advocaatje")))
        assertEquals(listOf("bieb/uil"), book.entries(uid).map { it.songKey })
        book.add(uid, song("uil"))
        assertEquals(1, book.entries(uid).size, "add never doubles a song")
        assertTrue(book.contains(uid, song("uil")))
    }

    @Test
    fun `a duplicate copies the songs under its own id, and deleting ends editing`() = runTest {
        val book = SetlistBook(FakeSetlists())
        val uid = book.create("Avond")
        book.add(uid, song("uil"))
        val copy = book.duplicate(uid, "Avond (kopie)")
        assertEquals(book.entries(uid), book.entries(copy))
        assertEquals("avond-kopie", book.setlist(copy)!!.id)
        book.startEditing(copy)
        book.delete(copy)
        assertNull(book.editing.value)
        assertEquals(listOf(uid), book.all().map { it.uid })
    }
}
