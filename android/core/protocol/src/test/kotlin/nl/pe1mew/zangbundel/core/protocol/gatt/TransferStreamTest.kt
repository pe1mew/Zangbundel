// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.gatt

import nl.pe1mew.zangbundel.core.protocol.payload.ByteRange
import nl.pe1mew.zangbundel.core.protocol.payload.TransferHeader
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class TransferStreamTest {
    private val stream = Random(1).nextBytes(2000)
    private fun header(ranges: List<ByteRange>) = TransferHeader(0x1234, stream.size, 0, ByteArray(8), ranges)

    private fun receive(notifications: List<ByteArray>): TransferStream.Receiver {
        val r = TransferStream.Receiver(64 * 1024)
        notifications.forEachIndexed { i, n -> if (r.accept(n) && i != notifications.lastIndex) fail("ended early at $i") }
        return r
    }

    @Test
    fun `the requested ranges arrive in chunks of MTU minus 6, at the smallest and a typical MTU`() {
        val ranges = listOf(ByteRange(16, 16), ByteRange(480, 33), ByteRange(1984, 16))
        for (mtu in listOf(23, 185, 517)) {
            val ns = TransferStream.notifications(header(ranges), stream, mtu)
            assertTrue(ns.drop(1).all { it.size <= minOf(mtu - 3, 512) }, "no DATA or END over MTU − 3 or 512 bytes")
            // The HEADER fits from MTU 185; at MTU 23 it does not (ble-protocol Q22, open).
            if (mtu >= 185) assertTrue(ns[0].size <= mtu - 3, "HEADER of ${ns[0].size} bytes at MTU $mtu")
            val got = receive(ns).result()!!
            assertEquals(ranges, got.map { it.first })
            got.forEach { (r, b) -> assertContentEquals(stream.copyOfRange(r.off, r.off + r.len), b) }
        }
    }

    @Test
    fun `a whole document and an empty one`() {
        assertContentEquals(stream, receive(TransferStream.notifications(header(listOf(ByteRange(0, 2000))), stream, 185)).result()!!.single().second)
        val empty = TransferStream.notifications(TransferHeader(1, 0, 0, ByteArray(8), listOf(ByteRange(0, 0))), ByteArray(0), 185)
        assertEquals(0, receive(empty).result()!!.single().second.size)
    }

    @Test
    fun `an ERROR ends the transfer with its code`() {
        val r = TransferStream.Receiver(1000)
        assertTrue(r.accept(TransferStream.error(0x86)))
        assertEquals(0x86, r.errorCode)
        assertNull(r.result())
    }

    @Test
    fun `chunks out of order, extra bytes or a wrong count fail it`() {
        val ns = TransferStream.notifications(header(listOf(ByteRange(0, 500))), stream, 23)
        val swapped = ns.toMutableList().also { val t = it[1]; it[1] = it[2]; it[2] = t }
        val extra = ns.toMutableList().also { it.add(it.size - 1, byteArrayOf(TransferStream.DATA, 0, 99, 1)) }
        val wrongEnd = ns.dropLast(1) + byteArrayOf(TransferStream.END, 0, 1)
        for (bad in listOf(swapped, extra, wrongEnd)) {
            val r = TransferStream.Receiver(64 * 1024)
            bad.forEach { r.accept(it) }
            assertEquals(0x83, r.errorCode)
            assertNull(r.result())
        }
    }

    @Test
    fun `a header announcing more than the receiver takes fails at once`() {
        val r = TransferStream.Receiver(100)
        assertTrue(r.accept(header(listOf(ByteRange(0, 2000))).encode()))
        assertEquals(0x83, r.errorCode)
    }

    @Test
    fun `random notifications never make it throw`() {
        val random = Random(20261025)
        repeat(20_000) {
            val r = TransferStream.Receiver(4096)
            repeat(random.nextInt(1, 6)) {
                val n = random.nextBytes(random.nextInt(0, 40))
                if (n.isNotEmpty()) n[0] = listOf(0x01, 0x02, 0x03, 0x7F, 0x55)[random.nextInt(5)].toByte()
                r.accept(n)
            }
            r.result()
        }
    }
}
