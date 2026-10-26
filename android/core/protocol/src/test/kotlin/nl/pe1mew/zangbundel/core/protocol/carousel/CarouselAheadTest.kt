// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.carousel

import nl.pe1mew.zangbundel.core.protocol.carousel.Carousel.Document
import nl.pe1mew.zangbundel.core.protocol.carousel.Carousel.Lane
import nl.pe1mew.zangbundel.core.protocol.frame.Frame
import nl.pe1mew.zangbundel.core.protocol.payload.DocumentType.AHEAD
import nl.pe1mew.zangbundel.core.protocol.payload.DocumentType.INFO
import nl.pe1mew.zangbundel.core.protocol.payload.DocumentType.SETLIST
import nl.pe1mew.zangbundel.core.protocol.payload.DocumentType.SONG
import nl.pe1mew.zangbundel.core.protocol.payload.DocumentType.STATE
import nl.pe1mew.zangbundel.core.protocol.payload.SetlistDocument
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Songs ahead in the carousel (ble-protocol 7.4 rule 7, v0.32) and in the setlist document (9.5 key 2). */
class CarouselAheadTest {
    private val gid16 = 0x3B7D
    private fun bytes(n: Int, seed: Int) = Random(seed).nextBytes(n)

    private fun documents(song: Boolean) = listOfNotNull(
        Document(STATE, 1, bytes(240, 1)),
        Document(INFO, 0x1111, bytes(150, 2)),
        if (song) Document(SONG, 0x2001, bytes(2000, 3)) else null,
        Document(SETLIST, 0x3333, bytes(300, 4)),
    )

    private val ahead = listOf(Document(AHEAD, 0xA001, bytes(1000, 11)), Document(AHEAD, 0xA002, bytes(1200, 12)))

    private fun Carousel.take(lane: Lane, n: Int): List<Frame> = List(n) { next(lane)!! }

    /** The documents in the order a lane sends them, consecutive frames of one version taken as one. */
    private fun runs(frames: List<Frame>): List<Pair<Int, Int>> =
        frames.map { it.doc to it.dv }.fold(mutableListOf()) { acc, d -> if (acc.lastOrNull() != d) acc += d; acc }

    private fun Carousel.drainBursts() { for (lane in lanes) take(lane, burstRemaining(lane)) }

    @Test
    fun `the large sets alternate the current song, the setlist and one song ahead`() {
        val c = Carousel(gid16, 3, extended = false)
        c.publish(documents(song = true), ahead)
        c.drainBursts()
        val loop = runs(c.take(Lane.Large(1), 600))
        assertEquals(listOf(SONG to 0x2001, SETLIST to 0x3333, AHEAD to 0xA001, SONG to 0x2001, SETLIST to 0x3333, AHEAD to 0xA002), loop.take(6))
    }

    @Test
    fun `before the first push the large sets carry the setlist and the songs ahead only`() {
        val c = Carousel(gid16, 3, extended = false)
        c.publish(documents(song = false), ahead)
        c.drainBursts()
        assertEquals(listOf(SETLIST to 0x3333, AHEAD to 0xA001, SETLIST to 0x3333, AHEAD to 0xA002), runs(c.take(Lane.Large(1), 300)).take(4))
    }

    @Test
    fun `bursts never contain songs ahead`() {
        val c = Carousel(gid16, 3, extended = true)
        c.publish(documents(song = true), ahead)
        val burst = c.lanes.flatMap { lane -> c.take(lane, c.burstRemaining(lane)) }
        assertTrue(burst.isNotEmpty() && burst.none { it.doc == AHEAD })
    }

    @Test
    fun `one legacy set and the extended set also carry every song ahead`() {
        for (c in listOf(Carousel(gid16, 1, extended = false), Carousel(gid16, 1, extended = true))) {
            c.publish(documents(song = true), ahead)
            c.drainBursts()
            val lane = c.lanes.last()
            val sent = c.take(lane, 3000).filter { it.doc == AHEAD }.map { it.dv }.toSet()
            assertEquals(setOf(0xA001, 0xA002), sent, "$lane")
        }
    }

    @Test
    fun `songs ahead are given apart from the other documents`() {
        val c = Carousel(gid16, 3, extended = false)
        assertFailsWith<IllegalArgumentException> { c.publish(documents(song = true) + ahead.first()) }
        assertFailsWith<IllegalArgumentException> { c.publish(documents(song = true), listOf(Document(SONG, 0x2002, bytes(10, 5)))) }
    }

    @Test
    fun `the setlist lists the songs ahead by position, empty for a song not broadcast`() {
        val dh = bytes(8, 7)
        val setlist = SetlistDocument(emptyList(), listOf(dh, ByteArray(0)))
        val back = SetlistDocument.decode(setlist.encode())
        assertEquals(2, back.ahead.size)
        assertTrue(back.ahead[0].contentEquals(dh) && back.ahead[1].isEmpty())
        assertTrue(SetlistDocument.decode(SetlistDocument(emptyList()).encode()).ahead.isEmpty(), "key 2 is optional")
        assertFailsWith<IllegalArgumentException> { SetlistDocument(emptyList(), listOf(ByteArray(5))) }
    }

    @Test
    fun `the state carries the current position and the positions sung (v0_35)`() {
        val bitmap = nl.pe1mew.zangbundel.core.protocol.payload.StateBody.sungBitmap(setOf(1, 3, 9))!!
        assertEquals(2, bitmap.size)
        val body = nl.pe1mew.zangbundel.core.protocol.payload.StateBody(
            gid = nl.pe1mew.zangbundel.core.model.GatheringId(ByteArray(5)), seq = 1, ts = 1, status = 0, flags = 0, rseq = 0,
            ek = ByteArray(32), cpos = 3, sung = bitmap,
        )
        val back = nl.pe1mew.zangbundel.core.protocol.payload.StateBody.decode(body.encode())
        assertEquals(3, back.cpos)
        assertEquals(listOf(1, 3, 9), (1..16).filter { back.wasSung(it) })
        assertTrue(SetlistDocument(emptyList(), listOf(bytes(8, 1))).encode().size < 20, "no items key when there are none")
    }
}
