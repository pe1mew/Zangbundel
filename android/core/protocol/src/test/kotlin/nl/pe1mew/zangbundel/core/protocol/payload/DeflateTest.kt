// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.payload

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

class DeflateTest {
    private val lyrics = ("{title: Kampvuurlied}\n{meta: id kampvuurlied}\n\n" +
        "Het vuur brandt hoog, de vonken gaan,\nwe zingen samen onder de maan.\n").repeat(20).toByteArray()

    @Test
    fun `compressible documents are deflated and come back unchanged`() {
        val (enc, framed) = Deflate.frame(lyrics)
        assertEquals(Deflate.ENC_DEFLATE, enc)
        assertTrue(framed.size < lyrics.size * 9 / 10)
        assertContentEquals(lyrics, Deflate.unframe(enc, framed, lyrics.size))
    }

    @Test
    fun `documents that do not shrink by 10 percent stay raw`() {
        val noise = Random(1).nextBytes(500)
        val (enc, framed) = Deflate.frame(noise)
        assertEquals(Deflate.ENC_RAW, enc)
        assertContentEquals(noise, framed)
    }

    @Test
    fun `the output cap stops a bomb while inflating`() {
        val bomb = Deflate.compress(ByteArray(10_000_000))
        assertTrue(bomb.size < 20_000)
        val e = assertFailsWith<PayloadException> { Deflate.inflate(bomb, maxOutput = PayloadLimits.DOCUMENT_DECODED) }
        assertTrue(e.message!!.contains("exceeds"))
    }

    @Test
    fun `output exactly at the cap is accepted, one byte more is not`() {
        val data = ByteArray(1000) { (it % 7).toByte() }
        val compressed = Deflate.compress(data)
        assertContentEquals(data, Deflate.inflate(compressed, 1000))
        assertFailsWith<PayloadException> { Deflate.inflate(compressed, 999) }
    }

    @Test
    fun `truncated, malformed and trailing input is refused`() {
        val compressed = Deflate.compress(lyrics)
        assertFailsWith<PayloadException> { Deflate.inflate(compressed.copyOf(compressed.size - 5), 1 shl 16) }
        assertFailsWith<PayloadException> { Deflate.inflate(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()), 1 shl 16) }
        assertFailsWith<PayloadException> { Deflate.inflate(compressed + byteArrayOf(1, 2, 3), 1 shl 16) }
        assertFailsWith<PayloadException> { Deflate.unframe(2, compressed, 1 shl 16) }
        assertFailsWith<PayloadException> { Deflate.unframe(Deflate.ENC_RAW, ByteArray(11), 10) }
    }

    @Test
    fun `random input only ever fails with PayloadException`() {
        val random = Random(20261025)
        repeat(20_000) {
            val bytes = random.nextBytes(random.nextInt(0, 64))
            try {
                Deflate.inflate(bytes, 4096)
            } catch (e: PayloadException) {
                // expected
            } catch (e: Throwable) {
                fail("${bytes.joinToString("") { "%02x".format(it) }} threw ${e::class.simpleName}")
            }
        }
    }
}
