// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.frame

import nl.pe1mew.zangbundel.core.protocol.ProtocolConstants

/** The two broadcast carriers of ble-protocol 7.1. */
enum class Carrier(val magic: Int, val headerBytes: Int) {
    LEGACY(ProtocolConstants.MAGIC_LEGACY_FRAME, LEGACY_HEADER_BYTES),
    EXTENDED(ProtocolConstants.MAGIC_EXTENDED_FRAME, ProtocolConstants.EXTENDED_FRAME_HEADER_BYTES),
}

private const val LEGACY_HEADER_BYTES = 8

/**
 * One data frame of ble-protocol 7.2, as carried in manufacturer data after the company identifier.
 * [frameSize] is the number of document bytes per frame: 16 on the legacy carrier, `fs` on the
 * extended carrier. Frame [idx] carries document bytes `idx × frameSize` onwards; only the last
 * frame may be shorter.
 */
class Frame(
    val carrier: Carrier,
    val gid16: Int,
    val doc: Int,
    val dv: Int,
    val total: Int,
    val idx: Int,
    val frameSize: Int,
    data: ByteArray,
) {
    private val bytes = data.copyOf()
    val data: ByteArray get() = bytes.copyOf()
    val dataSize: Int get() = bytes.size
    internal fun raw(): ByteArray = bytes

    init {
        require(gid16 in 0..0xFFFF) { "gid16 out of range" }
        require(doc in 0..0xFF) { "doc out of range" }
        require(dv in 0..0xFFFF) { "dv out of range" }
        require(idx in 0 until total) { "idx $idx outside 0 until $total" }
        when (carrier) {
            Carrier.LEGACY -> {
                require(total in 1..ProtocolConstants.LEGACY_MAX_FRAMES) { "legacy total out of range" }
                require(frameSize == ProtocolConstants.LEGACY_FRAME_DATA_BYTES) { "legacy frames carry 16 bytes" }
            }
            Carrier.EXTENDED -> {
                require(total in 1..0xFFFF) { "extended total out of range" }
                require(frameSize in 1..ProtocolConstants.EXTENDED_FRAME_MAX_DATA_BYTES) { "fs out of range" }
            }
        }
        val last = idx == total - 1
        require(if (last) bytes.size <= frameSize && (bytes.isNotEmpty() || total == 1) else bytes.size == frameSize) {
            "frame $idx of $total carries ${bytes.size} bytes with frame size $frameSize"
        }
    }

    override fun toString(): String = "Frame($carrier doc=$doc dv=$dv ${idx + 1}/$total, ${bytes.size} B)"
}

/** Encodes and decodes the frame layouts of ble-protocol 7.2 (big-endian, section 2). */
object FrameCodec {

    fun encode(frame: Frame): ByteArray {
        val out = ByteArray(frame.carrier.headerBytes + frame.dataSize)
        out[0] = frame.carrier.magic.toByte()
        u16(out, 1, frame.gid16)
        out[3] = frame.doc.toByte()
        u16(out, 4, frame.dv)
        val dataAt = when (frame.carrier) {
            Carrier.LEGACY -> {
                out[6] = frame.total.toByte()
                out[7] = frame.idx.toByte()
                8
            }
            Carrier.EXTENDED -> {
                u16(out, 6, frame.total)
                u16(out, 8, frame.idx)
                u16(out, 10, frame.frameSize)
                12
            }
        }
        frame.raw().copyInto(out, dataAt)
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
        val maxSize = when (carrier) {
            Carrier.LEGACY -> LEGACY_HEADER_BYTES + ProtocolConstants.LEGACY_FRAME_DATA_BYTES
            Carrier.EXTENDED -> ProtocolConstants.EXTENDED_FRAME_MAX_BYTES
        }
        if (payload.size < carrier.headerBytes || payload.size > maxSize) return null
        val gid16 = u16(payload, 1)
        val doc = payload[3].toInt() and 0xFF
        val dv = u16(payload, 4)
        val total: Int
        val idx: Int
        val frameSize: Int
        when (carrier) {
            Carrier.LEGACY -> {
                total = payload[6].toInt() and 0xFF
                idx = payload[7].toInt() and 0xFF
                frameSize = ProtocolConstants.LEGACY_FRAME_DATA_BYTES
            }
            Carrier.EXTENDED -> {
                total = u16(payload, 6)
                idx = u16(payload, 8)
                frameSize = u16(payload, 10)
            }
        }
        val data = payload.copyOfRange(carrier.headerBytes, payload.size)
        return try {
            Frame(carrier, gid16, doc, dv, total, idx, frameSize, data)
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

/** Splits a document into frames for one carrier (ble-protocol 7.2). */
object Framer {

    /** At most 255 frames of 16 bytes; a larger document is GATT-only (9.4) and is refused here. */
    fun legacy(gid16: Int, doc: Int, dv: Int, document: ByteArray): List<Frame> {
        require(document.size <= ProtocolConstants.LEGACY_DOCUMENT_MAX_BYTES) {
            "document of ${document.size} bytes exceeds the legacy carrier's ${ProtocolConstants.LEGACY_DOCUMENT_MAX_BYTES}"
        }
        return split(Carrier.LEGACY, gid16, doc, dv, document, ProtocolConstants.LEGACY_FRAME_DATA_BYTES)
    }

    /** [fs] is the frame data size for this document version; lower than 235 when the stack refuses full frames (7.1). */
    fun extended(gid16: Int, doc: Int, dv: Int, document: ByteArray, fs: Int = ProtocolConstants.EXTENDED_FRAME_MAX_DATA_BYTES): List<Frame> {
        require(fs in 1..ProtocolConstants.EXTENDED_FRAME_MAX_DATA_BYTES) { "fs out of range" }
        return split(Carrier.EXTENDED, gid16, doc, dv, document, fs)
    }

    private fun split(carrier: Carrier, gid16: Int, doc: Int, dv: Int, document: ByteArray, size: Int): List<Frame> {
        val total = maxOf(1, (document.size + size - 1) / size)
        return List(total) { i ->
            val from = i * size
            Frame(carrier, gid16, doc, dv, total, i, size, document.copyOfRange(from, minOf(from + size, document.size)))
        }
    }
}
