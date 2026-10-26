// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.frame

import nl.pe1mew.zangbundel.core.protocol.ProtocolConstants
import nl.pe1mew.zangbundel.core.protocol.fec.ReedSolomon

/** The two broadcast carriers of ble-protocol 7.1. */
enum class Carrier(val magic: Int, val headerBytes: Int) {
    LEGACY(ProtocolConstants.MAGIC_LEGACY_FRAME, ProtocolConstants.LEGACY_FRAME_HEADER_BYTES),
    EXTENDED(ProtocolConstants.MAGIC_EXTENDED_FRAME, ProtocolConstants.EXTENDED_FRAME_HEADER_BYTES),
}

/**
 * One data frame of ble-protocol 7.2, as carried in manufacturer data after the company identifier.
 *
 * A document version is cut into [k] source frames of [frameSize] bytes, the last padded with zero
 * bytes, followed by repair frames of the erasure code (7.3); [idx] 0 … k − 1 is a source frame,
 * k and above a repair frame. Every frame carries the document length [len], so the padding can be
 * stripped. [data] is always exactly [frameSize] bytes.
 */
class Frame(
    val carrier: Carrier,
    val gid16: Int,
    val doc: Int,
    val dv: Int,
    val k: Int,
    val idx: Int,
    val frameSize: Int,
    val len: Int,
    data: ByteArray,
) {
    private val bytes = data.copyOf()
    val data: ByteArray get() = bytes.copyOf()
    internal fun raw(): ByteArray = bytes

    /** All frames of this document version: source plus repair (7.3). */
    val n: Int get() = k + ReedSolomon.repairCount(k)
    val isSource: Boolean get() = idx < k

    init {
        require(gid16 in 0..0xFFFF) { "gid16 out of range" }
        require(doc in 0..0xFF) { "doc out of range" }
        require(dv in 0..0xFFFF) { "dv out of range" }
        when (carrier) {
            Carrier.LEGACY -> {
                require(k in 1..ProtocolConstants.LEGACY_MAX_FRAMES) { "legacy k out of range" }
                require(frameSize == ProtocolConstants.LEGACY_FRAME_DATA_BYTES) { "legacy frames carry 16 bytes" }
            }
            Carrier.EXTENDED -> {
                require(k in 1..0xFFFF) { "extended k out of range" }
                require(frameSize in 1..ProtocolConstants.EXTENDED_FRAME_MAX_DATA_BYTES) { "fs out of range" }
            }
        }
        // 7.2 (v0.34): repair frames may go past `n`, up to index 255, in the state's lead-in (7.4 rule 4).
        require(idx in 0 until maxOf(n, ReedSolomon.MAX_FRAMES)) { "idx $idx outside 0 until ${maxOf(n, ReedSolomon.MAX_FRAMES)}" }
        require(len in 0..0xFFFF && (if (k == 1) len <= frameSize else len > (k - 1) * frameSize && len <= k * frameSize)) {
            "len $len does not fit $k frames of $frameSize bytes"
        }
        require(bytes.size == frameSize) { "a frame carries exactly $frameSize data bytes" }
    }

    override fun toString(): String = "Frame($carrier doc=$doc dv=$dv ${idx + 1}/$n k=$k len=$len)"
}

/** Encodes and decodes the frame layouts of ble-protocol 7.2 (big-endian, section 2). */
object FrameCodec {

    fun encode(frame: Frame): ByteArray {
        val out = ByteArray(frame.carrier.headerBytes + frame.frameSize)
        out[0] = frame.carrier.magic.toByte()
        u16(out, 1, frame.gid16)
        out[3] = frame.doc.toByte()
        u16(out, 4, frame.dv)
        when (frame.carrier) {
            Carrier.LEGACY -> {
                out[6] = frame.k.toByte()
                out[7] = frame.idx.toByte()
                u16(out, 8, frame.len)
            }
            Carrier.EXTENDED -> {
                u16(out, 6, frame.k)
                u16(out, 8, frame.idx)
                u16(out, 10, frame.frameSize)
                u16(out, 12, frame.len)
            }
        }
        frame.raw().copyInto(out, frame.carrier.headerBytes)
        return out
    }

