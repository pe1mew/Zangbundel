// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.beacon

import nl.pe1mew.zangbundel.core.model.GatheringId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FingerprintTest {
    private val first = List(Fingerprint.WORDS_PER_LIST) { "a$it" }
    private val second = List(Fingerprint.WORDS_PER_LIST) { "b$it" }

    @Test
    fun `the words come from the first two bytes of the gathering id`() {
        val key = Fingerprint.keyOf(GatheringId(byteArrayOf(133.toByte(), 0, 7, 7, 7)))
        assertEquals(Fingerprint.Key(133, 0), key)
        assertEquals("a133-b0", Fingerprint.words(key, first, second), "ble-protocol 6.6: appel-vuur in Dutch")
    }

    @Test
    fun `bytes are read unsigned`() {
        val key = Fingerprint.keyOf(GatheringId(byteArrayOf(-1, -128, 0, 0, 0)))
        assertEquals(Fingerprint.Key(255, 128), key)
        assertEquals("a255-b128", Fingerprint.words(key, first, second))
    }

    @Test
    fun `a list of another length is refused`() {
        assertFailsWith<IllegalArgumentException> { Fingerprint.words(Fingerprint.Key(0, 0), first.dropLast(1), second) }
    }
}
