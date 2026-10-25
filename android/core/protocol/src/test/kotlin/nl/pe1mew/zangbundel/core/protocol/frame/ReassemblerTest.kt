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
    fun `frames in any order complete the document once`() {
        val bytes = doc(100)
        val frames = Framer.legacy(gid16, 3, 1, bytes).shuffled(Random(7))
        val results = feed(legacy(), frames + frames)
        val complete = results.filterIsInstance<Result.Complete>()
        assertEquals(1, complete.size)
        assertContentEquals(bytes, complete.single().bytes)
        assertEquals(frames.size, results.indexOfFirst { it is Result.Complete } + 1)
        assertTrue(results.drop(frames.size).all { it == Result.Duplicate })
    }

    @Test
    fun `extended frames with a lowered fs reassemble`() {
        val bytes = doc(1000)
        val r = Reassembler(gid16, Carrier.EXTENDED)
        val complete = feed(r, Framer.extended(gid16, 4, 2, bytes, fs = 120)).filterIsInstance<Result.Complete>().single()
        assertContentEquals(bytes, complete.bytes)
    }

    @Test
    fun `an empty document completes from its one frame`() {
        val complete = assertIs<Result.Complete>(legacy().accept(Framer.legacy(gid16, 5, 1, ByteArray(0)).single()))
        assertEquals(0, complete.bytes.size)
    }

    @Test
    fun `frames of another gathering, carrier or document type are ignored`() {
        val r = legacy()
        assertEquals(Result.Ignored, r.accept(Framer.legacy(0x1111, 3, 1, doc(10)).single()))
        assertEquals(Result.Ignored, r.accept(Framer.extended(gid16, 3, 1, doc(10)).single()))
        assertEquals(Result.Ignored, r.accept(Framer.legacy(gid16, 8, 1, doc(10)).single())) // GATT only
        assertEquals(Result.Ignored, r.accept(Framer.legacy(gid16, 9, 1, doc(10)).single())) // unknown type
        assertEquals(Result.Ignored, r.accept(Framer.legacy(gid16, 0, 1, doc(10)).single()))
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
        assertNull(r.missingRanges(3, 0x9000))
        assertEquals(Result.Stored, r.accept(b[1]))            // b and c are still collected
        assertIs<Result.Complete>(r.accept(b[2]))
        assertEquals(Result.Stored, r.accept(a[1]))            // a straggler of a starts it again and drops b
        assertNull(r.missingRanges(3, 0x1000))
        assertEquals(listOf(16 to 32), r.missingRanges(3, 0x5000))  // frames 1 and 2; the last one unseen
    }

    @Test
    fun `state versions are told apart by inequality only`() {
        val r = legacy()
        r.accept(Framer.legacy(gid16, 1, 0xFFFF, doc(40))[0])
        assertEquals(Result.Stored, r.accept(Framer.legacy(gid16, 1, 0, doc(40))[0]))
        assertEquals(2, listOf(0xFFFF, 0).count { r.missingRanges(1, it) != null })
    }

    @Test
    fun `a version framed differently starts again`() {
        val r = legacy()
        r.accept(Framer.legacy(gid16, 3, 1, doc(40))[0])        // 3 frames
        val other = Framer.legacy(gid16, 3, 1, doc(60))         // 4 frames, same dv
        assertEquals(Result.Stored, r.accept(other[0]))
        assertEquals(listOf(16 to 48), r.missingRanges(3, 1))
    }

    @Test
    fun `missing ranges are merged byte ranges of the framed document`() {
        val r = legacy()
        val frames = Framer.legacy(gid16, 3, 1, doc(70))         // 5 frames, last one 6 bytes
        r.accept(frames[0]); r.accept(frames[2])
        // Frames 1, 3, 4 missing; the last frame was never seen, so its range is a full frame.
        assertEquals(listOf(16 to 16, 48 to 32), r.missingRanges(3, 1))
        r.accept(frames[4])
        assertEquals(listOf(16 to 16, 48 to 16), r.missingRanges(3, 1))
        assertNull(r.missingRanges(4, 1))
    }

    @Test
    fun `more than 32 gaps merge into the last range`() {
        val r = legacy()
        val frames = Framer.legacy(gid16, 3, 1, doc(16 * 100))
        frames.filterIndexed { i, _ -> i % 2 == 0 }.forEach { r.accept(it) }   // every odd frame missing
        val ranges = r.missingRanges(3, 1)!!
        assertEquals(32, ranges.size)
        assertEquals(16 to 16, ranges.first())
        val last = ranges.last()
        assertEquals(31 * 32 + 16, last.first)
        assertEquals(1600, last.first + last.second)
    }

    @Test
    fun `repaired ranges fill the same buffer`() {
        val bytes = doc(70)
        val r = legacy()
        val frames = Framer.legacy(gid16, 3, 1, bytes)
        r.accept(frames[0]); r.accept(frames[2])
        assertEquals(Result.Stored, r.acceptRange(3, 1, 16, bytes.copyOfRange(16, 32), streamLength = 70))
        val done = assertIs<Result.Complete>(r.acceptRange(3, 1, 48, bytes.copyOfRange(48, 70), streamLength = 70))
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
        assertEquals(Result.Ignored, r.acceptRange(3, 1, 16, ByteArray(16), streamLength = 200))  // length beyond the frames
        assertEquals(Result.Ignored, r.acceptRange(3, 1, 64, ByteArray(16), streamLength = 70))   // runs past the end
        assertEquals(Result.Ignored, r.acceptRange(3, 1, -1, ByteArray(1), streamLength = 70))
        assertEquals(Result.Duplicate, r.acceptRange(3, 1, 20, ByteArray(8), streamLength = 70))   // covers no whole frame
    }

    @Test
    fun `a document that fails verification can be collected again`() {
        val frames = Framer.legacy(gid16, 3, 1, doc(40))
        val r = legacy()
        assertIs<Result.Complete>(feed(r, frames).last())
        assertTrue(r.isComplete(3, 1))
        r.reset(3, 1)
        assertIs<Result.Complete>(feed(r, frames).last())
    }

    @Test
    fun `documents above the size bound are not collected`() {
        val r = Reassembler(gid16, Carrier.EXTENDED, maxDocumentBytes = 1000)
        assertEquals(Result.Ignored, r.accept(Frame(Carrier.EXTENDED, gid16, 3, 1, 5, 0, 235, ByteArray(235))))
        assertEquals(Result.Stored, r.accept(Frame(Carrier.EXTENDED, gid16, 3, 1, 4, 0, 235, ByteArray(235))))
        assertEquals(Result.Ignored, r.acceptRange(4, 1, 0, ByteArray(1001), streamLength = 1001))
    }

    @Test
    fun `random frames never make it throw`() {
        val random = Random(20261025)
        val r = Reassembler(gid16, Carrier.EXTENDED, maxDocumentBytes = 4096)
        repeat(50_000) {
            val payload = random.nextBytes(random.nextInt(0, 260))
            if (payload.size > 3) {
                payload[0] = 0xD2.toByte()
                payload[1] = (gid16 ushr 8).toByte(); payload[2] = gid16.toByte()
                payload[3] = random.nextInt(0, 9).toByte()
                if (payload.size > 5) { payload[4] = 0; payload[5] = random.nextInt(0, 4).toByte() }
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
}
