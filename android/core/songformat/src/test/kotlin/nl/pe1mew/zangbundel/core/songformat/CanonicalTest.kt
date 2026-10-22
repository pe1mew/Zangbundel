// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.songformat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CanonicalTest {
    @Test
    fun `a canonical file is left unchanged`() {
        val text = "{title: Vader Jacob}\n{meta: id vader-jacob}\n\nVader Jacob,\n"
        val r = Canonical.canonicalise(text)
        assertEquals(text, r.text)
        assertFalse(r.changed)
    }

    @Test
    fun `bom crlf trailing whitespace and missing final newline are normalised`() {
        val r = Canonical.canonicalise("﻿{title: X}\r\nline one  \r\n\r\n\r\n")
        assertEquals("{title: X}\nline one\n", r.text)
        assertTrue(r.changed)
    }

    @Test
    fun `the hash is 16 bytes and stable`() {
        val a = Canonical.hash("{title: X}\n")
        val b = Canonical.hash("{title: X}\n")
        assertEquals(a, b)
        assertEquals(32, a.toHex().length)
    }
}
