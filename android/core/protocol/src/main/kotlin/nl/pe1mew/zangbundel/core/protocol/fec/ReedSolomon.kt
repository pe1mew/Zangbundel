// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.fec

/**
 * The erasure code across broadcast frames (ble-protocol 7.3, approved 2026-10-25): systematic
 * Reed–Solomon over GF(2⁸) with Cauchy coefficients.
 *
 * `k` source frames of equal size are followed by `r` repair frames; any `k` distinct frames of the
 * `n = k + r` give back the source frames. Repair frame `j`, byte `b`:
 * `R_j[b] = Σᵢ c(j, i) · S_i[b]` with `c(j, i) = 1 / (x_j ⊕ y_i)`, `y_i = i`, `x_j = k + j`.
 * Every square submatrix of a Cauchy matrix is invertible, so any `k` rows of the identity stacked
 * on the Cauchy matrix decode. The field polynomial is x⁸ + x⁴ + x³ + x² + 1 (`0x11D`).
 */
object ReedSolomon {
    /** `n = k + r` is at most 256: the row labels `x_j`, `y_i` must be distinct bytes. */
    const val MAX_FRAMES = 256

    private val exp = IntArray(512)
    private val log = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            exp[i] = x
            log[x] = i
            x = x shl 1
            if (x and 0x100 != 0) x = x xor 0x11D
        }
        for (i in 255 until 512) exp[i] = exp[i - 255]
    }

    internal fun mul(a: Int, b: Int): Int = if (a == 0 || b == 0) 0 else exp[log[a] + log[b]]
    internal fun inv(a: Int): Int {
        require(a != 0) { "0 has no inverse" }
        return exp[255 - log[a]]
    }

    /** The redundancy of 7.3: `r = min(2k, 256 − k)`, and none when `k` exceeds 255. */
    fun repairCount(k: Int): Int = if (k > MAX_FRAMES - 1) 0 else minOf(2 * k, MAX_FRAMES - k)

    private fun coefficient(k: Int, j: Int, i: Int): Int = inv((k + j) xor i)

    /** The [r] repair frames for [source] (all the same size). */
    fun encode(source: List<ByteArray>, r: Int): List<ByteArray> {
        val k = source.size
        require(k >= 1 && k + r <= MAX_FRAMES) { "k + r must be at most $MAX_FRAMES" }
        val size = source[0].size
        require(source.all { it.size == size }) { "source frames must be the same size" }
        return List(r) { j ->
            val out = ByteArray(size)
            for (i in 0 until k) {
                val c = coefficient(k, j, i)
                val s = source[i]
                for (b in 0 until size) {
                    val v = s[b].toInt() and 0xFF
                    if (v != 0) out[b] = (out[b].toInt() xor mul(c, v)).toByte()
                }
            }
            out
        }
    }

    /**
     * The [k] source frames from any [k] distinct frames: [received] maps a frame index
     * (0 … k − 1 source, k … n − 1 repair) to its bytes, all of [size] bytes. Returns null when
     * fewer than [k] distinct frames are given.
     */
    fun decode(k: Int, size: Int, received: Map<Int, ByteArray>): List<ByteArray>? {
        require(k >= 1 && k <= MAX_FRAMES - 1) { "k out of range" }
        val rows = received.entries.filter { it.key in 0 until MAX_FRAMES && it.value.size == size }.take(k)
        if (rows.size < k) return null
        if (rows.all { it.key < k }) return List(k) { i -> received.getValue(i).copyOf() }

        // Matrix A (k × k): the row of each received frame in [I; C]; B: their bytes. Solve A·S = B.
        val a = Array(k) { IntArray(k) }
        val b = Array(k) { IntArray(size) }
        rows.forEachIndexed { row, (idx, bytes) ->
            if (idx < k) a[row][idx] = 1 else for (i in 0 until k) a[row][i] = coefficient(k, idx - k, i)
            for (p in 0 until size) b[row][p] = bytes[p].toInt() and 0xFF
        }
        for (col in 0 until k) {
            val pivot = (col until k).firstOrNull { a[it][col] != 0 } ?: return null   // cannot happen for a Cauchy code
            if (pivot != col) { a[pivot] = a[col].also { a[col] = a[pivot] }; b[pivot] = b[col].also { b[col] = b[pivot] } }
            val f = inv(a[col][col])
            for (c in col until k) a[col][c] = mul(a[col][c], f)
            for (p in 0 until size) b[col][p] = mul(b[col][p], f)
            for (row in 0 until k) {
                if (row == col) continue
                val g = a[row][col]
                if (g == 0) continue
                for (c in col until k) a[row][c] = a[row][c] xor mul(g, a[col][c])
                for (p in 0 until size) b[row][p] = b[row][p] xor mul(g, b[col][p])
            }
        }
        return List(k) { i -> ByteArray(size) { p -> b[i][p].toByte() } }
    }
}
