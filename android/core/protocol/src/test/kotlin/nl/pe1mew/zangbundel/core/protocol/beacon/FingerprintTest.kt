// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.beacon

import nl.pe1mew.zangbundel.core.model.GatheringId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FingerprintTest {
    @Test
    fun `two lists of 256 words, a to z, no word in both`() {
        for (list in listOf(Fingerprint.FIRST, Fingerprint.SECOND)) {
            assertEquals(256, list.size)
            assertEquals(256, list.toSet().size, "no duplicates")
            assertTrue(list.all { Regex("[a-z]{3,8}").matches(it) }, "3 to 8 letters a to z")
        }
        assertTrue(Fingerprint.FIRST.intersect(Fingerprint.SECOND.toSet()).isEmpty())
    }

    @Test
    fun `the words come from the first two bytes of the gathering id`() {
        val appel = Fingerprint.FIRST.indexOf("appel")
        val gid = GatheringId(byteArrayOf(appel.toByte(), 0, 7, 7, 7))
        assertEquals("appel-vuur", Fingerprint.of(gid), "ble-protocol 6.6's example")
        assertEquals(Fingerprint.FIRST.last() + "-" + Fingerprint.SECOND.last(), Fingerprint.of(GatheringId(byteArrayOf(-1, -1, 0, 0, 0))), "bytes read unsigned")
    }
}
