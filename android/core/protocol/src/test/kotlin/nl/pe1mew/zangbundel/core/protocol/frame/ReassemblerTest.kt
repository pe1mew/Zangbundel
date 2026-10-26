// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.frame

import nl.pe1mew.zangbundel.core.protocol.frame.Reassembler.Result
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class ReassemblerTest {
    private val gid16 = 0x3B7D
    private fun doc(n: Int, seed: Int = 1) = Random(seed).nextBytes(n)
    private fun legacy() = Reassembler(gid16, Carrier.LEGACY)

    private fun feed(r: Reassembler, frames: List<Frame>): List<Result> = frames.map { r.accept(it) }

    @Test
    fun `the source frames alone complete the document once`() {
        val bytes = doc(100)
        val frames = Framer.legacy(gid16, 3, 1, bytes)                 // k = 7
        val results = feed(legacy(), frames.filter { it.isSource }.shuffled(Random(7)) + frames)
        val complete = results.filterIsInstance<Result.Complete>()
        assertEquals(1, complete.size)
        assertContentEquals(bytes, complete.single().bytes)
        assertEquals(7, results.indexOfFirst { it is Result.Complete } + 1)
        assertTrue(results.drop(7).all { it == Result.Duplicate })
    }

    @Test
    fun `any k distinct frames complete it, repair frames standing in for lost ones`() {
        val bytes = doc(2000)
        val frames = Framer.legacy(gid16, 3, 2, bytes)                 // k = 125, n = 256
        val random = Random(3)
        repeat(5) {
            val r = legacy()
            val pick = frames.shuffled(random).take(125)
            val results = feed(r, pick)
            assertContentEquals(bytes, assertIs<Result.Complete>(results.last()).bytes)
            assertTrue(results.dropLast(1).all { it == Result.Stored })
        }
    }

    @Test
    fun `extended frames with a lowered fs decode from repair frames only`() {
        val bytes = doc(1000)
        val frames = Framer.extended(gid16, 4, 2, bytes, fs = 120)      // k = 9, n = 27
        val r = Reassembler(gid16, Carrier.EXTENDED)
        val complete = feed(r, frames.filter { !it.isSource }).filterIsInstance<Result.Complete>().single()
        assertContentEquals(bytes, complete.bytes)
    }

    @Test
    fun `an empty document completes from one frame`() {
        val complete = assertIs<Result.Complete>(legacy().accept(Framer.legacy(gid16, 5, 1, ByteArray(0)).last()))
        assertEquals(0, complete.bytes.size)
    }

    @Test
    fun `frames of another gathering, carrier or document type are ignored`() {
        val r = legacy()
        assertEquals(Result.Ignored, r.accept(Framer.legacy(0x1111, 3, 1, doc(10)).first()))
        assertEquals(Result.Ignored, r.accept(Framer.extended(gid16, 3, 1, doc(10)).first()))
        assertEquals(Result.Ignored, r.accept(Framer.legacy(gid16, 8, 1, doc(10)).first()))   // GATT only
        assertEquals(Result.Ignored, r.accept(Framer.legacy(gid16, 10, 1, doc(10)).first()))  // unknown type (9 is a song ahead, v0.32)
        assertEquals(Result.Ignored, r.accept(Framer.legacy(gid16, 0, 1, doc(10)).first()))
    }

    @Test
    fun `songs ahead keep up to 64 versions, the oldest dropped first`() {
        val r = legacy()
        // 70 songs ahead of 40 bytes (3 source frames), each with only its first frame heard.
        for (i in 0 until 70) assertEquals(Result.Stored, r.accept(Framer.legacy(gid16, 9, 0x1000 + i, doc(40, i)).first()))
        assertEquals(null, r.progress(9, 0x1000 + 5), "among the six oldest, dropped")
        assertEquals(1 to 3, r.progress(9, 0x1000 + 6))
        assertEquals(1 to 3, r.progress(9, 0x1000 + 69))
    }

    @Test
    fun `two versions per document are kept, the two seen most recently`() {
        val r = legacy()
        // dv values are hash bytes, so their order means nothing: 0x9000 before 0x1000 before 0x5000.
        val a = Framer.legacy(gid16, 3, 0x9000, doc(40, 5))
        val b = Framer.legacy(gid16, 3, 0x1000, doc(40, 6))
        val c = Framer.legacy(gid16, 3, 0x5000, doc(40, 7))
        r.accept(a[0]); r.accept(b[0])
        assertEquals(Result.Stored, r.accept(c[0]))            // a, the first seen, is dropped
        assertNull(r.progress(3, 0x9000))
        assertEquals(Result.Stored, r.accept(b[1]))
        assertIs<Result.Complete>(r.accept(b[5]))              // k = 3: any third frame completes b
        assertEquals(Result.Stored, r.accept(a[1]))            // a straggler of a starts it again and drops b
        assertNull(r.progress(3, 0x1000))
        assertEquals(1 to 3, r.progress(3, 0x5000))
    }

    @Test
    fun `state versions are told apart by inequality only`() {
        val r = legacy()
        r.accept(Framer.legacy(gid16, 1, 0xFFFF, doc(40))[0])
        assertEquals(Result.Stored, r.accept(Framer.legacy(gid16, 1, 0, doc(40))[0]))
        assertEquals(2, listOf(0xFFFF, 0).count { r.progress(1, it) != null })
    }

    @Test
    fun `a version framed differently starts again`() {
        val r = legacy()
        r.accept(Framer.legacy(gid16, 3, 1, doc(40))[0])        // k = 3
        assertEquals(Result.Stored, r.accept(Framer.legacy(gid16, 3, 1, doc(60))[0]))   // k = 4, same dv
        assertEquals(1 to 4, r.progress(3, 1))
    }

    @Test
    fun `repair ranges ask only for as many source frames as are still needed`() {
        val r = legacy()
        val frames = Framer.legacy(gid16, 3, 1, doc(70))       // k = 5, last source frame 6 bytes
        r.accept(frames[0]); r.accept(frames[2]); r.accept(frames[7])   // two sources and one repair frame
        // Two more frames are needed: source frames 1 and 3.
        assertEquals(listOf(16 to 16, 48 to 16), r.missingRanges(3, 1))
        r.accept(frames[9])
        assertEquals(listOf(16 to 16), r.missingRanges(3, 1))
        assertNull(r.missingRanges(4, 1))
    }

    @Test
    fun `more than 32 gaps merge into the last range`() {
        val r = legacy()
        val frames = Framer.legacy(gid16, 3, 1, doc(16 * 100))
        frames.filterIndexed { i, f -> f.isSource && i % 2 == 0 }.forEach { r.accept(it) }    // 50 of k = 100
        val ranges = r.missingRanges(3, 1)!!
        assertEquals(32, ranges.size)
        assertEquals(16 to 16, ranges.first())
        val last = ranges.last()
        assertEquals(31 * 32 + 16, last.first)
        assertEquals(1600, last.first + last.second)
    }

    @Test
    fun `repaired ranges count as the source frames they cover`() {
        val bytes = doc(70)
        val r = legacy()
        val frames = Framer.legacy(gid16, 3, 1, bytes)
        r.accept(frames[0]); r.accept(frames[2]); r.accept(frames[6])
        assertEquals(Result.Stored, r.acceptRange(3, 1, 16, bytes.copyOfRange(16, 32), streamLength = 70))
        val done = assertIs<Result.Complete>(r.acceptRange(3, 1, 64, bytes.copyOfRange(64, 70), streamLength = 70))
        assertContentEquals(bytes, done.bytes)
    }

    @Test
    fun `a repair of a document never seen is taken only whole`() {
        val bytes = doc(300)
        val r = legacy()
        assertEquals(Result.Ignored, r.acceptRange(2, 4, 16, bytes.copyOfRange(16, 300), streamLength = 300))
        assertContentEquals(bytes, assertIs<Result.Complete>(r.acceptRange(2, 4, 0, bytes, streamLength = 300)).bytes)
        assertEquals(Result.Duplicate, r.accept(Framer.legacy(gid16, 2, 4, bytes)[0]))
    }

    @Test
    fun `inconsistent repairs are ignored`() {
        val bytes = doc(70)
        val r = legacy()
        r.accept(Framer.legacy(gid16, 3, 1, bytes)[0])
        assertEquals(Result.Ignored, r.acceptRange(3, 1, 16, ByteArray(16), streamLength = 200))   // another length
        assertEquals(Result.Ignored, r.acceptRange(3, 1, 64, ByteArray(16), streamLength = 70))    // past the end
        assertEquals(Result.Ignored, r.acceptRange(3, 1, -1, ByteArray(1), streamLength = 70))
        assertEquals(Result.Duplicate, r.acceptRange(3, 1, 20, ByteArray(8), streamLength = 70))    // no whole frame
    }

    @Test
    fun `a document that fails verification can be collected again`() {
        val frames = Framer.legacy(gid16, 3, 1, doc(40))
        val r = legacy()
        assertIs<Result.Complete>(feed(r, frames.take(3)).last())
        assertTrue(r.isComplete(3, 1))
        r.reset(3, 1)
        assertIs<Result.Complete>(feed(r, frames.drop(3).take(3)).last())
    }

    @Test
    fun `documents above the size bound are not collected`() {
        val r = Reassembler(gid16, Carrier.EXTENDED, maxDocumentBytes = 1000)
        assertEquals(Result.Ignored, r.accept(Framer.extended(gid16, 3, 1, doc(1001)).first()))
        assertEquals(Result.Stored, r.accept(Framer.extended(gid16, 3, 2, doc(1000)).first()))
        assertEquals(Result.Ignored, r.acceptRange(4, 1, 0, ByteArray(1001), streamLength = 1001))
    }

    @Test
    fun `random frames never make it throw`() {
        val random = Random(20261025)
        val r = Reassembler(gid16, Carrier.EXTENDED, maxDocumentBytes = 4096)
        repeat(50_000) {
            val payload = random.nextBytes(random.nextInt(0, 260))
            if (payload.size > 13) {
                payload[0] = 0xD2.toByte()
                payload[1] = (gid16 ushr 8).toByte(); payload[2] = gid16.toByte()
                payload[3] = random.nextInt(0, 9).toByte()
                payload[4] = 0; payload[5] = random.nextInt(0, 4).toByte()
                payload[6] = 0; payload[7] = random.nextInt(1, 20).toByte()
                payload[10] = 0; payload[11] = (payload.size - 14).coerceIn(0, 255).toByte()
            }
            try {
                FrameCodec.decode(payload)?.let { r.accept(it) }
                r.acceptRange(random.nextInt(0, 9), random.nextInt(0, 4), random.nextInt(-2, 300), random.nextBytes(random.nextInt(0, 64)), random.nextInt(-2, 5000))
                r.missingRanges(random.nextInt(0, 9), random.nextInt(0, 4))
            } catch (e: Throwable) {
                fail("${payload.joinToString("") { "%02x".format(it) }} threw ${e::class.simpleName}: ${e.message}")
            }
        }
    }

    @Test
    fun `blocks are received, missed behind a later frame, or still to come`() {
        val r = legacy()
        val frames = Framer.legacy(gid16, 3, 1, doc(100))                 // k = 7, then 14 repair frames
        assertNull(r.blocks(3, 1))
        listOf(0, 1, 3).forEach { r.accept(frames[it]) }
        val R = Reassembler.Block.RECEIVED
        val M = Reassembler.Block.MISSED
        val P = Reassembler.Block.PENDING
        assertEquals(listOf(R, R, M, R, P, P, P), r.blocks(3, 1))
        r.accept(frames[8])                                                // a repair frame: every source has gone by
        assertEquals(listOf(R, R, M, R, M, M, M), r.blocks(3, 1))
        frames.drop(9).take(3).forEach { r.accept(it) }                    // k = 7 distinct frames: rebuilt, all received
        assertEquals(List(7) { R }, r.blocks(3, 1))
        assertEquals(1, r.latestVersion(3))
    }

    @Test
    fun `a repaired range turns its blocks green`() {
        val bytes = doc(70)
        val r = legacy()
        r.accept(Framer.legacy(gid16, 3, 1, bytes)[2])                    // k = 5; block 2 received, 0 and 1 missed
        r.acceptRange(3, 1, 0, bytes.copyOfRange(0, 32), streamLength = 70)
        assertEquals(listOf(Reassembler.Block.RECEIVED, Reassembler.Block.RECEIVED, Reassembler.Block.RECEIVED, Reassembler.Block.PENDING, Reassembler.Block.PENDING), r.blocks(3, 1))
    }
}
