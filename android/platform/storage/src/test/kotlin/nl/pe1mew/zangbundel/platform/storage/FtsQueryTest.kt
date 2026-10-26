// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.platform.storage

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FtsQueryTest {
    @Test
    fun `typed words become required prefix terms`() {
        assertEquals("Hoog* laait*", FtsQuery.of("  Hoog laait "))
        assertEquals("Één* vuur*", FtsQuery.of("Één, vuur!"), "case and accents stay; the tokenizer folds them")
        assertEquals("rock* n* roll*", FtsQuery.of("rock 'n' roll"))
    }

    @Test
    fun `query syntax typed by the user never reaches the match`() {
        assertEquals("vuur* nacht*", FtsQuery.of("\"vuur\" OR -nacht"))
        assertEquals("title* x*", FtsQuery.of("title:x"))
        assertEquals("vuur* 3*", FtsQuery.of("vuur NEAR/3 AND not"))
        assertNull(FtsQuery.of("  * \" - ( ) "))
        assertNull(FtsQuery.of(""))
    }

    @Test
    fun `at most eight terms`() {
        assertEquals(8, FtsQuery.of((1..20).joinToString(" ") { "w$it" })!!.split(' ').size)
    }

    @Test
    fun `random input gives only letters, digits, stars and spaces`() {
        val random = Random(20261026)
        repeat(10_000) {
            val input = String(CharArray(random.nextInt(40)) { random.nextInt(0x20, 0x3000).toChar() })
            val q = FtsQuery.of(input) ?: return@repeat
            assertTrue(q.all { it.isLetterOrDigit() || it == '*' || it == ' ' }, q)
        }
    }
}
