// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.gatt

import nl.pe1mew.zangbundel.core.protocol.payload.ByteRange
import nl.pe1mew.zangbundel.core.protocol.payload.PayloadException
import nl.pe1mew.zangbundel.core.protocol.payload.TransferHeader

/**
 * The Data notifications of a transfer (ble-protocol 9.7): the HEADER, in as many `0x01`
 * notifications as the MTU needs (v0.23), DATA chunks of the requested ranges concatenated in
 * order, END with the chunk count, or ERROR ending the transfer.
 */
object TransferStream {
    const val DATA: Byte = 0x02
    const val END: Byte = 0x03
    const val ERROR: Byte = 0x7F

    /**
     * ATT header (3) plus type and chunk index (3): a DATA notification carries MTU − 6 bytes (9.7),
     * and at most 509, because an attribute value is at most 512 bytes and Android cuts a longer
     * notification to 512 (found on the phones 2026-10-25: at MTU 517 every full chunk lost 2 bytes).
     */
    fun chunkSize(mtu: Int): Int {
        require(mtu >= MIN_MTU) { "MTU is at least $MIN_MTU" }
        return minOf(mtu - 6, MAX_VALUE - 3)
    }

    /** Every notification of one transfer: HEADER parts, DATA chunks, END (9.7). [ranges] are clamped already. */
    fun notifications(header: TransferHeader, stream: ByteArray, mtu: Int): List<ByteArray> {
        val bytes = concatenate(stream, header.ranges)
        val size = chunkSize(mtu)
        val chunks = (bytes.indices step size).map { bytes.copyOfRange(it, minOf(it + size, bytes.size)) }
        require(chunks.size <= 0xFFFF) { "too many chunks for a u16 index" }
        // v0.23 (Q22): the HEADER's CBOR in parts of MTU − 4, at most 511 bytes, each behind the type byte.
        val cbor = header.encode().let { it.copyOfRange(1, it.size) }
        val part = minOf(mtu - 4, MAX_VALUE - 1)
        val headerParts = (cbor.indices step part).map { byteArrayOf(TransferHeader.TYPE) + cbor.copyOfRange(it, minOf(it + part, cbor.size)) }
        return headerParts +
            chunks.mapIndexed { i, c -> byteArrayOf(DATA, (i ushr 8).toByte(), i.toByte()) + c } +
            byteArrayOf(END, (chunks.size ushr 8).toByte(), chunks.size.toByte())
    }

    fun error(code: Int): ByteArray = byteArrayOf(ERROR, code.toByte())

    /** The requested ranges of [stream] in order: what the DATA chunks carry. */
    fun concatenate(stream: ByteArray, ranges: List<ByteRange>): ByteArray {
        val out = ByteArray(ranges.sumOf { it.len })
        var at = 0
        for (r in ranges) { stream.copyInto(out, at, r.off, r.off + r.len); at += r.len }
        return out
    }

    /**
     * The singer's side: collects one transfer's notifications. [accept] returns true once the
     * transfer has ended, by END ([result]) or ERROR ([errorCode]). Untrusted input: a DATA chunk out
     * of order, more bytes than the HEADER's ranges announce, or a wrong END count fails the transfer.
     */
    class Receiver(private val maxBytes: Int) {
        var header: TransferHeader? = null
            private set
        var errorCode: Int? = null
            private set
        /** Why the transfer failed, for diagnostics. */
        var failure: String? = null
            private set
        private var expected = 0
        private val headerBytes = java.io.ByteArrayOutputStream()
        private var next = 0
        private var buffer = ByteArray(0)
        private var filled = 0
        private var done = false

        fun accept(notification: ByteArray): Boolean {
            if (done) return true
            if (notification.isEmpty()) return fail("empty notification")
            when (notification[0]) {
                TransferHeader.TYPE -> {
                    if (header != null) return fail("HEADER part after the HEADER was complete")
                    headerBytes.write(notification, 1, notification.size - 1)
                    if (headerBytes.size() > MAX_HEADER_BYTES) return fail("HEADER over $MAX_HEADER_BYTES bytes")
                    // Parts are appended until the CBOR map decodes (v0.23); a definite-length map never decodes early.
                    val h = try { TransferHeader.decode(byteArrayOf(TransferHeader.TYPE) + headerBytes.toByteArray()) } catch (e: PayloadException) { return false }
                    expected = h.ranges.sumOf { it.len }
                    if (expected > maxBytes) return fail("HEADER announces $expected bytes")
                    header = h
                    buffer = ByteArray(expected)
                }
                DATA -> {
                    if (header == null || notification.size < 3) return fail("DATA before a complete HEADER, or too short")
                    val index = ((notification[1].toInt() and 0xFF) shl 8) or (notification[2].toInt() and 0xFF)
                    val n = notification.size - 3
                    if (index != next) return fail("DATA $index where $next was due")
                    if (filled + n > expected) return fail("DATA beyond the $expected bytes announced")
                    notification.copyInto(buffer, filled, 3, notification.size)
                    filled += n
                    next++
                }
                END -> {
                    if (header == null || notification.size != 3) return fail("END before a complete HEADER, or malformed")
                    val count = ((notification[1].toInt() and 0xFF) shl 8) or (notification[2].toInt() and 0xFF)
                    if (count != next || filled != expected) return fail("END says $count chunks, $next came with $filled of $expected bytes")
                    done = true
                }
                ERROR -> {
                    errorCode = if (notification.size == 2) notification[1].toInt() and 0xFF else MALFORMED
                    failure = "ERROR from the leader"
                    done = true
                }
                else -> return fail("unknown type ${notification[0]}")
            }
            return done
        }

        /** The bytes of each requested range, once the transfer ended with END; null otherwise. */
        fun result(): List<Pair<ByteRange, ByteArray>>? {
            val h = header ?: return null
            if (!done || errorCode != null) return null
            var at = 0
            return h.ranges.map { r -> (r to buffer.copyOfRange(at, at + r.len)).also { at += r.len } }
        }

        private fun fail(why: String): Boolean {
            errorCode = MALFORMED
            failure = why
            done = true
            return true
        }
    }

    const val MIN_MTU = 23
    /** A HEADER with 32 ranges of the largest offsets is under 300 bytes; more is not a HEADER. */
    const val MAX_HEADER_BYTES = 512
    /** The largest attribute value (Bluetooth Core, ATT): no notification is longer. */
    const val MAX_VALUE = 512
    private const val MALFORMED = 0x83
}
