// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.cbor

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

class CborTest {

    private fun hex(s: String): ByteArray = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
    private fun decode(h: String, max: Int = 1024): CborValue = Cbor.decode(hex(h), max)
    private fun rejects(h: String, max: Int = 1024): String =
        assertFailsWith<CborException> { decode(h, max) }.message ?: ""

    /** RFC 8949 appendix A, the subset the protocol's data model covers. */
    private val vectors: List<Pair<CborValue, String>> = listOf(
        CborInt(0) to "00",
        CborInt(1) to "01",
        CborInt(10) to "0a",
        CborInt(23) to "17",
        CborInt(24) to "1818",
        CborInt(25) to "1819",
        CborInt(100) to "1864",
        CborInt(1000) to "1903e8",
        CborInt(1000000) to "1a000f4240",
        CborInt(1000000000000) to "1b000000e8d4a51000",
        CborInt(-1) to "20",
        CborInt(-10) to "29",
        CborInt(-100) to "3863",
        CborInt(-1000) to "3903e7",
        CborInt(Long.MAX_VALUE) to "1b7fffffffffffffff",
        CborInt(Long.MIN_VALUE) to "3b7fffffffffffffff",
        CborBytes(ByteArray(0)) to "40",
        CborBytes(hex("01020304")) to "4401020304",
        CborText("") to "60",
        CborText("a") to "6161",
        CborText("IETF") to "6449455446",
        CborText("\"\\") to "62225c",
        CborText("ü") to "62c3bc",
        CborText("水") to "63e6b0b4",
        CborText("𐅑") to "64f0908591",
        CborArray() to "80",
        CborArray(CborInt(1), CborInt(2), CborInt(3)) to "83010203",
        CborArray(CborInt(1), CborArray(CborInt(2), CborInt(3)), CborArray(CborInt(4), CborInt(5))) to "8301820203820405",
        CborMap(emptyList()) to "a0",
        CborMap.of(1L to CborInt(2), 3L to CborInt(4)) to "a201020304",
        CborMap(listOf(CborText("a") to CborInt(1), CborText("b") to CborArray(CborInt(2), CborInt(3)))) to "a26161016162820203",
        CborBool(false) to "f4",
        CborBool(true) to "f5",
        CborNull to "f6",
    )

    @Test
    fun `encodes the RFC 8949 vectors with preferred serialization`() {
        for ((value, expected) in vectors) assertEquals(expected, Cbor.encode(value).hex(), "encoding $value")
    }

    @Test
    fun `decodes the RFC 8949 vectors`() {
        for ((expected, h) in vectors) assertEquals(expected, decode(h), "decoding $h")
    }

    @Test
    fun `argument widths switch at the preferred boundaries`() {
        val cases = mapOf(23L to 1, 24L to 2, 255L to 2, 256L to 3, 65535L to 3, 65536L to 5, 4294967295L to 5, 4294967296L to 9)
        for ((v, size) in cases) {
            assertEquals(size, Cbor.encode(CborInt(v)).size, "unsigned $v")
            assertEquals(size, Cbor.encode(CborInt(-1 - v)).size, "negative ${-1 - v}")
        }
    }

    @Test
    fun `a protocol-shaped map round-trips`() {
        val payload = CborMap.of(
            1L to CborInt(3),
            2L to CborBytes(ByteArray(16) { it.toByte() }),
            3L to CborText("Kampvuur 25-10"),
            4L to CborArray(CborArray(CborInt(0), CborInt(16)), CborArray(CborInt(48), CborInt(32))),
            5L to CborBool(true),
        )
        val bytes = Cbor.encode(payload)
        val back = Cbor.decodeMap(bytes, bytes.size)
        assertEquals(payload, back)
        assertEquals("Kampvuur 25-10", back.requireText(3))
        assertEquals(2, back.requireArray(4).size)
    }

    @Test
    fun `non-preferred encodings are accepted on decode`() {
        assertEquals(CborInt(23), decode("1817"))
        assertEquals(CborInt(1), decode("1a00000001"))
    }

