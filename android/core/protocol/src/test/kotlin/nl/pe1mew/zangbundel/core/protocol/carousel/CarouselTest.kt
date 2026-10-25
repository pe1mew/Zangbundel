// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.carousel

import nl.pe1mew.zangbundel.core.protocol.carousel.Carousel.Document
import nl.pe1mew.zangbundel.core.protocol.carousel.Carousel.Lane
import nl.pe1mew.zangbundel.core.protocol.frame.Carrier
import nl.pe1mew.zangbundel.core.protocol.frame.Frame
import nl.pe1mew.zangbundel.core.protocol.frame.Reassembler
import nl.pe1mew.zangbundel.core.protocol.payload.DocumentType.INFO
import nl.pe1mew.zangbundel.core.protocol.payload.DocumentType.MESSAGE
import nl.pe1mew.zangbundel.core.protocol.payload.DocumentType.REQSTAT
import nl.pe1mew.zangbundel.core.protocol.payload.DocumentType.SETLIST
import nl.pe1mew.zangbundel.core.protocol.payload.DocumentType.SONG
import nl.pe1mew.zangbundel.core.protocol.payload.DocumentType.STATE
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CarouselTest {
    private val gid16 = 0x3B7D
    private fun bytes(n: Int, seed: Int) = Random(seed).nextBytes(n)

    /** A typical gathering: state 240 B (15 legacy frames), info, a 2 kB song (125 frames), setlist, reqstat, message. */
    private fun documents(seq: Int = 1, songSeed: Int = 1, songSize: Int = 2000) = listOf(
        Document(STATE, seq, bytes(240, 100 + seq)),
        Document(INFO, 0x1111, bytes(150, 2)),
        Document(SONG, 0x2000 + songSeed, bytes(songSize, songSeed)),
        Document(SETLIST, 0x3333, bytes(600, 4)),
        Document(REQSTAT, 0x4444, bytes(100, 5)),
        Document(MESSAGE, 0x5555, bytes(80, 6)),
    )

    private fun Carousel.take(lane: Lane, n: Int): List<Frame> = List(n) { next(lane)!! }

    @Test
    fun `sets follow the configuration`() {
        assertEquals(listOf(Lane.Small), Carousel(gid16, 1, extended = false).lanes)
        assertEquals(listOf(Lane.Small, Lane.Large(1), Lane.Large(2), Lane.Extended), Carousel(gid16, 3, extended = true).lanes)
        assertNull(Carousel(gid16, 1, extended = false).next(Lane.Small))
    }

    @Test
    fun `a burst sends the changed documents three times, state first, shared over the legacy sets`() {
        val c = Carousel(gid16, 3, extended = false)
        c.publish(documents(seq = 1))
        repeat(1000) { for (lane in c.lanes) c.next(lane) }
        val next = documents(seq = 2).map { if (it.doc == MESSAGE) Document(MESSAGE, 0x5556, bytes(80, 9)) else it }
        c.publish(next)                                                       // state (15 frames) and message (5) changed
        // Set S takes the state; the message's 5 frames go to the emptiest sets: D1 3, D2 2.
        assertEquals(3 * 15, c.burstRemaining(Lane.Small))
        assertEquals(3 * 3, c.burstRemaining(Lane.Large(1)))
        assertEquals(3 * 2, c.burstRemaining(Lane.Large(2)))
        assertTrue(c.take(Lane.Small, 45).all { it.doc == STATE && it.dv == 2 })
        assertTrue(c.take(Lane.Large(1), 9).all { it.doc == MESSAGE })
        assertEquals(0, c.burstRemaining(Lane.Small))
        assertEquals(STATE, c.next(Lane.Small)!!.doc)                       // the normal loop starts with the state
    }

    @Test
    fun `a new state alone bursts only the state`() {
        val c = Carousel(gid16, 3, extended = true)
        c.publish(documents(seq = 1))
        c.publish(documents(seq = 2))                                         // only seq changed
        assertEquals(3 * 15, c.burstRemaining(Lane.Small))
        assertEquals(3 * 2, c.burstRemaining(Lane.Extended))
        assertEquals(0, c.burstRemaining(Lane.Large(1)))
        assertTrue(c.take(Lane.Small, 45).all { it.doc == STATE && it.dv == 2 })
    }

    @Test
    fun `in the normal loop two large sets share the song, even and odd frames`() {
        val c = Carousel(gid16, 3, extended = false)
        c.publish(documents())
        for (lane in c.lanes) c.take(lane, c.burstRemaining(lane))
        val d1 = c.take(Lane.Large(1), 63).filter { it.doc == SONG }
        val d2 = c.take(Lane.Large(2), 62).filter { it.doc == SONG }
        assertTrue(d1.all { it.idx % 2 == 0 } && d2.all { it.idx % 2 == 1 })
        assertEquals((0 until 125).toSet(), (d1 + d2).map { it.idx }.toSet())
    }

    @Test
    fun `one legacy set carries everything with a state frame after every 8 document frames`() {
        val c = Carousel(gid16, 1, extended = false)
        c.publish(documents())
        c.take(Lane.Small, c.burstRemaining(Lane.Small))
        val loop = c.take(Lane.Small, 15 + 9 * 20)
        assertTrue(loop.take(15).all { it.doc == STATE })
        val rest = loop.drop(15)
        for (k in 0 until 20) assertEquals(STATE, rest[k * 9 + 8].doc, "state frame after document frames ${k * 8 + 1}..${k * 8 + 8}")
        assertTrue(rest.withIndex().filter { (i, _) -> i % 9 != 8 }.none { it.value.doc == STATE })
    }

    @Test
    fun `the extended set repeats the whole state record after every 8 document frames`() {
        val c = Carousel(gid16, 1, extended = true)
        c.publish(documents())
        c.take(Lane.Extended, c.burstRemaining(Lane.Extended))
        val loop = c.take(Lane.Extended, 40).map { it.doc }
        assertEquals(listOf(STATE, STATE), loop.take(2))
        assertEquals(listOf(STATE, STATE), loop.subList(10, 12))           // 2 state + 8 document frames, then the state again
    }

    @Test
    fun `documents too large for the legacy carrier go out on the extended set only`() {
        val c = Carousel(gid16, 2, extended = true)
        c.publish(documents(songSize = 5000))
        val legacy = c.take(Lane.Large(1), 300)
        assertTrue(legacy.none { it.doc == SONG })
        assertTrue(c.take(Lane.Extended, 100).any { it.doc == SONG })
        assertNull(c.songPassTimeMs())
    }

    @Test
    fun `the download document is never sent`() {
        val c = Carousel(gid16, 1, extended = true)
        c.publish(documents() + Document(6, 0x6666, bytes(50, 7)))
        assertTrue((c.take(Lane.Small, 2000) + c.take(Lane.Extended, 500)).none { it.doc == 6 })
    }

    @Test
    fun `pass time follows the frame counts`() {
        fun pt(sets: Int) = Carousel(gid16, sets, extended = false).also { it.publish(documents()) }.songPassTimeMs()
        assertEquals(14_000L, pt(1))   // 125 frames + 15 state frames
        assertEquals(12_500L, pt(2))   // 125 frames on D1
        assertEquals(6_300L, pt(3))    // 63 frames on each of D1 and D2
    }

    /**
     * Ticks of 100 ms until a singer without the song has both the new state and the song, with no
     * loss, receiving every frame of every set it listens to. Compare ble-protocol 7.7 and 12.1.
     */
    private fun deliveryMs(legacySets: Int, extended: Boolean): Long {
        val c = Carousel(gid16, legacySets, extended)
        c.publish(documents(seq = 1, songSeed = 1))
        repeat(500) { for (lane in c.lanes) c.next(lane) }                    // steady state before the push
        c.publish(documents(seq = 2, songSeed = 2))                            // push a new song
        val carrier = if (extended) Carrier.EXTENDED else Carrier.LEGACY
        val singer = Reassembler(gid16, carrier)
        val lanes = c.lanes.filter { (it == Lane.Extended) == extended }
        var haveState = false
        var haveSong = false
        for (tick in 1..10_000) {
            for (lane in lanes) {
                val r = singer.accept(c.next(lane)!!)
                if (r is Reassembler.Result.Complete && r.doc == STATE && r.dv == 2) haveState = true
                if (r is Reassembler.Result.Complete && r.doc == SONG && r.dv == 0x2002) haveSong = true
            }
            if (haveState && haveSong) return tick * Carousel.ADVERTISING_INTERVAL_MS
        }
        error("never complete")
    }

    @Test
    fun `delivery of a pushed 2 kB song, no loss`() {
        assertEquals(1_100L, deliveryMs(1, extended = true))   // 2 state + 9 song frames
        assertEquals(4_700L, deliveryMs(3, extended = false))  // 15 state + 125 song frames shared over 3 sets: 47 each
        assertEquals(7_000L, deliveryMs(2, extended = false))  // 140 frames over 2 sets
        assertEquals(14_000L, deliveryMs(1, extended = false)) // 15 state + 125 song frames on one set
    }
}
