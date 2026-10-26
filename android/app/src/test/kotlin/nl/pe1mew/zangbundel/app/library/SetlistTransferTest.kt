// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.library

import kotlinx.coroutines.test.runTest
import nl.pe1mew.zangbundel.platform.storage.LibrarySetlist
import nl.pe1mew.zangbundel.platform.storage.PreparedEntry
import nl.pe1mew.zangbundel.platform.storage.PreparedSetlist
import nl.pe1mew.zangbundel.platform.storage.Setlists
import nl.pe1mew.zangbundel.platform.storage.Songbook
import nl.pe1mew.zangbundel.platform.storage.SongbookEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SetlistTransferTest {
    private class FakeSetlists : Setlists {
        val lists = LinkedHashMap<Long, PreparedSetlist>()
        val entries = HashMap<Long, List<PreparedEntry>>()
        private var next = 1L
        override suspend fun all() = lists.values.toList()
        override suspend fun setlist(uid: Long) = lists[uid]
        override suspend fun entries(uid: Long) = entries[uid].orEmpty()
        override suspend fun create(id: String, title: String, now: Long) = next++.also { lists[it] = PreparedSetlist(it, id, title, 0, now, null) }
        override suspend fun rename(uid: Long, title: String, now: Long) {}
        override suspend fun delete(uid: Long) {}
        override suspend fun replaceEntries(uid: Long, entries: List<PreparedEntry>, now: Long) { this.entries[uid] = entries }
    }

    private class FakeSongbook(val files: Map<String, ByteArray> = emptyMap()) : Songbook {
        private fun song(id: String, title: String) = SongbookEntry("bieb", id, title, null, "nl", emptyList(), "Bieb", 0, "d", "songs/$id.cho")
        override suspend fun entries() = listOf(song("advocaatje", "Advocaatje"), song("uil", "De uil zat in de olmen"))
        override suspend fun searchText(input: String) = emptySet<String>()
        override suspend fun librarySetlists() = emptyList<LibrarySetlist>()
        override suspend fun read(libraryId: String, directory: String, fileName: String) = files.getValue(fileName)
    }

    private class FakeDownloads(val served: Map<String, ByteArray>) : Downloads {
        override suspend fun fetch(url: String, maxBytes: Long, ifNoneMatch: String?) =
            served[url]?.let { Downloads.Fetched(200, null, it) } ?: Downloads.Fetched(404, null, ByteArray(0))
    }

    private val zbs = """
        {title: Zomerkamp}
        {meta: id zomerkamp}

        bieb/advocaatje  # Advocaatje
        andere/lied-x    # not installed
        ? de uil zat in de olmen
        ? Een lied dat niemand kent
    """.trimIndent().plus("\n").toByteArray()

    @Test
    fun `an import resolves what it can and keeps the rest`() = runTest {
        val store = FakeSetlists()
        val transfer = SetlistTransfer(FakeSongbook(), SetlistBook(store), FakeDownloads(emptyMap()))
        val done = assertIs<SetlistTransfer.Imported.Done>(transfer.import(zbs))
        assertEquals("zomerkamp", store.lists.getValue(done.uid).id, "the file's id is kept when it is free")
        assertEquals(
            listOf(
                PreparedEntry("bieb", "advocaatje", "Advocaatje"),
                PreparedEntry("andere", "lied-x", "andere/lied-x"),
                PreparedEntry("bieb", "uil", "De uil zat in de olmen"),
                PreparedEntry(null, null, "Een lied dat niemand kent"),
            ),
            store.entries.getValue(done.uid),
        )
        assertEquals(2, done.missing)
        // The same file again gets its own id.
        val again = assertIs<SetlistTransfer.Imported.Done>(transfer.import(zbs))
        assertEquals("zomerkamp-2", store.lists.getValue(again.uid).id)
    }

    @Test
    fun `a broken file is refused with its codes`() = runTest {
        val transfer = SetlistTransfer(FakeSongbook(), SetlistBook(FakeSetlists()), FakeDownloads(emptyMap()))
        val refused = assertIs<SetlistTransfer.Imported.Refused>(transfer.import("bieb/advocaatje\n".toByteArray()))
        assertTrue(refused.report.any { it.code == "SL002" })
        assertIs<SetlistTransfer.Imported.Refused>(transfer.import(byteArrayOf(0xC3.toByte(), 0x28)))
    }

    @Test
    fun `an export reads back as the same list`() = runTest {
        val store = FakeSetlists()
        val transfer = SetlistTransfer(FakeSongbook(), SetlistBook(store), FakeDownloads(emptyMap()))
        val first = assertIs<SetlistTransfer.Imported.Done>(transfer.import(zbs)).uid
        val (name, text) = transfer.export(first)!!
        assertEquals("zomerkamp.zbs", name)
        assertTrue("bieb/advocaatje  # Advocaatje" in text && "? Een lied dat niemand kent" in text, text)
        val second = assertIs<SetlistTransfer.Imported.Done>(transfer.import(text.toByteArray())).uid
        assertEquals(store.entries.getValue(first), store.entries.getValue(second))
    }

    @Test
    fun `only https addresses are fetched`() = runTest {
        val transfer = SetlistTransfer(FakeSongbook(), SetlistBook(FakeSetlists()), FakeDownloads(mapOf("https://example.org/a.zbs" to zbs)))
        assertIs<SetlistTransfer.Imported.Done>(transfer.importUrl("example.org/a.zbs"))
        assertEquals(LibraryRepository.Reason.UNSUPPORTED_URL, assertIs<SetlistTransfer.Imported.Failed>(transfer.importUrl("http://example.org/a.zbs")).reason)
        assertEquals(LibraryRepository.Reason.NETWORK, assertIs<SetlistTransfer.Imported.Failed>(transfer.importUrl("https://example.org/weg.zbs")).reason)
    }

    @Test
    fun `a library's setlist is copied as an editable list`() = runTest {
        val store = FakeSetlists()
        val shipped = LibrarySetlist("bieb", "avond", "setlists/avond.zbs", "Avond", 4, "Bieb", 0, "d")
        val transfer = SetlistTransfer(FakeSongbook(mapOf("setlists/avond.zbs" to zbs)), SetlistBook(store), FakeDownloads(emptyMap()))
        val uid = transfer.duplicate(shipped, "Avond (kopie)")!!
        assertEquals("avond-kopie", store.lists.getValue(uid).id)
        assertEquals(4, store.entries.getValue(uid).size)
    }
}
