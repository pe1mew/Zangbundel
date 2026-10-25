// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.frame

import nl.pe1mew.zangbundel.core.protocol.ProtocolConstants
import java.util.BitSet

/**
 * A singer's reassembly of broadcast documents (ble-protocol 7.5) for one gathering and one carrier.
 *
 * Frames are grouped by (`doc`, `dv`) with a bitmap of received indices; at most two versions per
 * document are kept, the two seen most recently. `dv` cannot be ordered: for ordinary documents it
 * is two bytes of the document hash, and `seq` of the state is compared for inequality only
 * (section 5). Repaired byte ranges from a GATT
 * Transfer (9.7) fill the same buffer. A complete document is handed out once as [Result.Complete];
 * verifying it against the signed state is the caller's job, and a document that fails verification
 * is [reset] so it can be collected again.
 *
 * Bounds (NFR-SEC-04): only the broadcast documents 1 to 7 are collected, each group is at most
 * [maxDocumentBytes], so the whole reassembler holds at most 7 × 2 × [maxDocumentBytes]. Nothing
 * received can make it throw.
 */
class Reassembler(
    private val gid16: Int,
    private val carrier: Carrier,
    private val maxDocumentBytes: Int = DEFAULT_MAX_DOCUMENT_BYTES,
) {
    sealed interface Result {
        /** Not for this gathering or carrier, not a broadcast document, inconsistent or too large. */
        data object Ignored : Result
        /** New bytes stored; the document is not complete yet. */
        data object Stored : Result
        /** Nothing new: the frame was already received, or the document was already handed out. */
        data object Duplicate : Result
        class Complete(val doc: Int, val dv: Int, bytes: ByteArray) : Result {
            val bytes: ByteArray = bytes.copyOf()
        }
    }

    private class Group(val doc: Int, val dv: Int, val total: Int, val frameSize: Int) {
        val buffer = ByteArray(total * frameSize)
        val received = BitSet(total)
        var length: Int? = null
        var complete = false

        fun frameEnd(i: Int): Int = if (i == total - 1) length ?: ((i + 1) * frameSize) else (i + 1) * frameSize
    }

    private val groups = HashMap<Int, MutableList<Group>>()

    fun accept(frame: Frame): Result {
        if (frame.carrier != carrier || frame.gid16 != gid16 || frame.doc !in BROADCAST_DOCUMENTS) return Result.Ignored
        val group = groupFor(frame.doc, frame.dv, frame.total, frame.frameSize) ?: return Result.Ignored
        if (group.complete || group.received[frame.idx]) return Result.Duplicate
        if (frame.idx == group.total - 1) group.length = frame.idx * group.frameSize + frame.dataSize
        frame.raw().copyInto(group.buffer, frame.idx * group.frameSize)
        group.received.set(frame.idx)
        return completeOrStored(group)
    }

    /**
     * Bytes of a repair (9.7): [bytes] starts at [offset] of the framed document whose length the
     * Transfer HEADER gave as [streamLength]. Only frames the range covers completely are filled.
     * Without a group, only a range holding the whole document is taken.
     */
    fun acceptRange(doc: Int, dv: Int, offset: Int, bytes: ByteArray, streamLength: Int): Result {
        if (doc !in BROADCAST_DOCUMENTS || dv !in 0..0xFFFF) return Result.Ignored
        if (streamLength !in 0..maxDocumentBytes || offset < 0 || offset.toLong() + bytes.size > streamLength) return Result.Ignored
        val group = groups[doc]?.firstOrNull { it.dv == dv }
        if (group == null) {
            if (offset != 0 || bytes.size != streamLength) return Result.Ignored
            val size = defaultFrameSize()
            val created = groupFor(doc, dv, maxOf(1, (streamLength + size - 1) / size), size) ?: return Result.Ignored
            created.length = streamLength
            bytes.copyInto(created.buffer)
            created.received.set(0, created.total)
            return completeOrStored(created)
        }
        if (group.complete) return Result.Duplicate
        if (!fitsFrames(group, streamLength) || (group.length != null && group.length != streamLength)) return Result.Ignored
        group.length = streamLength
        val end = offset + bytes.size
        var stored = false
        for (i in 0 until group.total) {
            val from = i * group.frameSize
            val to = group.frameEnd(i)
            if (group.received[i] || from < offset || to > end || (from == to && offset != from)) continue
            bytes.copyInto(group.buffer, from, from - offset, to - offset)
            group.received.set(i)
            stored = true
        }
        return if (stored) completeOrStored(group) else Result.Duplicate
    }

    /**
     * The byte ranges `[offset, length]` still missing for a document, ascending and merged, at most
     * [maxRanges] (9.7 allows 32; the tail is merged into the last range). Null when the document is
     * unknown, so the caller asks for the whole of it. When the last frame was never received, pass
     * the document's framed length from the verified state manifest (`len`, 9.4) as [knownLength]
     * (9.7, decided 2026-10-25, Q18); a value that does not fit the frames is ignored, and without
     * it the last range runs to the end of a full frame, which the leader clamps.
     */
    fun missingRanges(doc: Int, dv: Int, knownLength: Int? = null, maxRanges: Int = MAX_REPAIR_RANGES): List<Pair<Int, Int>>? {
        val group = groups[doc]?.firstOrNull { it.dv == dv } ?: return null
        if (group.complete) return emptyList()
        if (group.length == null && knownLength != null && fitsFrames(group, knownLength)) group.length = knownLength
        val ranges = ArrayList<IntArray>()
        for (i in 0 until group.total) {
            if (group.received[i]) continue
            val from = i * group.frameSize
            val to = group.frameEnd(i)
            val last = ranges.lastOrNull()
            if (last != null && last[1] == from) last[1] = to else ranges += intArrayOf(from, to)
        }
        if (ranges.size > maxRanges) {
            val keep = ranges.subList(0, maxRanges).toMutableList()
            keep[maxRanges - 1] = intArrayOf(keep[maxRanges - 1][0], ranges.last()[1])
            return keep.map { it[0] to it[1] - it[0] }
        }
        return ranges.map { it[0] to it[1] - it[0] }
    }

    fun isComplete(doc: Int, dv: Int): Boolean = groups[doc]?.firstOrNull { it.dv == dv }?.complete == true

    /** Drops a version, for example after it failed verification (7.5), so it is collected anew. */
    fun reset(doc: Int, dv: Int) {
        groups[doc]?.removeAll { it.dv == dv }
    }

    /** Whether a document of [length] bytes is framed as [group] says. */
    private fun fitsFrames(group: Group, length: Int): Boolean =
        if (group.total == 1) length in 0..group.frameSize
        else length > (group.total - 1) * group.frameSize && length <= group.total * group.frameSize

    private fun completeOrStored(group: Group): Result {
        if (group.received.cardinality() < group.total) return Result.Stored
        group.complete = true
        val length = group.length ?: group.buffer.size
        return Result.Complete(group.doc, group.dv, group.buffer.copyOf(length))
    }

    /** The group for (`doc`, `dv`), created inside the version window; null when the frame falls outside it. */
    private fun groupFor(doc: Int, dv: Int, total: Int, frameSize: Int): Group? {
        val versions = groups.getOrPut(doc) { ArrayList(MAX_VERSIONS) }
        val existing = versions.firstOrNull { it.dv == dv }
        if (existing != null) {
            if (existing.total == total && existing.frameSize == frameSize) return existing
            versions.remove(existing) // same version framed differently: start again with this framing
        }
        if (total.toLong() * frameSize > maxDocumentBytes) return null
        if (versions.size >= MAX_VERSIONS) versions.removeAt(0) // versions are kept in the order first seen
        return Group(doc, dv, total, frameSize).also { versions += it }
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
        /** Bound for one document; the song file limit of song-format 5.5 is 64 KiB before compression. */
        const val DEFAULT_MAX_DOCUMENT_BYTES = 64 * 1024
    }
}
