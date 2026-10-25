// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.cbor

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * The protocol's own bounded CBOR codec (architecture A-08, RFC 8949).
 *
 * Encoding uses definite lengths and preferred serialization (the shortest argument encoding);
 * map entries keep their order. Decoding accepts exactly the data model of [CborValue] and rejects
 * indefinite lengths, tags, floats and other simple values, duplicate map keys, nesting deeper than
 * [MAX_DEPTH], invalid UTF-8, integers outside [Long], trailing bytes and any payload larger than
 * the limit the caller passes. Lengths and counts are checked against the remaining input before
 * anything is allocated, so the memory a decode can take is bounded by the payload size.
 */
object Cbor {
    /** Deepest nesting of arrays and maps the decoder accepts. */
    const val MAX_DEPTH = 8

    fun encode(value: CborValue): ByteArray = ByteArrayOutputStream().also { write(it, value) }.toByteArray()

    /**
     * Decodes one data item that must take up all of [bytes].
     * @param maxSize the payload's limit from ble-protocol section 9; checked before decoding starts.
     */
    fun decode(bytes: ByteArray, maxSize: Int): CborValue {
        if (bytes.size > maxSize) throw CborException("payload of ${bytes.size} bytes exceeds the limit of $maxSize")
        val reader = Reader(bytes)
        val value = reader.item(depth = 0)
        if (reader.pos != bytes.size) throw CborException("${bytes.size - reader.pos} trailing bytes")
        return value
    }

    /** Decodes a payload that must be a map, as every protocol payload is (9.1). */
    fun decodeMap(bytes: ByteArray, maxSize: Int): CborMap =
        decode(bytes, maxSize) as? CborMap ?: throw CborException("payload is not a map")

    // ---- encoder ----

    private fun write(out: ByteArrayOutputStream, value: CborValue) {
        when (value) {
            is CborInt ->
                if (value.value >= 0) head(out, MAJOR_UNSIGNED, value.value.toULong())
                else head(out, MAJOR_NEGATIVE, (-1 - value.value).toULong())
            is CborBytes -> {
                head(out, MAJOR_BYTES, value.size.toULong())
                out.write(value.raw())
            }
            is CborText -> {
                val utf8 = value.value.toByteArray(Charsets.UTF_8)
                head(out, MAJOR_TEXT, utf8.size.toULong())
                out.write(utf8)
            }
            is CborArray -> {
                head(out, MAJOR_ARRAY, value.items.size.toULong())
                value.items.forEach { write(out, it) }
            }
            is CborMap -> {
                head(out, MAJOR_MAP, value.entries.size.toULong())
                value.entries.forEach { (k, v) -> write(out, k); write(out, v) }
            }
            is CborBool -> out.write(if (value.value) SIMPLE_TRUE_BYTE else SIMPLE_FALSE_BYTE)
            CborNull -> out.write(SIMPLE_NULL_BYTE)
        }
    }

    /** Initial byte plus the shortest argument (preferred serialization). */
    private fun head(out: ByteArrayOutputStream, major: Int, argument: ULong) {
        val m = major shl 5
        when {
            argument < 24u -> out.write(m or argument.toInt())
            argument <= 0xFFu -> { out.write(m or 24); out.write(argument.toInt()) }
            argument <= 0xFFFFu -> { out.write(m or 25); bigEndian(out, argument, 2) }
            argument <= 0xFFFFFFFFu -> { out.write(m or 26); bigEndian(out, argument, 4) }
            else -> { out.write(m or 27); bigEndian(out, argument, 8) }
        }
    }

    private fun bigEndian(out: ByteArrayOutputStream, v: ULong, n: Int) {
        for (i in n - 1 downTo 0) out.write(((v shr (8 * i)) and 0xFFu).toInt())
    }

    // ---- decoder ----

    private class Reader(private val b: ByteArray) {
        var pos = 0
        private val remaining: Int get() = b.size - pos

