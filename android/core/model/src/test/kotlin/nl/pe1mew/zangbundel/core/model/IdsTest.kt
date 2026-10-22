// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IdsTest {
    @Test
    fun `ids follow the song-format syntax`() {
        assertTrue(isValidId("vuur-in-de-nacht"))
        assertTrue(isValidId("a1"))
        assertFalse(isValidId("Vuur"))
        assertFalse(isValidId("vuur--nacht"))
        assertFalse(isValidId("-vuur"))
        assertFalse(isValidId(""))
        assertFalse(isValidId("a".repeat(65)))
        assertFailsWith<IllegalArgumentException> { SongId("Vuur") }
    }

    @Test
    fun `content hash round-trips through hex and compares by value`() {
        val hex = "3b7d2f9a1c4e6b8d0f2a4c6e8b1d3f5a"
        val h = ContentHash.fromHex(hex)
        assertEquals(hex, h.toHex())
        assertEquals(h, ContentHash.fromHex(hex))
        assertFailsWith<IllegalArgumentException> { ContentHash(ByteArray(15)) }
    }
}
