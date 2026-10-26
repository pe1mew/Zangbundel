// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.library

import nl.pe1mew.zangbundel.platform.storage.SongbookEntry
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SongbookFilterTest {
    private val nl = Locale.forLanguageTag("nl")

    private fun song(library: String, id: String, title: String, language: String = "nl", tags: List<String> = emptyList(), sortTitle: String? = null) =
        SongbookEntry(library, id, title, sortTitle, language, tags, library.uppercase(), 0, "d", "songs/$id.cho")

    private val songs = listOf(
        song("a", "uil", "De uil zat in de olmen", sortTitle = "Uil zat in de olmen, De", tags = listOf("kinderlied")),
        song("a", "een", "Één kopje thee", tags = listOf("kampvuur")),
        song("a", "advocaatje", "Advocaatje", tags = listOf("kampvuur")),
        song("b", "advocaatje", "Advocaatje", tags = listOf("kampvuur")),
        song("b", "lorem", "Lorem Ipsum!", language = "la"),
    )

    private fun titles(rows: List<SongbookFilter.Row>) = rows.map { it.entry.libraryId + ":" + it.entry.title }

    @Test
    fun `everything A to Z by sort title by default, Z to A on request`() {
        val rows = SongbookFilter().apply(songs, null, nl)
        assertEquals(listOf("a:Advocaatje", "b:Advocaatje", "a:Één kopje thee", "b:Lorem Ipsum!", "a:De uil zat in de olmen"), titles(rows))
        assertEquals(titles(rows).reversed(), titles(SongbookFilter(descending = true).apply(songs, null, nl)))
        assertTrue(SongbookFilter().isDefault)
        assertFalse(SongbookFilter(descending = true).isDefault)
    }

    @Test
    fun `a title matches every typed word, without case or accents`() {
        assertEquals(listOf("a:Één kopje thee"), titles(SongbookFilter(query = "een THEE").apply(songs, null, nl)))
        assertEquals(listOf("a:De uil zat in de olmen"), titles(SongbookFilter(query = "olm uil").apply(songs, null, nl)))
        assertTrue(SongbookFilter(query = "uil koffie").apply(songs, null, nl).isEmpty())
    }

    @Test
    fun `in the lyrics the full-text hits decide`() {
        val f = SongbookFilter(query = "maan", inText = true)
        assertEquals(listOf("b:Lorem Ipsum!"), titles(f.apply(songs, setOf("b/lorem"), nl)))
        assertTrue(f.apply(songs, null, nl).isEmpty(), "no hits yet means nothing shown, not everything")
    }

    @Test
    fun `filters combine across groups and allow any value within one`() {
        assertEquals(listOf("a:Advocaatje", "a:Één kopje thee"), titles(SongbookFilter(libraries = setOf("a"), tags = setOf("kampvuur")).apply(songs, null, nl)))
        assertEquals(5, SongbookFilter(languages = setOf("nl", "la")).apply(songs, null, nl).size)
        assertEquals(listOf("b:Lorem Ipsum!"), titles(SongbookFilter(languages = setOf("la")).apply(songs, null, nl)))
    }

    @Test
    fun `the library is named only where two visible rows share a title`() {
        val rows = SongbookFilter().apply(songs, null, nl)
        assertEquals(listOf(true, true, false, false, false), rows.map { it.showLibrary })
        assertEquals(listOf(false), SongbookFilter(libraries = setOf("a"), query = "advo").apply(songs, null, nl).map { it.showLibrary })
    }
}