        fun item(depth: Int): CborValue {
            val initial = u8()
            val major = initial ushr 5
            val info = initial and 0x1F
            if (major == MAJOR_SIMPLE) return simple(info)
            if (major == MAJOR_TAG) throw CborException("tags are not allowed")
            val argument = argument(info)
            return when (major) {
                MAJOR_UNSIGNED -> CborInt(toLong(argument))
                MAJOR_NEGATIVE -> CborInt(-1 - toLong(argument))
                MAJOR_BYTES -> CborBytes(take(argument))
                MAJOR_TEXT -> CborText(utf8(take(argument)))
                MAJOR_ARRAY -> {
                    val inner = enter(depth)
                    val count = count(argument, bytesPerElement = 1)
                    CborArray(List(count) { item(inner) })
                }
                MAJOR_MAP -> {
                    val inner = enter(depth)
                    val count = count(argument, bytesPerElement = 2)
                    val seen = HashSet<CborValue>(count * 2)
                    val entries = ArrayList<Pair<CborValue, CborValue>>(count)
                    repeat(count) {
                        val key = item(inner)
                        if (!seen.add(key)) throw CborException("duplicate map key $key")
                        entries += key to item(inner)
                    }
                    CborMap(entries)
                }
                else -> throw CborException("major type $major") // unreachable: 0..7 are all handled
            }
        }

        private fun enter(depth: Int): Int {
            val inner = depth + 1
            if (inner > MAX_DEPTH) throw CborException("nesting deeper than $MAX_DEPTH")
            return inner
        }

        private fun simple(info: Int): CborValue = when (info) {
            SIMPLE_FALSE -> CborBool(false)
            SIMPLE_TRUE -> CborBool(true)
            SIMPLE_NULL -> CborNull
            25, 26, 27 -> throw CborException("floating-point values are not allowed")
            INDEFINITE -> throw CborException("unexpected break")
            else -> throw CborException("simple value $info is not allowed")
        }

        private fun argument(info: Int): ULong = when {
            info < 24 -> info.toULong()
            info == 24 -> u8().toULong()
            info == 25 -> bytes(2)
            info == 26 -> bytes(4)
            info == 27 -> bytes(8)
            info == INDEFINITE -> throw CborException("indefinite lengths are not allowed")
            else -> throw CborException("reserved additional information $info")
        }

        private fun bytes(n: Int): ULong {
            if (remaining < n) throw CborException("truncated")
            var v = 0uL
            repeat(n) { v = (v shl 8) or (b[pos++].toInt() and 0xFF).toULong() }
            return v
        }

        private fun u8(): Int {
            if (remaining < 1) throw CborException("truncated")
            return b[pos++].toInt() and 0xFF
        }

        private fun toLong(v: ULong): Long {
            if (v > Long.MAX_VALUE.toULong()) throw CborException("integer out of range")
            return v.toLong()
        }

        /** A string of [length] bytes; checked against the input before allocating. */
        private fun take(length: ULong): ByteArray {
            if (length > remaining.toULong()) throw CborException("length $length exceeds the remaining ${remaining} bytes")
            val n = length.toInt()
            return b.copyOfRange(pos, pos + n).also { pos += n }
        }

        /** An element count; each element takes at least [bytesPerElement] bytes of the remaining input. */
        private fun count(argument: ULong, bytesPerElement: Int): Int {
            if (argument > (remaining / bytesPerElement).toULong()) throw CborException("count $argument exceeds the remaining input")
            return argument.toInt()
        }

        private fun utf8(bytes: ByteArray): String = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            throw CborException("text is not valid UTF-8")
        }
    }

    private const val MAJOR_UNSIGNED = 0
    private const val MAJOR_NEGATIVE = 1
    private const val MAJOR_BYTES = 2
    private const val MAJOR_TEXT = 3
    private const val MAJOR_ARRAY = 4
    private const val MAJOR_MAP = 5
    private const val MAJOR_TAG = 6
    private const val MAJOR_SIMPLE = 7
    private const val SIMPLE_FALSE = 20
    private const val SIMPLE_TRUE = 21
    private const val SIMPLE_NULL = 22
    private const val INDEFINITE = 31
    private const val SIMPLE_FALSE_BYTE = 0xF4
    private const val SIMPLE_TRUE_BYTE = 0xF5
    private const val SIMPLE_NULL_BYTE = 0xF6
}
