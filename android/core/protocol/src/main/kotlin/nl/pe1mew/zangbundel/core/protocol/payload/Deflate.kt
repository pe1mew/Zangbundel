// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.payload

import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Raw DEFLATE (RFC 1951, no zlib or gzip wrapper) for documents 3–7 (ble-protocol 7.3), with an
 * output cap on inflation (architecture section 7, NFR-SEC-04): a stream that would expand past the
 * cap is refused while inflating, before the excess is held in memory.
 */
object Deflate {
    /** `enc` values of the state manifest (9.4). */
    const val ENC_RAW = 0
    const val ENC_DEFLATE = 1

    /** 7.3: compress only when that saves at least 10 %. */
    private const val MIN_SAVING_PERCENT = 10

    fun compress(bytes: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        try {
            deflater.setInput(bytes)
            deflater.finish()
            val out = ByteArrayOutputStream(bytes.size / 2 + 16)
            val buffer = ByteArray(4096)
            while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    /**
     * The framed form of a document and its `enc`: DEFLATE when it saves at least 10 %, raw otherwise.
     */
    fun frame(document: ByteArray): Pair<Int, ByteArray> {
        val compressed = compress(document)
        return if (compressed.size.toLong() * 100 <= document.size.toLong() * (100 - MIN_SAVING_PERCENT)) ENC_DEFLATE to compressed
        else ENC_RAW to document
    }

    /** Inverse of [frame]: the document bytes, inflating with [maxOutput] as the cap. */
    fun unframe(enc: Int, framed: ByteArray, maxOutput: Int): ByteArray = when (enc) {
        ENC_RAW -> framed.also { if (it.size > maxOutput) throw PayloadException("document of ${it.size} bytes exceeds $maxOutput") }
        ENC_DEFLATE -> inflate(framed, maxOutput)
        else -> throw PayloadException("unknown enc $enc")
    }

    /**
     * Inflates a complete raw DEFLATE stream. Throws [PayloadException] when the output would exceed
     * [maxOutput], the stream is malformed or truncated, or bytes follow its end.
     */
    fun inflate(compressed: ByteArray, maxOutput: Int): ByteArray {
        val inflater = Inflater(true)
        try {
            // With nowrap the inflater may need one byte past the stream (java.util.zip.Inflater).
            inflater.setInput(compressed.copyOf(compressed.size + 1))
            val out = ByteArrayOutputStream(minOf(maxOutput, compressed.size * 4 + 64))
            val buffer = ByteArray(4096)
            while (!inflater.finished()) {
                val room = maxOutput - out.size()
                // Ask for one byte more than the room left, so an overrun is seen without holding it.
                val n = inflater.inflate(buffer, 0, minOf(buffer.size, room + 1))
                if (n > room) throw PayloadException("inflated document exceeds $maxOutput bytes")
                out.write(buffer, 0, n)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw PayloadException("truncated or unsupported DEFLATE stream")
                }
            }
            if (inflater.remaining > 1) throw PayloadException("${inflater.remaining - 1} bytes after the DEFLATE stream")
            return out.toByteArray()
        } catch (e: DataFormatException) {
            throw PayloadException("malformed DEFLATE stream")
        } finally {
            inflater.end()
        }
    }
}
