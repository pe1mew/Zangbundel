// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.gatt

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import nl.pe1mew.zangbundel.core.model.GatheringId
import nl.pe1mew.zangbundel.core.protocol.ProtocolConstants
import nl.pe1mew.zangbundel.core.protocol.Transport
import nl.pe1mew.zangbundel.core.protocol.engine.SlotPolicy
import nl.pe1mew.zangbundel.core.protocol.payload.ByteRange
import nl.pe1mew.zangbundel.core.protocol.payload.ControlCommand
import nl.pe1mew.zangbundel.core.protocol.payload.DocumentType
import nl.pe1mew.zangbundel.core.protocol.payload.JoinRequest
import nl.pe1mew.zangbundel.core.protocol.payload.JoinResponse
import nl.pe1mew.zangbundel.core.protocol.payload.RequestWrite
import nl.pe1mew.zangbundel.core.protocol.payload.Sealed
import nl.pe1mew.zangbundel.core.protocol.payload.TransferRequest
import nl.pe1mew.zangbundel.core.testing.JvmSodiumCrypto
import nl.pe1mew.zangbundel.core.testing.SimulatedAir
import nl.pe1mew.zangbundel.core.testing.SimulatedTransport
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(ExperimentalCoroutinesApi::class)
class LeaderGattServiceTest {
    private val crypto = JvmSodiumCrypto()
    private val gid = GatheringId(byteArrayOf(1, 2, 3, 4, 5))
    private val ek = crypto.generateBoxKeyPair()
    private val song = Random(3).nextBytes(2000)
    private val songHash = ByteArray(16) { 7 }

    private class Setup(val service: LeaderGattService, val leader: SimulatedTransport, val singer: SimulatedTransport)

    private suspend fun TestScope.setup(maxSingers: Int = 100, code: String? = null, slots: Int = 6): Setup {
        val air = SimulatedAir(backgroundScope)
        val leader = SimulatedTransport(air)
        val service = LeaderGattService(gid, ek, crypto, backgroundScope, { testScheduler.currentTime }, SlotPolicy(slots), maxSingers, code)
        service.publish(byteArrayOf(0x11), byteArrayOf(0x22), 77, listOf(
            LeaderGattService.Served(DocumentType.SONG, 0x0A0B, song, 0, songHash, crypto),
        ))
        leader.startServer(service)
        return Setup(service, leader, SimulatedTransport(air))
    }

    private fun joinRequest(sid: Byte, xk: ByteArray, code: String? = null, major: Int = 1) =
        JoinRequest(major, 0, ByteArray(8) { sid }, name = "Sanne", code = code, gid = gid, xk = xk)

    private suspend fun Setup.join(sid: Byte, code: String? = null, major: Int = 1): JoinResponse {
        val xk = crypto.generateBoxKeyPair()
        val client = GattClient(singer.connect(leader.address), crypto)
        return client.join(joinRequest(sid, xk.publicKey, code, major), ek.publicKey, xk).also { client.close() }
    }

    @Test
    fun `join is accepted, a rejoin keeps the token and is counted once`() = runTest {
        val s = setup()
        val first = s.join(1)
        assertEquals(JoinResponse.ACCEPTED, first.result)
        assertEquals(77, first.seq)
        val again = s.join(1)
        assertContentEquals(first.tok, again.tok)
        assertEquals(1, s.service.singerCount)
        s.join(2)
        assertEquals(2, s.service.singerCount)
    }

    @Test
    fun `wrong code, full table and another major version`() = runTest {
        val s = setup(maxSingers = 1, code = "4711")
        assertEquals(JoinResponse.WRONG_CODE, s.join(1, code = "1234").result)
        assertEquals(JoinResponse.ACCEPTED, s.join(1, code = "4711").result)
        assertEquals(JoinResponse.FULL, s.join(2, code = "4711").result)
        assertEquals(JoinResponse.VERSION_NOT_SUPPORTED, s.join(3, code = "4711", major = 2).result)
    }

    @Test
    fun `a plaintext join is refused as malformed, garbage as unopenable`() = runTest {
        val s = setup()
        val c = s.singer.connect(s.leader.address)
        val plain = byteArrayOf(Sealed.PLAINTEXT) + joinRequest(1, ByteArray(32)).encode()
        assertEquals(0x83, refused { c.write(ProtocolConstants.CHAR_JOIN, plain) })
        assertEquals(0x8D, refused { c.write(ProtocolConstants.CHAR_JOIN, byteArrayOf(1) + ByteArray(80)) })
    }

    @Test
    fun `a transfer serves the clamped ranges of the current song`() = runTest {
        val s = setup()
        val client = GattClient(s.singer.connect(s.leader.address), crypto)
        val t = client.transfer(TransferRequest(DocumentType.SONG, 0x0A0B, songHash, listOf(ByteRange(0, 16), ByteRange(1990, 100))))
        assertIs<GattClient.Transfer.Done>(t)
        assertEquals(2000, t.header.len)
        assertEquals(listOf(ByteRange(0, 16), ByteRange(1990, 10)), t.ranges.map { it.first })
        assertContentEquals(song.copyOfRange(1990, 2000), t.ranges[1].second)
        val whole = client.transfer(TransferRequest(DocumentType.SONG, null, songHash))
        assertContentEquals(song, assertIs<GattClient.Transfer.Done>(whole).ranges.single().second)
    }

