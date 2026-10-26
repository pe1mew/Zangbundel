// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.carousel

import nl.pe1mew.zangbundel.core.protocol.ProtocolConstants
import nl.pe1mew.zangbundel.core.protocol.frame.Frame
import nl.pe1mew.zangbundel.core.protocol.frame.Framer
import nl.pe1mew.zangbundel.core.protocol.payload.DocumentType

/**
 * The leader's broadcast carousel (ble-protocol 7.4): which frame each data set sends next.
 *
 * A pure scheduler, without radio or clock (architecture section 7): [publish] hands it the
 * current framed documents, and every [next] call gives the frame for one advertising event of one
 * set, i.e. 100 ms (7.4 rule 6). The beacon set and position changes (rule 5) are not its business.
 *
 * - **Set S** (`Lane.Small`), the first legacy data set: loops state, info, reqstat, message,
 *   download (rule 1). With only one legacy data set it carries every document and puts one state
 *   frame after every 8 document frames (rule 2).
 * - **Sets D1…D(n−1)** (`Lane.Large(i)`): loop song, setlist, song, …; with two of them D1 sends
 *   the even frames and D2 the odd ones, and in general D(i) sends the frames whose index is
 *   `i − 1` modulo the number of D sets (rule 2).
 * - **The extended set** (`Lane.Extended`): loops documents 1–7 and sends the whole state record
 *   again after every 8 document frames (rule 3).
 * - **Erasure code** (7.3): every document is framed as `k` source and `r` repair frames; one pass
 *   of a document is all `n = k + r` of them, and a burst sends the source frames of all changed
 *   documents before their repair frames.
 * - **Burst** (rule 4): on [publish], every set restarts with the documents that changed, three
 *   complete passes, then returns to its normal loop. On the legacy carrier the sets share the
 *   burst (decided 2026-10-25): Set S sends the state record first, and every other changed frame
 *   goes to the set with the least queued, so all sets finish a pass together. The extended set
 *   sends its pass with the state record first.
 *
 * Documents larger than the legacy carrier can hold (4080 bytes) are left out of the legacy sets;
 * they are fetched by repair (9.4 `gatt`) and still go out on the extended set. The download
 * document (6) is reserved in phase 1 and is never sent.
 */
