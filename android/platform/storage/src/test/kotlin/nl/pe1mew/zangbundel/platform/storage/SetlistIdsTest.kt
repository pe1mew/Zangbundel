// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.platform.storage

import nl.pe1mew.zangbundel.core.model.isValidId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SetlistIdsTest {
    @Test
    fun `an id from the title, in song-id syntax`() {
        assertEquals("zomerkamp-2026", SetlistIds.of("Zomerkamp 2026", emptySet()))
        assertEquals("een-avond-bij-t-vuur", SetlistIds.of("  Één avond bij 't vuur! ", emptySet()))
        assertEquals("setlist", SetlistIds.of("🔥 ★", emptySet()), "nothing usable left")
    }

    @Test
    fun `taken ids get a number`() {
        assertEquals("zomerkamp-3", SetlistIds.of("Zomerkamp", setOf("zomerkamp", "zomerkamp-2")))
    }

    @Test
    fun `random titles always give a valid id`() {
        val random = Random(20261026)
        repeat(5_000) {
            val title = String(CharArray(random.nextInt(0, 90)) { random.nextInt(0x20, 0x2000).toChar() })
            assertTrue(isValidId(SetlistIds.of(title, setOf("setlist"))), title)
        }
    }
}
