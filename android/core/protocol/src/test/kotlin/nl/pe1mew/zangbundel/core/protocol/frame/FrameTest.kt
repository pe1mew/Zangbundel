// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.frame

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FrameTest {
    private fun hex(s: String): ByteArray = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
    private fun doc(n: Int) = ByteArray(n) { (it * 7 + 3).toByte() }

    @Test
    fun `legacy frame layout follows 7-2`() {
        val frame = Frame(Carrier.LEGACY, 0xA1B2, 3, 0x0102, 2, 1, 16, hex("cafe"))
        // magic, gid16, doc, dv, total, idx, data
        assertEquals("d1" + "a1b2" + "03" + "0102" + "02" + "01" + "cafe", FrameCodec.encode(frame).hex())
    }

    @Test
    fun `extended frame layout follows 7-2`() {
        val frame = Frame(Carrier.EXTENDED, 0xA1B2, 3, 0x0102, 0x0203, 0x0202, 200, hex("beef"))
        // magic, gid16, doc, dv, total u16, idx u16, fs u16, data
        assertEquals("d2" + "a1b2" + "03" + "0102" + "0203" + "0202" + "00c8" + "beef", FrameCodec.encode(frame).hex())
    }

    @Test
    fun `framing then decoding gives back every frame`() {
        for (frame in Framer.legacy(7, 3, 9, doc(40)) + Framer.extended(7, 3, 9, doc(500))) {
            val back = assertNotNull(FrameCodec.decode(FrameCodec.encode(frame)))
            assertEquals(frame.toString(), back.toString())
            assertContentEquals(frame.data, back.data)
        }
    }

    @Test
    fun `documents split into full frames and a shorter last one`() {
        assertEquals(listOf(16, 16, 8), Framer.legacy(1, 3, 1, doc(40)).map { it.dataSize })
        assertEquals(listOf(235, 235, 30), Framer.extended(1, 3, 1, doc(500)).map { it.dataSize })
        assertEquals(listOf(100, 100, 100), Framer.extended(1, 3, 1, doc(300), fs = 100).map { it.dataSize })
        assertEquals(listOf(0), Framer.legacy(1, 5, 1, ByteArray(0)).map { it.dataSize })
        assertEquals(255, Framer.legacy(1, 3, 1, doc(4080)).size)
    }

    @Test
    fun `framing refuses what the carrier cannot carry`() {
        assertFailsWith<IllegalArgumentException> { Framer.legacy(1, 3, 1, doc(4081)) }
        assertFailsWith<IllegalArgumentException> { Framer.extended(1, 3, 1, doc(10), fs = 236) }
        assertFailsWith<IllegalArgumentException> { Framer.extended(1, 3, 1, doc(10), fs = 0) }
    }

    @Test
    fun `decoding returns null for anything that is not a well-formed data frame`() {
        assertNull(FrameCodec.decode(ByteArray(0)))
        assertNull(FrameCodec.decode(hex("01" + "00".repeat(9))))                 // beacon
        assertNull(FrameCodec.decode(hex("d1a1b2030102")))                         // shorter than the header
        assertNull(FrameCodec.decode(hex("d1a1b20301020101" + "00".repeat(17))))  // 17 data bytes
        assertNull(FrameCodec.decode(hex("d1a1b20301020000")))                     // total 0
        assertNull(FrameCodec.decode(hex("d1a1b20301020202" + "00".repeat(16))))  // idx == total
        assertNull(FrameCodec.decode(hex("d1a1b20301020200" + "00".repeat(8))))   // non-last frame short
        assertNull(FrameCodec.decode(hex("d1a1b20301020201")))                     // last of two frames empty
        assertNotNull(FrameCodec.decode(hex("d1a1b20301020100")))                  // empty document, one frame
        assertNull(FrameCodec.decode(hex("d2a1b2030102000100000000")))             // fs 0
        assertNull(FrameCodec.decode(hex("d2a1b20301020001000000ec" + "00")))     // fs 236
        assertNull(FrameCodec.decode(hex("d2a1b20301020001000000eb" + "00".repeat(236)))) // longer than 247
        assertNotNull(FrameCodec.decode(hex("d2a1b20301020001000000eb" + "00".repeat(235))))
    }
}
