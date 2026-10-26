// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.beacon

import nl.pe1mew.zangbundel.core.model.GatheringId
import nl.pe1mew.zangbundel.core.protocol.ProtocolConstants.FLAG_BCAST
import nl.pe1mew.zangbundel.core.protocol.ProtocolConstants.FLAG_BUSY
import nl.pe1mew.zangbundel.core.protocol.ProtocolConstants.FLAG_CODE
import nl.pe1mew.zangbundel.core.protocol.ProtocolConstants.FLAG_ENDED
import nl.pe1mew.zangbundel.core.protocol.cbor.CborBytes
import nl.pe1mew.zangbundel.core.protocol.cbor.CborInt
import nl.pe1mew.zangbundel.core.protocol.cbor.CborMap
import nl.pe1mew.zangbundel.core.protocol.cbor.Cbor
import nl.pe1mew.zangbundel.core.testing.JvmSodiumCrypto
import java.util.Base64
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class BeaconTest {
    private val gid = GatheringId(byteArrayOf(0x3B, 0x7D, 0x01, 0x02, 0x03))
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
    private fun beacon(name: String = "Kampvuur 25-10", flags: Int = FLAG_BCAST or FLAG_CODE) =
        Beacon(ver = 1, gid = gid, seq = 0x1234, flags = flags, pos = 12, name = name)

    @Test
    fun `the field bytes follow 6-1`() {
        // ver, gid (5), seq (u16 big-endian), flags, pos
        assertEquals("01" + "3b7d010203" + "1234" + "41" + "0c", beacon().fields().hex())
    }

    @Test
    fun `encoding A round-trips and fits the scan response`() {
        val bytes = BeaconCodec.encodeA(beacon())
        assertTrue(bytes.size <= 27)                         // 31 − length − type − company identifier
        val back = assertNotNull(BeaconCodec.decodeA(bytes))
        assertEquals("Kampvuur 25-10", back.name)
        assertEquals(0x1234, back.seq)
        assertEquals(12, back.pos)
        assertTrue(back.codeRequired && !back.ended && !back.busy)
        assertEquals(gid, back.gid)
    }

    @Test
    fun `names are cut at a character boundary`() {
        assertEquals(16, BeaconCodec.encodeA(beacon(name = "é".repeat(9))).size - 10)   // 8 × 2 bytes, not 17
        assertEquals("é".repeat(8), BeaconCodec.decodeA(BeaconCodec.encodeA(beacon(name = "é".repeat(9))))!!.name)
        assertEquals("Kampvuur 2", BeaconCodec.decodeB(BeaconCodec.encodeB(beacon()))!!.name)  // 10 bytes in B
    }

    @Test
    fun `encoding A refuses frames, test signals and malformed payloads`() {
        val good = BeaconCodec.encodeA(beacon())
        for (first in listOf(0x00, 0x7F, 0xD1, 0xD2)) assertNull(BeaconCodec.decodeA(good.copyOf().also { it[0] = first.toByte() }))
        assertNull(BeaconCodec.decodeA(good.copyOf(9)))
        assertNull(BeaconCodec.decodeA(good + ByteArray(20)))
        assertNull(BeaconCodec.decodeA(beacon().fields() + byteArrayOf(0xC3.toByte(), 0x28)))   // invalid UTF-8
        assertEquals("", BeaconCodec.decodeA(beacon().fields())!!.name)
    }

    @Test
    fun `Base32 follows RFC 4648 without padding`() {
        assertEquals("MZXW6YTBOI", Base32.encode("foobar".toByteArray()))
        assertEquals("MZXW6YQ", Base32.encode("foob".toByteArray()))
        assertContentEquals("foobar".toByteArray(), Base32.decode("MZXW6YTBOI"))
        assertEquals(16, Base32.encode(ByteArray(10)).length)
    }

    @Test
    fun `encoding B round-trips within 29 bytes`() {
        val b = beacon(flags = FLAG_ENDED)
        val name = BeaconCodec.encodeB(b)
        assertTrue(name.startsWith("ZB") && name[18] == ' ')
        assertTrue(name.toByteArray().size <= 29)
        val back = assertNotNull(BeaconCodec.decodeB(name))
        assertTrue(back.ended)
        assertContentEquals(b.fields(), back.fields())
        assertNull(BeaconCodec.decodeB("XY" + name.substring(2)))
        assertNull(BeaconCodec.decodeB(name.substring(0, 10)))
        assertNull(BeaconCodec.decodeB(name.substring(0, 18) + "#name"))
        assertNull(BeaconCodec.decodeB(name.substring(0, 2) + "1" + name.substring(3)))      // not Base32
        assertNull(BeaconCodec.decodeB(name.substring(0, 19) + "a much longer name"))
    }

    @Test
    fun `random input never throws`() {
        val r = Random(20261025)
        repeat(20_000) {
            val bytes = r.nextBytes(r.nextInt(0, 32))
            try {
                BeaconCodec.decodeA(bytes)
                BeaconCodec.decodeB("ZB" + String(CharArray(r.nextInt(0, 30)) { "ABCXYZ234567 é#".random(r) }))
            } catch (e: Throwable) {
                fail("${bytes.hex()} threw ${e::class.simpleName}")
            }
        }
    }
}

