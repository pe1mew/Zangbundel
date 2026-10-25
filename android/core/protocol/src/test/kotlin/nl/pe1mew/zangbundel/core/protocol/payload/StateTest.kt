// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.payload

import nl.pe1mew.zangbundel.core.model.ContentHash
import nl.pe1mew.zangbundel.core.model.GatheringId
import nl.pe1mew.zangbundel.core.model.LibraryId
import nl.pe1mew.zangbundel.core.model.SongId
import nl.pe1mew.zangbundel.core.model.SongRef
import nl.pe1mew.zangbundel.core.protocol.cbor.Cbor
import nl.pe1mew.zangbundel.core.protocol.cbor.CborArray
import nl.pe1mew.zangbundel.core.protocol.cbor.CborBytes
import nl.pe1mew.zangbundel.core.protocol.cbor.CborInt
import nl.pe1mew.zangbundel.core.protocol.cbor.CborMap
import nl.pe1mew.zangbundel.core.testing.JvmSodiumCrypto
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StateTest {
    private val crypto = JvmSodiumCrypto()
    private val keys = crypto.signingKeyPairFromSeed(ByteArray(32) { it.toByte() })
    private val gid = gatheringIdOf(keys.publicKey, crypto)
    private val ek = crypto.generateBoxKeyPair().publicKey
    private val ref = SongRef(LibraryId("zangbundel-nl"), SongId("vader-jacob"), ContentHash(ByteArray(16) { 0x11 }), "2026.10.0")

    private fun body(
        seq: Int = 7, ts: Long = 120_000, status: Int = StateBody.STATUS_RUNNING,
        docs: Map<Int, DocEntry> = emptyMap(), dlg: List<ByteArray> = emptyList(), title: String? = "Vader Jacob",
    ) = StateBody(
        gid = gid, seq = seq, ts = ts, status = status, flags = 0b0000_0010, cur = ref, title = title, pos = 12,
        singers = 23, docs = docs, rseq = 4, dlg = dlg, pt = 4200, ek = ek,
    )

    @Test
    fun `gid is the first five bytes of SHA-256 over the public key`() {
        assertContentEquals(crypto.sha256(keys.publicKey).copyOf(5), gid.bytes)
    }

    @Test
    fun `a signed state verifies and decodes to the same body`() {
        val docs = mapOf(
            3 to DocEntry.of(Deflate.compress(ByteArray(2000)), Deflate.ENC_DEFLATE, crypto),
            7 to DocEntry.of(ByteArray(30), Deflate.ENC_RAW, crypto, gatt = false),
        )
        val signed = SignedState.sign(body(docs = docs, dlg = listOf(ByteArray(8) { 1 })), keys.secretKey, crypto)
        val back = SignedState.verify(signed.encode(), keys.publicKey, gid, crypto)
        assertContentEquals(signed.bodyBytes, back.bodyBytes)
        assertContentEquals(signed.bodyBytes, back.body.encode())
        assertEquals(7, back.dv)
        assertEquals(ref, back.body.cur)
        assertEquals(docs, back.body.docs)
        assertEquals(1, back.body.dlg.size)
        assertEquals(12, back.body.pos)
    }

    @Test
    fun `a typical state record fits the protocol's figures`() {
        val docs = (3..7).associateWith { DocEntry.of(ByteArray(100 * it), Deflate.ENC_DEFLATE, crypto) }
        val signed = SignedState.sign(body(docs = docs, dlg = List(2) { ByteArray(8) }), keys.secretKey, crypto)
        // 9.4: body at most 440 bytes; the record fits a 512-byte characteristic.
        assertTrue(signed.bodyBytes.size <= PayloadLimits.STATE_BODY, "body ${signed.bodyBytes.size} bytes")
        assertTrue(signed.encode().size <= PayloadLimits.GATT_VALUE)
    }

    private fun worstCase(title: String?, rev: String?, pt: Long?) = StateBody(
        gid = gid, seq = 0xFFFF, ts = Long.MAX_VALUE, status = 0, flags = 0xFF,
        cur = SongRef(LibraryId("a".repeat(64)), SongId("b".repeat(64)), ContentHash(ByteArray(16)), rev), title = title,
        pos = 255, singers = Long.MAX_VALUE,
        docs = (3..7).associateWith { DocEntry(ByteArray(8) { 0x7F }, Deflate.ENC_DEFLATE, PayloadLimits.DOCUMENT_FRAMED, gatt = true) },
        rseq = Long.MAX_VALUE, dlg = List(8) { ByteArray(8) }, pt = pt, ek = ek,
    )

    @Test
    fun `an oversized body loses title, then rev, then pt, and is signed`() {
        // 9.4 (Q17, decided 2026-10-25): leave out title, then cur's rev, then pt, as far as needed.
        val accented = worstCase(title = "é".repeat(60), rev = "c".repeat(40), pt = 60_000)
        assertTrue(accented.encode().size > PayloadLimits.STATE_BODY)
        val signed = SignedState.sign(accented, keys.secretKey, crypto)
        assertEquals(null, signed.body.title)
        assertTrue(signed.bodyBytes.size <= PayloadLimits.STATE_BODY)
        assertTrue(signed.encode().size <= PayloadLimits.GATT_VALUE)

        val huge = worstCase(title = "🔥".repeat(60), rev = "r".repeat(500), pt = Long.MAX_VALUE)
        val fitted = SignedState.sign(huge, keys.secretKey, crypto).body
        assertEquals(null, fitted.title)
        assertEquals(null, fitted.cur!!.revision)
        assertTrue(fitted.encode().size <= PayloadLimits.STATE_BODY)
    }

    @Test
    fun `with title, rev and pt left out the largest body fits`() {
        val bare = worstCase(title = null, rev = null, pt = null)
        // The figure 9.4 quotes for the largest body once the three fields are left out.
        assertEquals(405, bare.encode().size)
    }

    @Test
    fun `a body that fits is signed unchanged`() {
        val b = body()
        assertContentEquals(b.encode(), SignedState.sign(b, keys.secretKey, crypto).bodyBytes)
    }

    @Test
    fun `a tampered body, another key or another gathering is refused`() {
        val signed = SignedState.sign(body(), keys.secretKey, crypto)
        val tampered = signed.bodyBytes.copyOf().also { it[it.size - 1] = (it.last() + 1).toByte() }
        val forged = Cbor.encode(CborArray(CborBytes(tampered), CborBytes(signed.signature)))
        assertFailsWith<PayloadException> { SignedState.verify(forged, keys.publicKey, gid, crypto) }

        val other = crypto.generateSigningKeyPair()
        assertFailsWith<PayloadException> { SignedState.verify(signed.encode(), other.publicKey, gid, crypto) }

        val otherGid = GatheringId(byteArrayOf(1, 2, 3, 4, 5))
        assertFailsWith<PayloadException> { SignedState.verify(signed.encode(), keys.publicKey, otherGid, crypto) }
    }

    @Test
    fun `malformed records are refused before any body is read`() {
        val signed = SignedState.sign(body(), keys.secretKey, crypto)
        val cases = listOf(
            Cbor.encode(CborArray(CborBytes(signed.bodyBytes))),                                             // no signature
            Cbor.encode(CborArray(CborBytes(signed.bodyBytes), CborBytes(signed.signature.copyOf(63)))),      // short signature
            Cbor.encode(CborBytes(signed.bodyBytes)),                                                         // not an array
            Cbor.encode(CborArray(CborBytes(ByteArray(441)), CborBytes(ByteArray(64)))),                      // body over 440
            ByteArray(600),                                                                                   // over 512
        )
        for (bytes in cases) assertFailsWith<PayloadException> { SignedState.verify(bytes, keys.publicKey, gid, crypto) }
    }

    @Test
    fun `a signed body missing a required key is refused`() {
        val noEk = Cbor.encode(
            CborMap(
                (Cbor.decode(body().encode(), 440) as CborMap).entries.filter {
                    it.first != CborInt(14)
                },
            ),
        )
        val record = Cbor.encode(CborArray(CborBytes(noEk), CborBytes(crypto.sign(noEk, keys.secretKey))))
        assertFailsWith<PayloadException> { SignedState.verify(record, keys.publicKey, gid, crypto) }
    }

    @Test
    fun `ts must increase unless the gathering ended`() {
        val s = SignedState.sign(body(ts = 1000), keys.secretKey, crypto)
        assertTrue(s.supersedes(null))
        assertTrue(s.supersedes(999))
        assertFalse(s.supersedes(1000))
        val ended = SignedState.sign(body(ts = 10, status = StateBody.STATUS_ENDED), keys.secretKey, crypto)
        assertTrue(ended.supersedes(5000))
    }

    @Test
    fun `manifest entries give dv as the first two bytes of dh`() {
        val e = DocEntry(byteArrayOf(0x12, 0x34, 0, 0, 0, 0, 0, 0), Deflate.ENC_RAW, 10)
        assertEquals(0x1234, e.dv)
        assertContentEquals(crypto.sha256(ByteArray(3)).copyOf(8), DocEntry.of(ByteArray(3), 0, crypto).dh)
    }

    @Test
    fun `titles are truncated at 60 characters`() {
        val b = body(title = "🔥".repeat(70)) // a four-byte character, 70 times
        assertEquals(60, b.title!!.codePointCount(0, b.title!!.length))
    }
}
