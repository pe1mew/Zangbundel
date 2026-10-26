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

    /**
     * A typical gathering. Legacy frames, source + repair (7.3): state 240 B 15 + 30, info 10 + 20,
     * song 2 kB 125 + 131, setlist 38 + 76, reqstat 7 + 14, message 5 + 10.
     */
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
    fun `a burst opens with the state's lead-in of distinct frames, then the rest three times`() {
        val c = Carousel(gid16, 3, extended = false)
        c.publish(documents(seq = 1))
        repeat(1000) { for (lane in c.lanes) c.next(lane) }
        val next = documents(seq = 2).map { if (it.doc == MESSAGE) Document(MESSAGE, 0x5556, bytes(80, 9)) else it }
        c.publish(next)                                   // state (k = 15, n = 45) and message (15) changed
        // v0.34: the lead-in is 3 × 45 = 135 distinct state frames, 45 per set; then the message's 15 frames, three times.
        assertEquals(listOf(45 + 15, 45 + 15, 45 + 15), listOf(Lane.Small, Lane.Large(1), Lane.Large(2)).map { c.burstRemaining(it) })
        assertTrue(c.take(Lane.Small, 15).all { it.doc == STATE && it.dv == 2 && it.isSource })
        val leadIn = c.take(Lane.Small, 30) + c.take(Lane.Large(1), 45) + c.take(Lane.Large(2), 45)
        assertTrue(leadIn.all { it.doc == STATE && it.dv == 2 && !it.isSource })
        assertEquals(120, leadIn.map { it.idx }.toSet().size, "every lead-in frame is a different one")
        assertTrue(leadIn.any { it.idx >= 45 }, "past the usual n")
        val rest = c.take(Lane.Small, 5) + c.take(Lane.Large(1), 5) + c.take(Lane.Large(2), 5)
        assertTrue(rest.all { it.doc == MESSAGE })
    }

    @Test
    fun `in a burst the setlist goes before the song`() {
        val c = Carousel(gid16, 3, extended = false)
        c.publish(documents(seq = 1))
        repeat(1000) { for (lane in c.lanes) c.next(lane) }
        val next = documents(seq = 2, songSeed = 2).map { if (it.doc == SETLIST) Document(SETLIST, 0x3334, bytes(600, 8)) else it }
        c.publish(next)
        val burst = c.lanes.flatMap { lane -> c.take(lane, c.burstRemaining(lane)) }
        val firstSetlist = burst.indexOfFirst { it.doc == SETLIST }
        val firstSong = burst.indexOfFirst { it.doc == SONG }
        assertTrue(firstSetlist in 0 until firstSong, "setlist at $firstSetlist, song at $firstSong")
    }

    @Test
    fun `a new state alone bursts only the state, its repair frames on the other sets`() {
        val c = Carousel(gid16, 3, extended = true)
        c.publish(documents(seq = 1))
        c.publish(documents(seq = 2))                      // only seq changed
        assertEquals(listOf(45, 45, 45, 18), c.lanes.map { c.burstRemaining(it) })   // 45 legacy frames per pass; 6 extended
        assertTrue(c.lanes.flatMap { c.take(it, 15) }.all { it.doc == STATE && it.dv == 2 })
    }

    @Test
    fun `in the normal loop two large sets share the song, even and odd frames`() {
        val c = Carousel(gid16, 3, extended = false)
        c.publish(documents())
        for (lane in c.lanes) c.take(lane, c.burstRemaining(lane))
        val d1 = c.take(Lane.Large(1), 128)
        val d2 = c.take(Lane.Large(2), 128)
        assertTrue(d1.all { it.doc == SONG && it.idx % 2 == 0 } && d2.all { it.doc == SONG && it.idx % 2 == 1 })
        assertEquals((0 until 256).toSet(), (d1 + d2).map { it.idx }.toSet())
    }

    @Test
    fun `one legacy set carries everything with a state frame after every 8 document frames`() {
        val c = Carousel(gid16, 1, extended = false)
        c.publish(documents())
        c.take(Lane.Small, c.burstRemaining(Lane.Small))
        val loop = c.take(Lane.Small, 45 + 9 * 20)
        assertTrue(loop.take(45).all { it.doc == STATE })
        val rest = loop.drop(45)
        for (k in 0 until 20) assertEquals(STATE, rest[k * 9 + 8].doc, "state frame after document frames ${k * 8 + 1}..${k * 8 + 8}")
        assertTrue(rest.withIndex().filter { (i, _) -> i % 9 != 8 }.none { it.value.doc == STATE })
        assertEquals(20, rest.filter { it.doc == STATE }.map { it.idx }.toSet().size)        // inserted frames cycle through the code
    }

    @Test
    fun `the extended set repeats k state frames after every 8 document frames`() {
        val c = Carousel(gid16, 1, extended = true)
        c.publish(documents())
        c.take(Lane.Extended, c.burstRemaining(Lane.Extended))
        val loop = c.take(Lane.Extended, 40)
        assertEquals(listOf(STATE, STATE), loop.take(2).map { it.doc })
        assertEquals(listOf(STATE, STATE), loop.subList(10, 12).map { it.doc })       // 2 state + 8 document frames, then 2 more
        assertTrue(loop[0].idx != loop[10].idx)                                        // different coded frames each time
    }

    @Test
    fun `documents too large for the legacy carrier go out on the extended set only`() {
        val c = Carousel(gid16, 2, extended = true)
        c.publish(documents(songSize = 5000))
        assertTrue(c.take(Lane.Large(1), 300).none { it.doc == SONG })
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
    fun `pass time covers all coded frames of the song`() {
        fun pt(sets: Int) = Carousel(gid16, sets, extended = false).also { it.publish(documents()) }.songPassTimeMs()
        assertEquals(28_800L, pt(1))   // 256 frames + 32 state frames
        assertEquals(25_600L, pt(2))   // 256 frames on D1
        assertEquals(12_800L, pt(3))   // 128 frames on each of D1 and D2
    }

    /**
     * Ticks of 100 ms until a singer without the song has both the new state and the song, receiving
     * each frame with probability [reception]. Compare ble-protocol 7.7 and 12.1.
     */
    private fun deliveryMs(legacySets: Int, extended: Boolean, reception: Double = 1.0, random: Random = Random(1)): Long {
        val c = Carousel(gid16, legacySets, extended)
        c.publish(documents(seq = 1, songSeed = 1))
        repeat(500) { for (lane in c.lanes) c.next(lane) }
        c.publish(documents(seq = 2, songSeed = 2))
        val singer = Reassembler(gid16, if (extended) Carrier.EXTENDED else Carrier.LEGACY)
        val lanes = c.lanes.filter { (it == Lane.Extended) == extended }
        var haveState = false
        var haveSong = false
        for (tick in 1..10_000) {
            for (lane in lanes) {
                val frame = c.next(lane)!!
                if (random.nextDouble() >= reception) continue
                val r = singer.accept(frame)
                if (r is Reassembler.Result.Complete && r.doc == STATE && r.dv == 2) haveState = true
                if (r is Reassembler.Result.Complete && r.doc == SONG && r.dv == 0x2002) haveSong = true
            }
            if (haveState && haveSong) return tick * Carousel.ADVERTISING_INTERVAL_MS
        }
        error("never complete")
    }

    @Test
    fun `delivery of a pushed 2 kB song, no loss`() {
        val measured = listOf(deliveryMs(1, extended = true), deliveryMs(3, extended = false), deliveryMs(2, extended = false), deliveryMs(1, extended = false))
        println("no-loss delivery of state and song: $measured")
        // v0.34: on the legacy carrier the state's lead-in (135 frames) comes first; the song follows (v0.33: 5.7, 8.5 and 17 s).
        assertEquals(listOf(1_500L, 8_700L, 13_000L, 26_000L), measured)
    }

    @Test
    fun `delivery under loss as measured on the Huawei with Wi-Fi off, 40 percent reception`() {
        val random = Random(20261025)
        fun median(sets: Int, ext: Boolean) = List(21) { deliveryMs(sets, ext, 0.4, random) }.sorted()[10]
        val legacy = median(3, ext = false)
        val ext = median(1, ext = true)
        println("median delivery at 40 %: legacy 3 sets $legacy ms, extended $ext ms")
        assertTrue(legacy in 9_000..17_000, "legacy $legacy ms")      // proposal section 2: about 12 s; about 16 s behind the lead-in (v0.34)
        assertTrue(ext in 1_500..4_000, "extended $ext ms")            // about 2.2 s
    }
}
