// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.frame

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FrameTest {
    private fun hex(s: String): ByteArray = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
    private fun doc(n: Int) = ByteArray(n) { (it * 7 + 3).toByte() }

    @Test
    fun `legacy frame layout follows 7-2`() {
        val frame = Frame(Carrier.LEGACY, 0xA1B2, 3, 0x0102, 2, 1, 16, 18, hex("cafe" + "00".repeat(14)))
        // magic, gid16, doc, dv, k, idx, len, 16 data bytes: 26 bytes
        val bytes = FrameCodec.encode(frame)
        assertEquals("d1" + "a1b2" + "03" + "0102" + "02" + "01" + "0012" + "cafe" + "00".repeat(14), bytes.hex())
        assertEquals(26, bytes.size)
    }

    @Test
    fun `extended frame layout follows 7-2`() {
        val frame = Frame(Carrier.EXTENDED, 0xA1B2, 3, 0x0102, 2, 3, 4, 7, hex("beef0000"))
        // magic, gid16, doc, dv, k u16, idx u16, fs u16, len u16, fs data bytes
        assertEquals("d2" + "a1b2" + "03" + "0102" + "0002" + "0003" + "0004" + "0007" + "beef0000", FrameCodec.encode(frame).hex())
    }

    @Test
    fun `framing gives k source frames then the repair frames`() {
        val legacy = Framer.legacy(1, 3, 1, doc(40))                 // k = 3, r = 6
        assertEquals(9, legacy.size)
        assertEquals(listOf(true, true, true, false, false, false, false, false, false), legacy.map { it.isSource })
        assertTrue(legacy.all { it.k == 3 && it.len == 40 && it.data.size == 16 })
        assertContentEquals(doc(40).copyOfRange(32, 40) + ByteArray(8), legacy[2].data)      // the last source frame is padded
        assertEquals(256, Framer.legacy(1, 3, 1, doc(2000)).size)      // k = 125, r = 131
        assertEquals(27, Framer.extended(1, 3, 1, doc(2000)).size)     // k = 9, r = 18
        assertEquals(256, Framer.legacy(1, 3, 1, doc(4080)).size)      // k = 255, r = 1
        assertEquals(3, Framer.legacy(1, 5, 1, ByteArray(0)).size)     // an empty document: k = 1, r = 2
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
    fun `framing refuses what the carrier cannot carry`() {
        assertFailsWith<IllegalArgumentException> { Framer.legacy(1, 3, 1, doc(4081)) }
        assertFailsWith<IllegalArgumentException> { Framer.extended(1, 3, 1, doc(10), fs = 234) }
        assertFailsWith<IllegalArgumentException> { Framer.extended(1, 3, 1, doc(10), fs = 0) }
    }

    @Test
    fun `decoding returns null for anything that is not a well-formed data frame`() {
        val good = "d1a1b2030102" + "02" + "01" + "0012" + "00".repeat(16)
        assertNotNull(FrameCodec.decode(hex(good)))
        assertNull(FrameCodec.decode(ByteArray(0)))
        assertNull(FrameCodec.decode(hex("01" + "00".repeat(9))))                                   // beacon
        assertNull(FrameCodec.decode(hex(good).copyOf(25)))                                         // short data
        assertNull(FrameCodec.decode(hex(good) + byteArrayOf(0)))                                   // long data
        assertNull(FrameCodec.decode(hex("d1a1b2030102" + "00" + "00" + "0000" + "00".repeat(16))))  // k = 0
        // v0.34: an index at or past `n` (here 6 for k = 2) is a frame of the state's lead-in (7.4 rule 4), no longer refused.
        assertNotNull(FrameCodec.decode(hex("d1a1b2030102" + "02" + "06" + "0012" + "00".repeat(16))))
        assertNull(FrameCodec.decode(hex("d1a1b2030102" + "02" + "00" + "0010" + "00".repeat(16))))  // len 16 needs 1 frame, not 2
        assertNull(FrameCodec.decode(hex("d1a1b2030102" + "02" + "00" + "0021" + "00".repeat(16))))  // len 33 needs 3 frames
        assertNull(FrameCodec.decode(hex("d2a1b2030102" + "0001" + "0000" + "0000" + "0000")))       // fs 0
        assertNull(FrameCodec.decode(hex("d2a1b2030102" + "0001" + "0000" + "00ea" + "0001" + "00".repeat(234))))   // fs 234
        assertNotNull(FrameCodec.decode(hex("d2a1b2030102" + "0001" + "0000" + "00e9" + "00e9" + "00".repeat(233))))
    }
}