class Carousel(
    private val gid16: Int,
    /** Legacy data sets in use, 1 to 3 (7.1). */
    val legacyDataSets: Int,
    val extended: Boolean,
    /** Extended frame data size `fs` (7.1: lower than 235 when the stack refuses full frames). */
    private val extendedFs: Int = ProtocolConstants.EXTENDED_FRAME_MAX_DATA_BYTES,
) {
    init {
        require(legacyDataSets in 1..MAX_LEGACY_DATA_SETS) { "1 to $MAX_LEGACY_DATA_SETS legacy data sets" }
        require(extendedFs in 1..ProtocolConstants.EXTENDED_FRAME_MAX_DATA_BYTES) { "fs out of range" }
    }

    /** A document as published: its number, its `dv` (`seq` for the state) and its framed bytes. */
    class Document(val doc: Int, val dv: Int, framed: ByteArray) {
        val framed: ByteArray = framed.copyOf()
        init {
            require(doc in BROADCAST_DOCUMENTS) { "only documents 1 to 7 are broadcast" }
            require(dv in 0..0xFFFF) { "dv is u16" }
        }
    }

    sealed interface Lane {
        data object Small : Lane
        data class Large(val index: Int) : Lane
        data object Extended : Lane
    }

    /** The data sets this carousel feeds, in the order the leader opens them. */
    val lanes: List<Lane> = buildList {
        add(Lane.Small)
        for (i in 1 until legacyDataSets) add(Lane.Large(i))
        if (extended) add(Lane.Extended)
    }

    private class LaneState {
        var loop: List<Frame> = emptyList()
        var position = 0
        val burst = ArrayDeque<Frame>()
        /** Frames the last burst started with, for [burstPassesDone]. */
        var burstTotal = 0
    }

    private val state = lanes.associateWith { LaneState() }
    private var published: Map<Int, Document> = emptyMap()

    /**
     * Replaces the documents and starts the burst for those whose `dv` changed. The state record
     * (document 1) must be present: it is what singers verify everything else against.
     */
    fun publish(documents: Collection<Document>) {
        val docs = documents.associateBy { it.doc }
        require(docs.size == documents.size) { "one version per document" }
        require(DocumentType.STATE in docs) { "the state record is always published" }
        val changed = docs.values.filter { published[it.doc]?.dv != it.dv }.map { it.doc }.toSet()
        published = docs.filterKeys { it != DocumentType.DOWNLOAD }
        val legacyPass = sharedLegacyPass(changed)
        for (lane in lanes) {
            val s = state.getValue(lane)
            s.loop = loopFor(lane)
            s.position = 0
            s.burst.clear()
            val pass = if (lane == Lane.Extended) extendedPass(changed) else legacyPass.getValue(lane)
            repeat(BURST_PASSES) { s.burst.addAll(pass) }
            s.burstTotal = s.burst.size
        }
    }

    /** The frame [lane] sends at its next advertising event; null only before the first [publish]. */
    fun next(lane: Lane): Frame? {
        val s = state[lane] ?: throw IllegalArgumentException("$lane is not a set of this carousel")
        s.burst.removeFirstOrNull()?.let { return it }
        if (s.loop.isEmpty()) return null
        val frame = s.loop[s.position % s.loop.size]
        s.position = (s.position + 1) % s.loop.size
        return frame
    }

    /** Frames left in [lane]'s burst; zero once it has returned to its normal loop. */
    fun burstRemaining(lane: Lane): Int = state.getValue(lane).burst.size

    /** Complete passes [lane] has sent of the last burst, 0 to [BURST_PASSES]. */
    fun burstPassesDone(lane: Lane): Int {
        val s = state.getValue(lane)
        if (s.burstTotal == 0) return BURST_PASSES
        return ((s.burstTotal - s.burst.size) * BURST_PASSES / s.burstTotal).coerceIn(0, BURST_PASSES)
    }

    /**
     * Pass time `pt` (9.4, 7.6): how long one full pass of the current song takes on the legacy
     * carrier, in milliseconds, from the frame counts at 100 ms per event. Null without a song, or
     * when the song is too large for the legacy carrier.
     */
    fun songPassTimeMs(): Long? {
        val song = published[DocumentType.SONG] ?: return null
        if (!fitsLegacy(song)) return null
        val frames = legacyFrames(song).size
        val events = if (legacyDataSets == 1) {
            frames + frames / STATE_FRAME_EVERY    // one state frame after every 8 document frames
        } else {
            val sets = legacyDataSets - 1
            (frames + sets - 1) / sets             // the D sets share the song's frames
        }
        return events.toLong() * ADVERTISING_INTERVAL_MS
    }

    // ---- loops (7.4 rules 1–3) ----

    private fun loopFor(lane: Lane): List<Frame> = when (lane) {
        Lane.Small -> if (legacyDataSets == 1) singleSetLoop() else framesOf(SMALL_ORDER, ::legacyFrames)
        is Lane.Large -> largeLoop(lane.index)
        Lane.Extended -> withStateRecordEvery8(framesOf(EXTENDED_ORDER.filter { it != DocumentType.STATE }, ::extendedFrames))
    }

    /** One legacy data set: the state record, then everything with one state frame after every 8 document frames. */
    private fun singleSetLoop(): List<Frame> {
        val stateFrames = docFrames(DocumentType.STATE, ::legacyFrames)
        val documentFrames = framesOf(SINGLE_SET_ORDER, ::legacyFrames)
        if (documentFrames.isEmpty()) return stateFrames
        val out = ArrayList<Frame>(stateFrames.size + documentFrames.size * 9 / 8 + 1)
        out += stateFrames
        var nextState = 0
        documentFrames.forEachIndexed { i, f ->
            out += f
            if ((i + 1) % STATE_FRAME_EVERY == 0 && stateFrames.isNotEmpty()) {
                out += stateFrames[nextState % stateFrames.size]
                nextState++
            }
        }
        return out
    }

    /** Set D[index]: song, setlist, song, … with the frames partitioned over the D sets. */
    private fun largeLoop(index: Int): List<Frame> {
        val song = partition(docFrames(DocumentType.SONG, ::legacyFrames), index)
        val setlist = partition(docFrames(DocumentType.SETLIST, ::legacyFrames), index)
        return song + setlist   // cycling gives song, setlist, song, setlist, …
    }

    /**
     * The extended loop (rule 3): the state record's `k` frames again after every 8 document frames,
     * cycling through its coded frames so that each repetition brings singers new ones (7.3).
     */
    private fun withStateRecordEvery8(documentFrames: List<Frame>): List<Frame> {
        val stateFrames = docFrames(DocumentType.STATE, ::extendedFrames)
        if (stateFrames.isEmpty()) return documentFrames
        val k = stateFrames.first().k
        var next = 0
        fun stateRecord(): List<Frame> = List(k) { stateFrames[(next + it) % stateFrames.size] }.also { next = (next + k) % stateFrames.size }
        val out = ArrayList<Frame>()
        out += stateRecord()
        documentFrames.forEachIndexed { i, f ->
            out += f
            if ((i + 1) % STATE_FRAME_EVERY == 0 && i != documentFrames.lastIndex) out += stateRecord()
        }
        return out
    }

    // ---- burst (7.4 rule 4) ----

    /**
     * One pass of the changed documents over the legacy sets: the state record's source frames on Set
     * S first, its repair frames next, then every other changed frame, each to the set with the fewest
     * frames queued (Set S first on a tie), so that all sets finish the pass together.
     */
    private fun sharedLegacyPass(changed: Set<Int>): Map<Lane, List<Frame>> {
        val legacyLanes = lanes.filter { it != Lane.Extended }
        val assigned = legacyLanes.associateWith { ArrayList<Frame>() }
        val state = if (DocumentType.STATE in changed) docFrames(DocumentType.STATE, ::legacyFrames) else emptyList()
        assigned.getValue(Lane.Small) += state.filter { it.isSource }
        // v0.24 (Q24): the whole state record opens the pass, so a singer missing a few of its frames
        // completes it at once; then the sources of the other documents before their repair frames (7.3, 7.4).
        val others = framesOf(BURST_ORDER.filter { it in changed }, ::legacyFrames)
        val rest = state.filter { !it.isSource } + others.filter { it.isSource } + others.filter { !it.isSource }
        for (frame in rest) assigned.getValue(legacyLanes.minBy { assigned.getValue(it).size }) += frame
        return assigned
    }

    private fun extendedPass(changed: Set<Int>): List<Frame> {
        val state = if (DocumentType.STATE in changed) docFrames(DocumentType.STATE, ::extendedFrames) else emptyList()
        val others = framesOf(EXTENDED_ORDER.filter { it in changed && it != DocumentType.STATE }, ::extendedFrames)
        return state + others.filter { it.isSource } + others.filter { !it.isSource }
    }

    // ---- framing ----

    private fun framesOf(order: List<Int>, framer: (Document) -> List<Frame>): List<Frame> =
        order.flatMap { docFrames(it, framer) }

    private fun docFrames(doc: Int, framer: (Document) -> List<Frame>): List<Frame> =
        published[doc]?.let(framer) ?: emptyList()

    private fun legacyFrames(d: Document): List<Frame> =
        if (fitsLegacy(d)) Framer.legacy(gid16, d.doc, d.dv, d.framed) else emptyList()

    private fun extendedFrames(d: Document): List<Frame> = Framer.extended(gid16, d.doc, d.dv, d.framed, extendedFs)

    private fun fitsLegacy(d: Document): Boolean = d.framed.size <= ProtocolConstants.LEGACY_DOCUMENT_MAX_BYTES

    private fun partition(frames: List<Frame>, index: Int): List<Frame> {
        val sets = legacyDataSets - 1
        return if (sets <= 1) frames else frames.filter { it.idx % sets == index - 1 }
    }

    companion object {
        const val MAX_LEGACY_DATA_SETS = 3
        const val BURST_PASSES = 3
        const val STATE_FRAME_EVERY = 8
        const val ADVERTISING_INTERVAL_MS = 100L
        val BROADCAST_DOCUMENTS = 1..7

        /** Set S with two or more data sets (rule 1); download (6) is reserved and never published. */
        private val SMALL_ORDER = listOf(DocumentType.STATE, DocumentType.INFO, DocumentType.REQSTAT, DocumentType.MESSAGE, DocumentType.DOWNLOAD)
        /** Set S alone (rule 2): everything except the state, which is inserted. */
        private val SINGLE_SET_ORDER = listOf(DocumentType.INFO, DocumentType.SONG, DocumentType.SETLIST, DocumentType.REQSTAT, DocumentType.MESSAGE, DocumentType.DOWNLOAD)
        /** The order of a shared legacy burst after the state record: small documents, then song and setlist. */
        private val BURST_ORDER = listOf(DocumentType.INFO, DocumentType.REQSTAT, DocumentType.MESSAGE, DocumentType.DOWNLOAD, DocumentType.SONG, DocumentType.SETLIST)
        /** The extended set (rule 3): documents 1 to 7 in order. */
        private val EXTENDED_ORDER = (1..7).toList()
    }
}