class JoinLinkTest {
    private val crypto = JvmSodiumCrypto()
    private val keys = crypto.generateSigningKeyPair()
    private val gid = nl.pe1mew.zangbundel.core.protocol.payload.gatheringIdOf(keys.publicKey, crypto)

    @Test
    fun `both link forms round-trip and pass the published page's rules`() {
        val link = JoinLink(gid, keys.publicKey, code = "K7Q2M9XA", name = "Kampvuur 25 oktober")
        val https = link.httpsUrl()
        assertTrue(https.startsWith("https://pe1mew.github.io/Zangbundel/j/#"))
        val fragment = https.substringAfter('#')
        assertTrue(Regex("^[A-Za-z0-9_-]{20,400}$").matches(fragment))      // docs/j/index.html, line 151
        val back = assertNotNull(JoinLink.parse(https, crypto))
        assertEquals("K7Q2M9XA", back.code)
        assertEquals("Kampvuur 25 oktober", back.name)
        assertContentEquals(keys.publicKey, back.pk)
        assertNotNull(JoinLink.parse(link.schemeUrl(), crypto))
        assertNotNull(JoinLink.parse(https.replace("/j/#", "/j#"), crypto))
        // 76 bytes of payload, 102 base64 characters, 141 characters of URL.
        assertEquals(76, link.payload().size)
        assertEquals(102, fragment.length)
        assertEquals(141, https.length)
    }

    @Test
    fun `link sizes from the smallest to the largest payload`() {
        // No code, no name: 45 bytes, 60 base64 characters, 99 characters of URL.
        val bare = JoinLink(gid, keys.publicKey)
        assertEquals(45, bare.payload().size)
        assertEquals(99, bare.httpsUrl().length)
        // An 8-character code and a 40-character ASCII name: 98 bytes, 131 base64, 170 of URL.
        val full = JoinLink(gid, keys.publicKey, code = "K7Q2M9XA", name = "n".repeat(40))
        assertEquals(98, full.payload().size)
        assertEquals(170, full.httpsUrl().length)
        // 40 accented characters take 80 bytes: 138 bytes of payload, 223 characters of URL.
        assertEquals(223, JoinLink(gid, keys.publicKey, code = "K7Q2M9XA", name = "é".repeat(40)).httpsUrl().length)
    }

    @Test
    fun `a test build marks its link and the app reads it like any other`() {
        val link = JoinLink(gid, keys.publicKey, code = "4711")
        val debug = link.httpsUrl("debug")
        assertTrue(debug.startsWith("https://pe1mew.github.io/Zangbundel/j/?app=debug#"))
        assertEquals(link.httpsUrl().substringAfter('#'), debug.substringAfter('#'))
        assertEquals("4711", assertNotNull(JoinLink.parse(debug, crypto)).code)
        assertNotNull(JoinLink.parse(link.httpsUrl("fieldtest").replace("/j/?", "/j?"), crypto))
        assertNull(JoinLink.parse(debug.replace("?app=debug", "?app=Debug"), crypto))
        assertNull(JoinLink.parse(debug.replace("?app=debug", "?x=debug"), crypto))
        assertFailsWith<IllegalArgumentException> { link.httpsUrl("de.bug") }
    }

    @Test
    fun `a key that does not match the gathering id is refused`() {
        val other = crypto.generateSigningKeyPair()
        assertNull(JoinLink.parse(JoinLink(gid, other.publicKey).httpsUrl(), crypto))
    }

    @Test
    fun `malformed links are refused`() {
        val good = JoinLink(gid, keys.publicKey).httpsUrl()
        assertNull(JoinLink.parse(good.replace("pe1mew.github.io", "example.org"), crypto))
        assertNull(JoinLink.parse("$good==", crypto))                                       // padding
        assertNull(JoinLink.parse("https://pe1mew.github.io/Zangbundel/j/#abc", crypto))     // too short
        fun link(m: CborMap) = JoinLink.SCHEME_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(Cbor.encode(m))
        assertNull(JoinLink.parse(link(CborMap.of(1L to CborInt(2), 2L to CborBytes(gid.bytes), 3L to CborBytes(keys.publicKey))), crypto))
        assertNull(JoinLink.parse(link(CborMap.of(1L to CborInt(1), 2L to CborBytes(gid.bytes), 3L to CborBytes(ByteArray(31)))), crypto))
        assertNull(JoinLink.parse(link(CborMap.of(1L to CborInt(1), 2L to CborBytes(gid.bytes), 3L to CborBytes(keys.publicKey), 4L to CborInt(5))), crypto))
    }

    @Test
    fun `random links never throw`() {
        val r = Random(7)
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        repeat(5_000) {
            val s = JoinLink.SCHEME_PREFIX + String(CharArray(r.nextInt(0, 420)) { alphabet.random(r) })
            try { JoinLink.parse(s, crypto) } catch (e: Throwable) { fail("$s threw ${e::class.simpleName}") }
        }
    }
}
