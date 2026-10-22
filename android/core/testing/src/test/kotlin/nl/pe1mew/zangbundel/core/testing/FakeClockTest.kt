// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.testing

import kotlin.test.Test
import kotlin.test.assertEquals

class FakeClockTest {
    @Test
    fun `advances by hand only`() {
        val clock = FakeClock(1_000)
        clock.advance(250)
        assertEquals(1_250, clock.nowMillis)
    }
}