    @Test
    fun `size limit is checked before decoding`() {
        assertTrue(rejects("83010203", max = 3).contains("exceeds the limit"))
        assertEquals(CborArray(CborInt(1), CborInt(2), CborInt(3)), decode("83010203", max = 4))
    }

    @Test
    fun `indefinite lengths are rejected`() {
        rejects("9f01ff")        // indefinite array
        rejects("5f4101ff")      // indefinite byte string
        rejects("7f6161ff")      // indefinite text string
        rejects("bf0102ff")      // indefinite map
        rejects("ff")            // lone break
    }

    @Test
    fun `tags floats and other simple values are rejected`() {
        rejects("c074323031332d30332d32315432303a30343a30305a") // tag 0
        rejects("f93c00")        // half float
        rejects("fa47c35000")    // single float
        rejects("fb3ff199999999999a") // double float
        rejects("f7")            // undefined
        rejects("f0")            // simple value 16
        rejects("f820")          // simple value 32
    }

    @Test
    fun `reserved additional information is rejected`() {
        rejects("1c"); rejects("1d"); rejects("1e")
    }

    @Test
    fun `duplicate map keys are rejected`() {
        assertTrue(rejects("a201020103").contains("duplicate"))
        rejects("a2410102410103") // byte-string keys compare by content
    }

    @Test
    fun `nesting is limited to eight levels`() {
        val eight = "81".repeat(8) + "00"
        val nine = "81".repeat(9) + "00"
        assertEquals(8, generateSequence(decode(eight)) { (it as? CborArray)?.items?.single() }.count { it is CborArray })
        assertTrue(rejects(nine).contains("nesting"))
        rejects("a101".repeat(9) + "00")
    }

    @Test
    fun `integers beyond the Long range are rejected`() {
        rejects("1b8000000000000000")
        rejects("3b8000000000000000")
        rejects("1bffffffffffffffff")
    }

    @Test
    fun `lengths and counts beyond the input are rejected without allocating`() {
        rejects("5affffffff")    // byte string of 4 GiB announced in a 5-byte payload
        rejects("7affffffff")
        rejects("9affffffff")    // array of 4 billion items
        rejects("baffffffff")    // map of 4 billion pairs
        rejects("5b7fffffffffffffff")
        rejects("4401")          // truncated byte string
        rejects("8201")          // truncated array
        rejects("19")            // truncated argument
        rejects("")              // empty
    }

    @Test
    fun `invalid UTF-8 and trailing bytes are rejected`() {
        assertTrue(rejects("62c328").contains("UTF-8"))
        rejects("63eda080")      // encoded surrogate
        assertTrue(rejects("0000").contains("trailing"))
    }

    @Test
    fun `required keys must be present with the right type`() {
        val map = CborMap.of(1L to CborInt(5), 2L to CborText("x"))
        assertEquals(5, map.requireInt(1))
        assertFailsWith<CborException> { map.requireInt(3) }
        assertFailsWith<CborException> { map.requireInt(2) }
        assertFailsWith<CborException> { Cbor.decodeMap(hex("80"), 10) }
    }

    @Test
    fun `random input only ever fails with CborException`() {
        val random = Random(20261025)
        repeat(50_000) { round ->
            val bytes = random.nextBytes(random.nextInt(0, 48))
            // Bias the first byte towards containers so deeper paths are reached as well.
            if (bytes.isNotEmpty() && round % 3 == 0) bytes[0] = (0x80 or random.nextInt(0, 0x40)).toByte()
            try {
                val value = Cbor.decode(bytes, maxSize = 64)
                assertEquals(value, Cbor.decode(Cbor.encode(value), maxSize = 1 shl 16), "re-encoding of ${bytes.hex()}")
            } catch (e: CborException) {
                // expected for most inputs
            } catch (e: Throwable) {
                fail("input ${bytes.hex()} threw ${e::class.simpleName}: ${e.message}")
            }
        }
    }
}
