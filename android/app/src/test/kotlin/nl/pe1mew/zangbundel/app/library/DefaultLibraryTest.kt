// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.library

import nl.pe1mew.zangbundel.core.songformat.LibraryUrl
import nl.pe1mew.zangbundel.platform.storage.LibraryRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DefaultLibraryTest {
    private fun row(id: String, installedAt: Long) =
        LibraryRow(id, id, "", "r", "r-00000000", null, null, null, null, null, "", "1.1", "CC0-1.0", "", installedAt)

    @Test
    fun `the default library is listed first, the others in the order they were added`() {
        val rows = listOf(row("eigen", 1), row(DefaultLibrary.ID, 3), row("tweede", 2))
        assertEquals(listOf(DefaultLibrary.ID, "eigen", "tweede"), rows.sortedWith(DefaultLibrary.order).map { it.id })
    }

    @Test
    fun `the default url leads to the repository's release index`() {
        assertEquals(
            "https://raw.githubusercontent.com/pe1mew/Zangbundel-bibliotheek/HEAD/releases.json",
            assertIs<LibraryUrl.Index>(LibraryUrl.of(DefaultLibrary.URL)).url,
        )
    }
}
