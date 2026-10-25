// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.payload

import nl.pe1mew.zangbundel.core.model.GatheringId
import nl.pe1mew.zangbundel.core.protocol.ProtocolConstants.ERR_MALFORMED
import nl.pe1mew.zangbundel.core.protocol.ProtocolConstants.ERR_SEALED
import nl.pe1mew.zangbundel.core.protocol.ProtocolError
import nl.pe1mew.zangbundel.core.testing.JvmSodiumCrypto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SealedTest {
    private val crypto = JvmSodiumCrypto()
    private val leader = crypto.generateBoxKeyPair()       // ek
    private val singer = crypto.generateBoxKeyPair()       // xk
    private val gid = GatheringId(byteArrayOf(1, 2, 3, 4, 5))
    private val tok = ByteArray(16) { 3 }

    private fun join(name: String = "Zanger 12", g: GatheringId = gid) =
        JoinRequest(1, 0, ByteArray(8) { 7 }, name = name, gid = g, xk = singer.publicKey)

    private fun code(block: () -> Unit): Int = assertFailsWith<ProtocolError> { block() }.code

    @Test
    fun `a sealed join opens only with the leader's key and keeps the name out of the bytes`() {
        val value = Sealed.join(join(), leader.publicKey, crypto)
        assertEquals(0x01, value[0].toInt())
        assertEquals(1 + 48 + join().encode().size, value.size)
        assertFalse(String(value, Charsets.ISO_8859_1).contains("Zanger"))
        assertEquals("Zanger 12", Sealed.openJoin(value, leader, gid, crypto).name)
        assertEquals(ERR_SEALED, code { Sealed.openJoin(value, crypto.generateBoxKeyPair(), gid, crypto) })
    }

    @Test
    fun `a tampered, truncated or unframed value cannot be opened`() {
        val value = Sealed.join(join(), leader.publicKey, crypto)
        val tampered = value.copyOf().also { it[it.size - 1] = (it.last() + 1).toByte() }
        assertEquals(ERR_SEALED, code { Sealed.openJoin(tampered, leader, gid, crypto) })
        assertEquals(ERR_SEALED, code { Sealed.openJoin(value.copyOf(40), leader, gid, crypto) })
        assertEquals(ERR_SEALED, code { Sealed.openJoin(ByteArray(0), leader, gid, crypto) })
        assertEquals(ERR_SEALED, code { Sealed.openJoin(byteArrayOf(0x02) + value.copyOfRange(1, value.size), leader, gid, crypto) })
        assertEquals(ERR_MALFORMED, code { Sealed.openJoin(byteArrayOf(0x00) + join().encode(), leader, gid, crypto) })
        assertEquals(ERR_MALFORMED, code { Sealed.openJoin(ByteArray(513).also { it[0] = 1 }, leader, gid, crypto) })
    }

    @Test
    fun `a payload bound to another gathering is refused`() {
        val value = Sealed.join(join(g = GatheringId(byteArrayOf(9, 9, 9, 9, 9))), leader.publicKey, crypto)
        assertEquals(ERR_MALFORMED, code { Sealed.openJoin(value, leader, gid, crypto) })
    }

    @Test
    fun `a sealed value that opens to garbage is malformed`() {
        val garbage = byteArrayOf(0x01) + crypto.seal(byteArrayOf(0x42), leader.publicKey)
        assertEquals(ERR_MALFORMED, code { Sealed.openControl(garbage, leader, gid, crypto) })
    }

    @Test
    fun `request and control round-trip`() {
        val r = RequestWrite(tok, RequestWrite.ADD, byteArrayOf(1, 2, 3, 4), text = "Het lied van de vos", gid = gid)
        assertEquals("Het lied van de vos", Sealed.openRequest(Sealed.request(r, leader.publicKey, crypto), leader, gid, crypto).text)
        val c = ControlCommand(tok, ByteArray(4), ControlCommand.LEAVE, gid = gid)
        assertEquals(ControlCommand.LEAVE, Sealed.openControl(Sealed.control(c, leader.publicKey, crypto), leader, gid, crypto).op)
    }

    @Test
    fun `the largest upload chunk still fits one sealed write`() {
        // Worst case: chunk 0, which also carries len, with len at the 64 kB maximum.
        fun write(n: Int) = Sealed.control(
            ControlCommand(tok, ByteArray(4), ControlCommand.UPLOAD_CHUNK, idx = 0, data = ByteArray(n), len = 65_536, gid = gid),
            leader.publicKey, crypto,
        )
        val value = write(ControlCommand.UPLOAD_CHUNK_MAX_BYTES)
        assertEquals(512, value.size)
        assertEquals(ControlCommand.UPLOAD_CHUNK_MAX_BYTES, Sealed.openControl(value, leader, gid, crypto).data!!.size)
        assertFailsWith<IllegalArgumentException> { write(ControlCommand.UPLOAD_CHUNK_MAX_BYTES + 1) }
    }

    @Test
    fun `transfer is plaintext except for document 8`() {
        val song = TransferRequest(DocumentType.SONG, hash = ByteArray(16))
        val plain = Sealed.transfer(song, null, crypto)
        assertEquals(0x00, plain[0].toInt())
        assertEquals(DocumentType.SONG, Sealed.openTransfer(plain, leader, gid, crypto).doc)

        val requests = TransferRequest(DocumentType.REQUESTS, tok = tok, gid = gid)
        val sealed = Sealed.transfer(requests, leader.publicKey, crypto)
        assertEquals(0x01, sealed[0].toInt())
        assertEquals(DocumentType.REQUESTS, Sealed.openTransfer(sealed, leader, gid, crypto).doc)

        assertEquals(ERR_MALFORMED, code { Sealed.openTransfer(byteArrayOf(0x00) + requests.encode(), leader, gid, crypto) })
        val sealedSong = byteArrayOf(0x01) + crypto.seal(song.encode(), leader.publicKey)
        assertEquals(ERR_MALFORMED, code { Sealed.openTransfer(sealedSong, leader, gid, crypto) })
        assertEquals(ERR_SEALED, code { Sealed.openTransfer(byteArrayOf(0x07), leader, gid, crypto) })
    }

    @Test
    fun `join response and document 8 go sealed to the singer`() {
        val response = JoinResponse(JoinResponse.ACCEPTED, tok, seq = 3, role = 1)
        val read = Sealed.joinResponse(response, singer.publicKey, crypto)
        assertEquals(1, Sealed.openJoinResponse(read, singer, crypto).role)
        assertFailsWith<ProtocolError> { Sealed.openJoinResponse(read, leader, crypto) }

        val doc = RequestsDocument(
            listOf(RequestsDocument.Item(ByteArray(4), ByteArray(8), "Sam", text = "Het lied van de vos", status = 0, ts = 1)),
            rseq = 1, open = true,
        )
        val (stream, dh) = Sealed.requestsStream(doc, singer.publicKey, crypto)
        assertFalse(String(stream, Charsets.ISO_8859_1).contains("Sam"))
        assertEquals("Sam", Sealed.openRequestsStream(stream, dh, singer, crypto).items.single().name)
        assertFailsWith<PayloadException> { Sealed.openRequestsStream(stream, ByteArray(8), singer, crypto) }
        assertFailsWith<PayloadException> { Sealed.openRequestsStream(stream, dh, leader, crypto) }
    }

    @Test
    fun `the sealed plaintext limit leaves room for the longest request`() {
        assertEquals(463, Sealed.MAX_SEALED_PLAINTEXT)
        val tooLong = RequestWrite(tok, RequestWrite.ADD, ByteArray(4), text = "x".repeat(100), gid = gid)
        assertTrue(Sealed.request(tooLong, leader.publicKey, crypto).size <= 512)
    }
}