    @Test
    fun `unknown version, wrong hash and a range past the end are refused with their codes`() = runTest {
        val s = setup()
        val client = GattClient(s.singer.connect(s.leader.address), crypto)
        suspend fun code(r: TransferRequest) = assertIs<GattClient.Transfer.Failed>(client.transfer(r)).code
        assertEquals(0x86, code(TransferRequest(DocumentType.SONG, 0x9999, songHash)))
        assertEquals(0x86, code(TransferRequest(DocumentType.SONG, 0x0A0B, ByteArray(16))))
        assertEquals(0x86, code(TransferRequest(DocumentType.SETLIST, null)))
        assertEquals(0x8C, code(TransferRequest(DocumentType.SONG, 0x0A0B, songHash, listOf(ByteRange(2000, 16)))))
    }

    @Test
    fun `the previous version is still served after a push, the one before is gone`() = runTest {
        val s = setup()
        val v2 = Random(4).nextBytes(100)
        s.service.publish(byteArrayOf(1), byteArrayOf(2), 78, listOf(LeaderGattService.Served(DocumentType.SONG, 0x0C0D, v2, 0, songHash, crypto)))
        s.service.publish(byteArrayOf(1), byteArrayOf(2), 79, listOf(LeaderGattService.Served(DocumentType.SONG, 0x0E0F, v2, 0, songHash, crypto)))
        val client = GattClient(s.singer.connect(s.leader.address), crypto)
        assertIs<GattClient.Transfer.Done>(client.transfer(TransferRequest(DocumentType.SONG, 0x0C0D, songHash)))
        assertEquals(0x86, assertIs<GattClient.Transfer.Failed>(client.transfer(TransferRequest(DocumentType.SONG, 0x0A0B, songHash))).code)
    }

    @Test
    fun `info and state are read in clear`() = runTest {
        val s = setup()
        val client = GattClient(s.singer.connect(s.leader.address), crypto)
        assertContentEquals(byteArrayOf(0x11), client.readInfo())
        assertContentEquals(byteArrayOf(0x22), client.readState())
    }

    @Test
    fun `requests are closed, an unknown token is refused, leave removes the singer`() = runTest {
        val s = setup()
        val tok = s.join(1).tok!!
        val c = s.singer.connect(s.leader.address)
        fun req(t: ByteArray) = Sealed.request(RequestWrite(t, RequestWrite.ADD, ByteArray(4), text = "Lied", gid = gid), ek.publicKey, crypto)
        fun ctl(t: ByteArray, op: Int) = Sealed.control(ControlCommand(t, ByteArray(4), op, gid = gid), ek.publicKey, crypto)
        assertEquals(0x81, refused { c.write(ProtocolConstants.CHAR_REQUEST, req(tok)) })
        assertEquals(0x80, refused { c.write(ProtocolConstants.CHAR_REQUEST, req(ByteArray(16))) })
        assertEquals(0x89, refused { c.write(ProtocolConstants.CHAR_CONTROL, ctl(tok, ControlCommand.CLEAR_SONG)) })
        c.write(ProtocolConstants.CHAR_CONTROL, ctl(tok, ControlCommand.LEAVE))
        assertEquals(0, s.service.singerCount)
    }

    @Test
    fun `after the end every write is refused with 0x88`() = runTest {
        val s = setup()
        s.service.end()
        val client = GattClient(s.singer.connect(s.leader.address), crypto)
        assertEquals(0x88, assertIs<GattClient.Transfer.Failed>(client.transfer(TransferRequest(DocumentType.SONG, null, songHash))).code)
    }

    @Test
    fun `a connection beyond max_conn is dropped, and BUSY follows the open count`() = runTest {
        val busy = ArrayList<Boolean>()
        val air = SimulatedAir(backgroundScope)
        val leader = SimulatedTransport(air)
        val service = LeaderGattService(gid, ek, crypto, backgroundScope, { testScheduler.currentTime }, SlotPolicy(2), onBusyChanged = { busy += it })
        service.publish(byteArrayOf(1), byteArrayOf(2), 1, emptyList())
        leader.startServer(service)
        val singer = SimulatedTransport(air)
        val a = singer.connect(leader.address)
        assertEquals(listOf(true), busy)                      // open ≥ max_conn − 1
        val b = singer.connect(leader.address)
        val third = singer.connect(leader.address)            // the stack takes it; the service does not
        testScheduler.runCurrent()
        assertEquals(Transport.CONNECTION_FAILED, refused { third.write(ProtocolConstants.CHAR_TRANSFER, byteArrayOf(0)) })
        a.close()
        assertEquals(listOf(true), busy)
        b.close()
        assertEquals(listOf(true, false), busy)
    }

    private suspend fun refused(block: suspend () -> Unit): Int {
        try { block() } catch (e: Transport.GattException) { return e.status }
        fail("expected the write to be refused")
    }

    @Test
    fun `random writes never make it throw`() = runTest {
        val s = setup()
        val c = s.singer.connect(s.leader.address)
        val random = Random(20261025)
        repeat(3000) {
            val value = random.nextBytes(random.nextInt(0, 600))
            if (value.isNotEmpty()) value[0] = random.nextInt(0, 3).toByte()
            val ch = listOf(ProtocolConstants.CHAR_JOIN, ProtocolConstants.CHAR_TRANSFER, ProtocolConstants.CHAR_REQUEST, ProtocolConstants.CHAR_CONTROL, 0x55)[random.nextInt(5)]
            try { c.write(ch, value) } catch (e: Transport.GattException) { assertTrue(e.status > 0) }
        }
    }
}
