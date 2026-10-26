// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui

import nl.pe1mew.zangbundel.app.gathering.Gathering
import nl.pe1mew.zangbundel.core.model.GatheringId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** guiDesign D-36: the warning line when two gatherings in range have nearly the same name. */
class NearlySameNamesTest {
    private fun nearby(vararg names: String) = names.mapIndexed { i, n -> Gathering.Nearby(GatheringId(ByteArray(GatheringId.SIZE) { i.toByte() }), n, -60, false, 0) }

    @Test
    fun `the same or nearly the same name warns`() {
        assertTrue(nearlySameNames(nearby("Kampvuur 26 okt", "Kampvuur 26 okt")))
        assertTrue(nearlySameNames(nearby("Kampvuur 26 okt", "kampvuur 26-okt.")))
        assertTrue(nearlySameNames(nearby("Zomerkamp", "Zomerkampp")))
        assertTrue(nearlySameNames(nearby("Bevers", "Welpen", "Bevers!")))
    }

    @Test
    fun `different names and a single gathering do not`() {
        assertFalse(nearlySameNames(nearby("Kampvuur 26 okt")))
        assertFalse(nearlySameNames(nearby("Bevers", "Welpen")))
        assertFalse(nearlySameNames(nearby("Kampvuur 26 okt", "Kampvuur 19 nov")))
    }
}
