// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.fec

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReedSolomonTest {
    private fun frames(k: Int, size: Int, seed: Int) = Random(seed).let { r -> List(k) { r.nextBytes(size) } }

    @Test
    fun `field arithmetic is consistent`() {
        for (a in 1..255) {
            assertEquals(1, ReedSolomon.mul(a, ReedSolomon.inv(a)), "a = $a")
            assertEquals(0, ReedSolomon.mul(a, 0))
        }
        assertEquals(0x1D, ReedSolomon.mul(0x80, 2))          // x⁷ · x = x⁸ = x⁴ + x³ + x² + 1
    }

    @Test
    fun `redundancy follows 7-3`() {
        assertEquals(30, ReedSolomon.repairCount(15))
        assertEquals(131, ReedSolomon.repairCount(125))
        assertEquals(1, ReedSolomon.repairCount(255))
        assertEquals(0, ReedSolomon.repairCount(256))
        assertEquals(18, ReedSolomon.repairCount(9))
    }

    @Test
    fun `any k frames decode, for every k and random subsets`() {
        val random = Random(20261025)
        for (k in listOf(1, 2, 5, 15, 64, 125, 200, 255)) {
            val source = frames(k, 16, k)
            val r = ReedSolomon.repairCount(k)
            val all = source + ReedSolomon.encode(source, r)
            repeat(if (k > 100) 3 else 10) {
                val pick = all.indices.shuffled(random).take(k).associateWith { all[it] }
                val back = ReedSolomon.decode(k, 16, pick)!!
                for (i in 0 until k) assertContentEquals(source[i], back[i], "k=$k frame $i")
            }
        }
    }

    @Test
    fun `only repair frames decode too`() {
        val source = frames(9, 233, 1)
        val repair = ReedSolomon.encode(source, 18)
        val pick = (9 until 18).associateWith { repair[it - 9] }
        val back = ReedSolomon.decode(9, 233, pick)!!
        for (i in 0 until 9) assertContentEquals(source[i], back[i])
    }

    @Test
    fun `fewer than k frames do not decode`() {
        val source = frames(10, 16, 2)
        val all = source + ReedSolomon.encode(source, 20)
        assertNull(ReedSolomon.decode(10, 16, (0 until 9).associateWith { all[it] }))
        assertNull(ReedSolomon.decode(10, 16, (0 until 10).associateWith { ByteArray(15) }))   // wrong size is ignored
    }

    @Test
    fun `a known vector stays fixed`() {
        // Pins the construction so an iOS implementation can check itself against these bytes.
        val source = listOf(byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6))
        // Computed independently in Python from the same definition: inv(2) = 0x8E, inv(3) = 0xF4.
        val repair = ReedSolomon.encode(source, 2)
        assertEquals(listOf("79028d", "f67902"), repair.map { r -> r.joinToString("") { "%02x".format(it) } })
    }
}
