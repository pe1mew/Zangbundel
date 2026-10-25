// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.payload

import nl.pe1mew.zangbundel.core.model.ContentHash
import nl.pe1mew.zangbundel.core.model.GatheringId
import nl.pe1mew.zangbundel.core.model.LibraryId
import nl.pe1mew.zangbundel.core.model.SongId
import nl.pe1mew.zangbundel.core.model.SongRef
import nl.pe1mew.zangbundel.core.protocol.cbor.Cbor
import nl.pe1mew.zangbundel.core.protocol.cbor.CborBytes
import nl.pe1mew.zangbundel.core.protocol.cbor.CborInt
import nl.pe1mew.zangbundel.core.protocol.cbor.CborMap
import nl.pe1mew.zangbundel.core.protocol.cbor.CborText
import nl.pe1mew.zangbundel.core.testing.JvmSodiumCrypto
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class PayloadTest {
    private val crypto = JvmSodiumCrypto()
    private val gid = GatheringId(byteArrayOf(1, 2, 3, 4, 5))
    private val tok = ByteArray(16) { 9 }
    private val song = "{title: Kampvuurlied}\n{meta: id kampvuurlied}\n\nHet vuur brandt hoog.\n".toByteArray()
    private val ref = SongRef(LibraryId("zangbundel-nl"), SongId("kampvuurlied"), ContentHash(crypto.sha256(song).copyOf(16)))

    /** Adds an unknown key to an encoded map: decoders must ignore it (9.1). */
    private fun withUnknownKey(bytes: ByteArray): ByteArray {
        val m = Cbor.decode(bytes, 1 shl 20) as CborMap
        return Cbor.encode(CborMap(m.entries + (CborInt(99) to CborText("future"))))
    }

    /** Removes a key from an encoded map. */
    private fun without(bytes: ByteArray, key: Long): ByteArray {
        val m = Cbor.decode(bytes, 1 shl 20) as CborMap
        return Cbor.encode(CborMap(m.entries.filter { it.first != CborInt(key) }))
    }

    @Test
    fun `song references round-trip and refuse invalid ids`() {
        assertEquals(ref, SongRefCodec.fromCbor(SongRefCodec.toCbor(ref)))
        val bad = CborMap.of(1L to CborText("Bad Id"), 2L to CborText("x"), 3L to CborBytes(ByteArray(16)))
        assertFailsWith<PayloadException> { decoding("ref") { SongRefCodec.fromCbor(bad) } }
    }

    @Test
    fun `info round-trips and its key must match the gathering id`() {
        val keys = crypto.generateSigningKeyPair()
        val realGid = gatheringIdOf(keys.publicKey, crypto)
        val info = Info(1, 0, realGid, "Kampvuur 25 oktober", 0b10, 12, keys.publicKey, hops = 0, app = "Zangbundel 0.1.0", nsets = 3, ext = true, ek = ByteArray(32))
        val back = Info.decode(withUnknownKey(info.encode()))
        assertEquals("Kampvuur 25 oktober", back.name)
        assertEquals(3, back.nsets)
        assertTrue(back.pkMatchesGid(crypto))
        assertFalse(Info(1, 0, gid, "x", 0, 0, keys.publicKey).pkMatchesGid(crypto))
        assertFailsWith<PayloadException> { Info.decode(without(info.encode(), 7)) }
        assertEquals(40, Info(1, 0, gid, "k".repeat(50), 0, 0, ByteArray(32)).name.length)
    }

    @Test
    fun `song document verifies its file against the reference`() {
        val doc = SongDocument(ref, song, "Tekst: onbekend. CC0.")
        val back = SongDocument.decode(doc.encode())
        assertContentEquals(song, back.file)
        assertTrue(back.fileMatchesHash(crypto))
        assertFalse(SongDocument(ref, song + byteArrayOf(10)).fileMatchesHash(crypto))
        assertFailsWith<PayloadException> { SongDocument.decode(without(doc.encode(), 2)) }
    }

    @Test
    fun `deflated documents survive framing and come back through the cap`() {
        val doc = SongDocument(ref, song)
        val (enc, framed) = Deflate.frame(doc.encode())
        val back = SongDocument.decode(Deflate.unframe(enc, framed, PayloadLimits.DOCUMENT_DECODED))
        assertContentEquals(song, back.file)
    }

    @Test
    fun `setlist, message, reqstat and requests round-trip`() {
        val setlist = SetlistDocument(listOf(SetlistDocument.Item(ref, "Kampvuurlied", SetlistDocument.CURRENT, votes = 3)))
        assertEquals(3L, SetlistDocument.decode(withUnknownKey(setlist.encode())).items.single().votes)

        val message = MessageDocument("Nog één lied, dan naar bed.", 5000)
        assertEquals(message.text, MessageDocument.decode(message.encode()).text)

        val reqstat = ReqstatDocument(listOf(ReqstatDocument.Item(byteArrayOf(1, 2, 3, 4), ReqstatDocument.ACCEPTED, setpos = 2)), rseq = 8)
        val rs = ReqstatDocument.decode(reqstat.encode())
        assertEquals(8, rs.rseq)
        assertContentEquals(byteArrayOf(1, 2, 3, 4), rs.items.single().rid)

        val requests = RequestsDocument(
            listOf(RequestsDocument.Item(ByteArray(4), ByteArray(8), "Sam", text = "Het lied van de vos", status = 0, ts = 10, count = 2)),
            rseq = 8, open = true,
        )
        val rq = RequestsDocument.decode(requests.encode())
        assertEquals("Sam", rq.items.single().name)
        assertTrue(rq.open)
        assertNull(rq.items.single().ref)
    }

    @Test
    fun `join request and response round-trip`() {
        val join = JoinRequest(1, 0, ByteArray(8) { 7 }, name = "Zanger 12", caps = JoinRequest.CAP_LEGACY, gid = gid, xk = ByteArray(32))
        val back = JoinRequest.decode(withUnknownKey(join.encode()))
        assertEquals("Zanger 12", back.name)
        assertEquals(JoinRequest.CAP_LEGACY, back.caps)
        assertEquals(24, JoinRequest(1, 0, ByteArray(8), name = "x".repeat(30), gid = gid, xk = ByteArray(32)).name!!.length)

        val ok = JoinResponse(JoinResponse.ACCEPTED, tok, seq = 42, maxPending = 3)
        assertEquals(42, JoinResponse.decode(ok.encode()).seq)
        assertFailsWith<PayloadException> { JoinResponse.decode(without(ok.encode(), 2)) } // accepted without tok
        assertEquals(JoinResponse.FULL, JoinResponse.decode(JoinResponse(JoinResponse.FULL).encode()).result)
        assertFailsWith<PayloadException> { JoinResponse.decode(Cbor.encode(CborMap.of(1L to CborInt(6)))) }
    }

    @Test
    fun `request write needs a reference or a title to add`() {
        val r = RequestWrite(tok, RequestWrite.ADD, byteArrayOf(1, 2, 3, 4), text = "Het lied van de vos", gid = gid)
        assertEquals("Het lied van de vos", RequestWrite.decode(r.encode()).text)
        assertFailsWith<PayloadException> { RequestWrite.decode(without(r.encode(), 5)) }
        assertEquals(RequestWrite.WITHDRAW, RequestWrite.decode(RequestWrite(tok, RequestWrite.WITHDRAW, ByteArray(4), gid = gid).encode()).op)
    }

    @Test
    fun `transfer request and header round-trip with ranges`() {
        val t = TransferRequest(DocumentType.SONG, dv = 0x1234, hash = ref.hash.bytes, ranges = listOf(ByteRange(16, 16), ByteRange(48, 32)))
        val back = TransferRequest.decode(t.encode())
        assertEquals(listOf(ByteRange(16, 16), ByteRange(48, 32)), back.ranges)
        assertFailsWith<IllegalArgumentException> { TransferRequest(DocumentType.SONG, ranges = listOf()) }            // no hash for a song
        assertFailsWith<IllegalArgumentException> { TransferRequest(4, ranges = listOf(ByteRange(48, 16), ByteRange(16, 16))) }
        assertFailsWith<IllegalArgumentException> { TransferRequest(4, ranges = List(33) { ByteRange(it * 10, 1) }) }
        assertFailsWith<IllegalArgumentException> { TransferRequest(DocumentType.REQUESTS) }                          // needs tok and gid

        val h = TransferHeader(dv = 0x1234, len = 70, enc = TransferHeader.ENC_DEFLATE, dh = ByteArray(8), ranges = listOf(ByteRange(0, 70)))
        val hb = TransferHeader.decode(h.encode())
        assertEquals(70, hb.len)
        assertEquals(0x01, h.encode()[0].toInt())
        assertFailsWith<PayloadException> { TransferHeader.decode(byteArrayOf(0x02) + h.encode().copyOfRange(1, h.encode().size)) }
    }

    @Test
    fun `control commands carry what their op needs`() {
        val cid = byteArrayOf(1, 1, 1, 1)
        val push = ControlCommand(tok, cid, ControlCommand.PUSH_SONG, ref = ref, gid = gid)
        assertEquals(ref, ControlCommand.decode(push.encode()).ref)
        assertFailsWith<PayloadException> { ControlCommand.decode(without(push.encode(), 4)) }

        val helper = ControlCommand(tok, cid, ControlCommand.BECOME_DELEGATE, gid = gid, hc = "0427")
        assertEquals("0427", ControlCommand.decode(helper.encode()).hc)
        val fiveDigits = Cbor.encode(CborMap((Cbor.decode(helper.encode(), 512) as CborMap).entries.map {
            if (it.first == CborInt(10)) it.first to CborText("04271") else it
        }))
        assertFailsWith<PayloadException> { ControlCommand.decode(fiveDigits) }     // not cut to four digits

        val chunk = ControlCommand(tok, cid, ControlCommand.UPLOAD_CHUNK, idx = 0, data = ByteArray(432), len = 2000, gid = gid)
        assertEquals(432, ControlCommand.decode(chunk.encode()).data!!.size)
        assertFailsWith<IllegalArgumentException> { ControlCommand(tok, cid, ControlCommand.UPLOAD_CHUNK, idx = 0, data = ByteArray(433), len = 2000, gid = gid) }
        assertFailsWith<IllegalArgumentException> { ControlCommand(tok, cid, ControlCommand.UPLOAD_CHUNK, idx = 0, data = ByteArray(10), gid = gid) }
        assertFailsWith<PayloadException> { ControlCommand.decode(Cbor.encode(CborMap((Cbor.decode(push.encode(), 512) as CborMap).entries.map {
            if (it.first == CborInt(3)) it.first to CborInt(11) else it
        }))) }
    }

    @Test
    fun `wrong field sizes are refused`() {
        val join = JoinRequest(1, 0, ByteArray(8), gid = gid, xk = ByteArray(32))
        val shortSid = Cbor.encode(CborMap((Cbor.decode(join.encode(), 512) as CborMap).entries.map {
            if (it.first == CborInt(3)) it.first to CborBytes(ByteArray(7)) else it
        }))
        assertFailsWith<PayloadException> { JoinRequest.decode(shortSid) }
        assertFailsWith<PayloadException> { JoinRequest.decode(ByteArray(513)) }
    }

    @Test
    fun `random maps only ever fail with PayloadException`() {
        val random = Random(20261025)
        val decoders: List<(ByteArray) -> Any> = listOf(
            Info::decode, SongDocument::decode, SetlistDocument::decode, MessageDocument::decode, ReqstatDocument::decode,
            RequestsDocument::decode, JoinRequest::decode, JoinResponse::decode, RequestWrite::decode, TransferRequest::decode,
            ControlCommand::decode, StateBody::decode,
        )
        val values = listOf(
            CborInt(0), CborInt(1), CborInt(3), CborInt(-1), CborInt(70000), CborBytes(ByteArray(4)), CborBytes(ByteArray(5)),
            CborBytes(ByteArray(8)), CborBytes(ByteArray(16)), CborBytes(ByteArray(32)), CborText("vader-jacob"), CborText("X"),
            CborMap.of(1L to CborText("a"), 2L to CborText("b"), 3L to CborBytes(ByteArray(16))),
            nl.pe1mew.zangbundel.core.protocol.cbor.CborArray(CborInt(1)), nl.pe1mew.zangbundel.core.protocol.cbor.CborBool(true),
        )
        repeat(20_000) {
            val entries = (1..random.nextInt(0, 12)).map { CborInt(random.nextLong(1, 15)) to values.random(random) }.distinctBy { it.first }
            val bytes = Cbor.encode(CborMap(entries))
            for (decode in decoders) {
                try {
                    decode(bytes)
                } catch (e: PayloadException) {
                    // expected for most
                } catch (e: Throwable) {
                    fail("$entries threw ${e::class.simpleName}: ${e.message}")
                }
            }
        }
    }
}
