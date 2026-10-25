// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.frame

import nl.pe1mew.zangbundel.core.protocol.ProtocolConstants
import nl.pe1mew.zangbundel.core.protocol.fec.ReedSolomon
import java.util.BitSet

/**
 * A singer's reassembly of broadcast documents (ble-protocol 7.5) for one gathering and one carrier.
 *
 * Frames are grouped by (`doc`, `dv`). A group holds the distinct frames received; as soon as it
 * has `k` of them, source or repair, it decodes the erasure code (7.3), strips the padding to `len`
 * and hands the document out once as [Result.Complete]. Verifying it against the signed state is the
 * caller's job; a document that fails verification is [reset] so it can be collected again.
 *
 * At most two versions per document are kept, the two seen most recently: `dv` cannot be ordered,
 * since for ordinary documents it is two bytes of the document hash and `seq` of the state is
 * compared for inequality only (section 5). Repaired byte ranges from a GATT Transfer (9.7) count as
 * the source frames they cover.
 *
 * Bounds (NFR-SEC-04): only broadcast documents 1 to 7, each at most [maxDocumentBytes], and a group
 * stores at most `k` frames, so the reassembler holds at most 7 × 2 × ([maxDocumentBytes] plus one
 * frame). Nothing received can make it throw.
 */
class Reassembler(
    private val gid16: Int,
    private val carrier: Carrier,
    private val maxDocumentBytes: Int = DEFAULT_MAX_DOCUMENT_BYTES,
) {
    sealed interface Result {
        /** Not for this gathering or carrier, not a broadcast document, inconsistent or too large. */
        data object Ignored : Result
        /** A new frame stored; fewer than `k` distinct frames so far. */
        data object Stored : Result
        /** Nothing new: the frame was already received, or the document was already handed out. */
        data object Duplicate : Result
        class Complete(val doc: Int, val dv: Int, bytes: ByteArray) : Result {
            val bytes: ByteArray = bytes.copyOf()
        }
    }

    private class Group(val doc: Int, val dv: Int, val k: Int, val frameSize: Int, val len: Int) {
        val seen = BitSet(minOf(k + ReedSolomon.repairCount(k), ReedSolomon.MAX_FRAMES))
        val frames = HashMap<Int, ByteArray>()
        var complete = false
    }

    private val groups = HashMap<Int, MutableList<Group>>()

    fun accept(frame: Frame): Result {
        if (frame.carrier != carrier || frame.gid16 != gid16 || frame.doc !in BROADCAST_DOCUMENTS) return Result.Ignored
        val group = groupFor(frame.doc, frame.dv, frame.k, frame.frameSize, frame.len) ?: return Result.Ignored
        if (group.complete || group.seen[frame.idx]) return Result.Duplicate
        group.seen.set(frame.idx)
        group.frames[frame.idx] = frame.raw().copyOf()
        return completeOrStored(group)
    }

    /**
     * Bytes of a repair (9.7): [bytes] starts at [offset] of the document whose length the Transfer
     * HEADER gave as [streamLength]. Every source frame the range covers completely is stored.
     * Without a group, only a range holding the whole document is taken.
     */
    fun acceptRange(doc: Int, dv: Int, offset: Int, bytes: ByteArray, streamLength: Int): Result {
        if (doc !in BROADCAST_DOCUMENTS || dv !in 0..0xFFFF) return Result.Ignored
        if (streamLength !in 0..maxDocumentBytes || offset < 0 || offset.toLong() + bytes.size > streamLength) return Result.Ignored
        val group = groups[doc]?.firstOrNull { it.dv == dv }
        if (group == null) {
            if (offset != 0 || bytes.size != streamLength) return Result.Ignored
            val size = defaultFrameSize()
            val created = groupFor(doc, dv, maxOf(1, (streamLength + size - 1) / size), size, streamLength) ?: return Result.Ignored
            created.complete = true
            return Result.Complete(doc, dv, bytes)
        }
        if (group.complete) return Result.Duplicate
        if (group.len != streamLength) return Result.Ignored
        val end = offset + bytes.size
        var stored = false
        for (i in 0 until group.k) {
            if (group.seen[i]) continue
            val from = i * group.frameSize
            val to = minOf(from + group.frameSize, group.len)
            if (from < offset || to > end || (from == to && offset != from)) continue
            group.seen.set(i)
            group.frames[i] = bytes.copyOfRange(from - offset, to - offset).copyOf(group.frameSize)
            stored = true
        }
        return if (stored) completeOrStored(group) else Result.Duplicate
    }

    /**
     * The byte ranges `[offset, length]` to ask for in a repair: only as many missing source frames as
     * the group still needs to reach `k` (any `k` distinct frames decode), merged and ascending, at most
     * [maxRanges] (9.7 allows 32; the tail is merged into the last range). Null when the document is
     * unknown, so the caller asks for the whole of it.
     */
    fun missingRanges(doc: Int, dv: Int, maxRanges: Int = MAX_REPAIR_RANGES): List<Pair<Int, Int>>? {
        val group = groups[doc]?.firstOrNull { it.dv == dv } ?: return null
        if (group.complete) return emptyList()
        var needed = group.k - group.frames.size
        val ranges = ArrayList<IntArray>()
        var i = 0
        while (needed > 0 && i < group.k) {
            if (!group.seen[i]) {
                val from = i * group.frameSize
                val to = minOf(from + group.frameSize, group.len)
                val last = ranges.lastOrNull()
                if (last != null && last[1] == from) last[1] = to else ranges += intArrayOf(from, to)
                needed--
            }
            i++
        }
        if (ranges.size > maxRanges) {
            val keep = ranges.subList(0, maxRanges).toMutableList()
            keep[maxRanges - 1] = intArrayOf(keep[maxRanges - 1][0], ranges.last()[1])
            return keep.map { it[0] to it[1] - it[0] }
        }
        return ranges.map { it[0] to it[1] - it[0] }
    }

    /** Distinct frames held for a version and the `k` it needs, for the singer's repair rule (7.6). */
    fun progress(doc: Int, dv: Int): Pair<Int, Int>? =
        groups[doc]?.firstOrNull { it.dv == dv }?.let { (if (it.complete) it.k else it.frames.size) to it.k }

    fun isComplete(doc: Int, dv: Int): Boolean = groups[doc]?.firstOrNull { it.dv == dv }?.complete == true

    /** Drops a version, for example after it failed verification (7.5), so it is collected anew. */
    fun reset(doc: Int, dv: Int) {
        groups[doc]?.removeAll { it.dv == dv }
    }

    private fun completeOrStored(group: Group): Result {
        if (group.frames.size < group.k) return Result.Stored
        val source = ReedSolomon.decode(group.k, group.frameSize, group.frames) ?: return Result.Stored
        group.complete = true
        group.frames.clear()
        val out = ByteArray(group.k * group.frameSize)
        source.forEachIndexed { i, s -> s.copyInto(out, i * group.frameSize) }
        return Result.Complete(group.doc, group.dv, out.copyOf(group.len))
    }

    /** The group for (`doc`, `dv`), created inside the version window; null when the document is too large. */
    private fun groupFor(doc: Int, dv: Int, k: Int, frameSize: Int, len: Int): Group? {
        val versions = groups.getOrPut(doc) { ArrayList(MAX_VERSIONS) }
        val existing = versions.firstOrNull { it.dv == dv }
        if (existing != null) {
            if (existing.k == k && existing.frameSize == frameSize && existing.len == len) return existing
            versions.remove(existing) // same version framed differently: start again with this framing
        }
        if (len > maxDocumentBytes) return null
        if (versions.size >= MAX_VERSIONS) versions.removeAt(0) // versions are kept in the order first seen
        return Group(doc, dv, k, frameSize, len).also { versions += it }
    }

    private fun defaultFrameSize(): Int = when (carrier) {
        Carrier.LEGACY -> ProtocolConstants.LEGACY_FRAME_DATA_BYTES
        Carrier.EXTENDED -> ProtocolConstants.EXTENDED_FRAME_MAX_DATA_BYTES
    }

    companion object {
        /** Broadcast documents (7.3); document 8 is GATT-only and never broadcast. */
        val BROADCAST_DOCUMENTS = 1..7
        const val MAX_VERSIONS = 2
        const val MAX_REPAIR_RANGES = 32
        /** Bound for one document: the framed limit of 9.8 (op 8 refuses uploads over 64 kB). */
        const val DEFAULT_MAX_DOCUMENT_BYTES = 64 * 1024
    }
}
