// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals

/** The field-test log's lines (architecture A-20): flat JSON of counts, flags and nested capability maps. */
class FieldLogTest {
    @Test
    fun `a line is one JSON object, nested maps included`() {
        assertEquals(
            "{\"e\":\"sample\",\"t\":60000,\"battery\":87,\"charging\":false,\"caps\":{\"extended\":true,\"maxDataLength\":1650},\"none\":null}",
            FieldLog.json(mapOf("e" to "sample", "t" to 60_000L, "battery" to 87, "charging" to false, "caps" to mapOf("extended" to true, "maxDataLength" to 1650), "none" to null)),
        )
    }

    @Test
    fun `strings cannot break out of their quotes or the line`() {
        assertEquals("{\"v\":\"a\\\"b\\\\c\\u000ad\"}", FieldLog.json(mapOf("v" to "a\"b\\c\nd")))
    }
}