    /**
     * Decodes a manufacturer-data payload (after the company identifier). Returns null for anything
     * that is not a well-formed data frame: beacons, other magics, wrong lengths, inconsistent fields.
     * Input from the air is untrusted; this never throws.
     */
    fun decode(payload: ByteArray): Frame? {
        if (payload.isEmpty()) return null
        val carrier = when (payload[0].toInt() and 0xFF) {
            ProtocolConstants.MAGIC_LEGACY_FRAME -> Carrier.LEGACY
            ProtocolConstants.MAGIC_EXTENDED_FRAME -> Carrier.EXTENDED
            else -> return null
        }
        if (payload.size <= carrier.headerBytes) return null
        val k: Int
        val idx: Int
        val frameSize: Int
        val len: Int
        when (carrier) {
            Carrier.LEGACY -> {
                k = payload[6].toInt() and 0xFF
                idx = payload[7].toInt() and 0xFF
                len = u16(payload, 8)
                frameSize = ProtocolConstants.LEGACY_FRAME_DATA_BYTES
            }
            Carrier.EXTENDED -> {
                k = u16(payload, 6)
                idx = u16(payload, 8)
                frameSize = u16(payload, 10)
                len = u16(payload, 12)
            }
        }
        if (payload.size != carrier.headerBytes + frameSize) return null
        return try {
            Frame(carrier, u16(payload, 1), payload[3].toInt() and 0xFF, u16(payload, 4), k, idx, frameSize, len,
                payload.copyOfRange(carrier.headerBytes, payload.size))
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun u16(out: ByteArray, at: Int, v: Int) {
        out[at] = (v ushr 8).toByte()
        out[at + 1] = v.toByte()
    }

    private fun u16(b: ByteArray, at: Int): Int = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)
}

/** Cuts a document into source frames and adds the repair frames of the erasure code (7.2, 7.3). */
object Framer {

    /**
     * At most 255 source frames of 16 bytes; a larger document is GATT-only (9.4) and is refused here.
     * [repair] gives the number of repair frames for `k` instead of 7.3's (the state's lead-in, 7.4 rule 4).
     */
    fun legacy(gid16: Int, doc: Int, dv: Int, document: ByteArray, repair: ((Int) -> Int)? = null): List<Frame> {
        require(document.size <= ProtocolConstants.LEGACY_DOCUMENT_MAX_BYTES) {
            "document of ${document.size} bytes exceeds the legacy carrier's ${ProtocolConstants.LEGACY_DOCUMENT_MAX_BYTES}"
        }
        return coded(Carrier.LEGACY, gid16, doc, dv, document, ProtocolConstants.LEGACY_FRAME_DATA_BYTES, repair)
    }

    /** [fs] is the frame data size for this document version; lower than 233 when the stack refuses full frames (7.1). */
    fun extended(gid16: Int, doc: Int, dv: Int, document: ByteArray, fs: Int = ProtocolConstants.EXTENDED_FRAME_MAX_DATA_BYTES): List<Frame> {
        require(fs in 1..ProtocolConstants.EXTENDED_FRAME_MAX_DATA_BYTES) { "fs out of range" }
        require(document.size <= 0xFFFF) { "document too large for a frame's len" }
        return coded(Carrier.EXTENDED, gid16, doc, dv, document, fs)
    }

    /** Source frames first, then the repair frames; one pass of the document is all of them (7.4). */
    private fun coded(carrier: Carrier, gid16: Int, doc: Int, dv: Int, document: ByteArray, size: Int, repairFor: ((Int) -> Int)? = null): List<Frame> {
        val k = maxOf(1, (document.size + size - 1) / size)
        val source = List(k) { i -> document.copyOfRange(i * size, minOf((i + 1) * size, document.size)).copyOf(size) }
        val r = repairFor?.invoke(k)?.coerceIn(0, maxOf(0, ReedSolomon.MAX_FRAMES - k)) ?: ReedSolomon.repairCount(k)
        val repair = ReedSolomon.encode(source, r)
        return (source + repair).mapIndexed { idx, data -> Frame(carrier, gid16, doc, dv, k, idx, size, document.size, data) }
    }
}
