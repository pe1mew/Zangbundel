// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.songformat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Song format 4.2 (v0.28): which tag label is shown. */
class TagLabelTest {
    private val labels = mapOf("nl" to "Kampvuur", "nl-BE" to "Kampvuur (BE)", "en" to "Campfire", "de" to "Lagerfeuer", "fr" to "Feu de camp")

    @Test
    fun `the app's language exactly comes first`() {
        assertEquals("Kampvuur (BE)", TagLabel.choose(labels, "nl-BE"))
        assertEquals("Lagerfeuer", TagLabel.choose(labels, "de"))
    }

    @Test
    fun `then its primary subtag, then English`() {
        assertEquals("Kampvuur", TagLabel.choose(labels, "nl-NL"))
        assertEquals("Kampvuur", TagLabel.choose(labels, "nl"))
        assertEquals("Campfire", TagLabel.choose(labels, "it-IT"))
    }

    @Test
    fun `then the code that sorts first, whatever the file's order`() {
        assertEquals("Lagerfeuer", TagLabel.choose(mapOf("fr" to "Feu de camp", "de" to "Lagerfeuer"), "it"))
        assertEquals("Lagerfeuer", TagLabel.choose(mapOf("de" to "Lagerfeuer", "fr" to "Feu de camp"), "it"))
        assertEquals("Kampvuur", TagLabel.choose(mapOf("nl-BE" to "Kampvuur (BE)", "nl" to "Kampvuur"), "it"), "nl before nl-be")
    }

    @Test
    fun `codes are compared regardless of case`() {
        assertEquals("Kampvuur (BE)", TagLabel.choose(mapOf("NL-be" to "Kampvuur (BE)", "EN" to "Campfire"), "nl-BE"))
        assertEquals("Campfire", TagLabel.choose(mapOf("EN" to "Campfire", "de" to "Lagerfeuer"), "it"))
        assertEquals("Lagerfeuer", TagLabel.choose(mapOf("FR" to "Feu de camp", "De" to "Lagerfeuer"), "it"))
    }

    @Test
    fun `no label leaves the tag id to the caller`() {
        assertNull(TagLabel.choose(emptyMap(), "nl"))
    }
}
