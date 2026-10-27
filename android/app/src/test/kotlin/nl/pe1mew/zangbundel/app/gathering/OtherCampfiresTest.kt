// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.gathering

import nl.pe1mew.zangbundel.core.model.GatheringId
import nl.pe1mew.zangbundel.core.protocol.beacon.Fingerprint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Another gathering heard while leading (FR-GATH-17, ble-protocol 10.1 step 6, guiDesign D-68). */
class OtherCampfiresTest {
    private val own = GatheringId(byteArrayOf(1, 2, 3, 4, 5))
    private val a = GatheringId(byteArrayOf(9, 9, 9, 9, 1))
    private val b = GatheringId(byteArrayOf(9, 9, 9, 9, 2))

    @Test
    fun `own beacons, ended gatherings and relays are never another campfire`() {
        val o = OtherCampfires(own, 65_000)
        o.seen(own, "wij", ended = false, relay = false, now = 0)
        o.seen(a, "klaar", ended = true, relay = false, now = 0)
        o.seen(b, "doorgegeven", ended = false, relay = true, now = 0)
        assertNull(o.near(1_000, "wij"))
    }

    @Test
    fun `the one heard last is named, and one not heard within the timeout is gone`() {
        val o = OtherCampfires(own, 65_000)
        o.seen(a, "Bevers", ended = false, relay = false, now = 0)
        o.seen(b, "Welpen", ended = false, relay = false, now = 30_000)
        assertEquals("Welpen", o.near(31_000, "Kampvuur 27 okt"))
        assertEquals("Welpen", o.near(90_000, "Kampvuur 27 okt"))
        assertNull(o.near(96_000, "Kampvuur 27 okt"))
    }

    @Test
    fun `a name the same as this campfire's own is replaced by the other's two words`() {
        val o = OtherCampfires(own, 65_000)
        o.seen(a, "Kampvuur 27 okt", ended = false, relay = false, now = 0)
        assertEquals(Fingerprint.of(a), o.near(1_000, " kampvuur 27 OKT"))
    }
}
