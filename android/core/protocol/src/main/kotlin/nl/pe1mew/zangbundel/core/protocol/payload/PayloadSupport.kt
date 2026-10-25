// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.payload

import nl.pe1mew.zangbundel.core.model.ContentHash
import nl.pe1mew.zangbundel.core.model.GatheringId
import nl.pe1mew.zangbundel.core.model.LibraryId
import nl.pe1mew.zangbundel.core.model.SongId
import nl.pe1mew.zangbundel.core.model.SongRef
import nl.pe1mew.zangbundel.core.protocol.cbor.Cbor
import nl.pe1mew.zangbundel.core.protocol.cbor.CborBytes
import nl.pe1mew.zangbundel.core.protocol.cbor.CborException
import nl.pe1mew.zangbundel.core.protocol.cbor.CborInt
import nl.pe1mew.zangbundel.core.protocol.cbor.CborMap
import nl.pe1mew.zangbundel.core.protocol.cbor.CborText
import nl.pe1mew.zangbundel.core.protocol.cbor.CborValue

/** A payload that is not valid for the protocol: malformed CBOR, a required key missing, a value out of range. */
class PayloadException(message: String) : Exception(message)

/** Size limits of the payloads (ble-protocol sections 8, 9). */
object PayloadLimits {
    /** A GATT characteristic value (2.2). */
    const val GATT_VALUE = 512
    /** The signed body of the state record (9.4). */
    const val STATE_BODY = 440
    /** A framed document: Control op 8 refuses uploads over 64 kB (9.8). */
    const val DOCUMENT_FRAMED = 64 * 1024
    /**
     * A document after inflating. Not stated by the protocol: a song file may itself be 64 KiB
     * (song-format 5.5), so its document needs room for the reference and licence next to it.
     */
    const val DOCUMENT_DECODED = 64 * 1024 + 2048
}

/** Text lengths are in characters; implementations truncate at character boundaries (9.1). */
fun truncateChars(text: String, maxChars: Int): String {
    if (text.codePointCount(0, text.length) <= maxChars) return text
    return text.substring(0, text.offsetByCodePoints(0, maxChars))
}

/** Runs a decoder and turns every refusal into a [PayloadException]. */
internal inline fun <T> decoding(what: String, block: () -> T): T = try {
    block()
} catch (e: CborException) {
    throw PayloadException("$what: ${e.message}")
} catch (e: IllegalArgumentException) {
    throw PayloadException("$what: ${e.message}")
}

internal fun decodeMap(bytes: ByteArray, maxSize: Int): CborMap = Cbor.decodeMap(bytes, maxSize)

// ---- strict readers: a known key present with the wrong type or size is an error ----

internal fun CborMap.uint(key: Long, max: Long = Long.MAX_VALUE): Long? {
    val v = get(key) ?: return null
    val n = (v as? CborInt)?.value ?: throw CborException("key $key is not an integer")
    if (n < 0 || n > max) throw CborException("key $key value $n outside 0..$max")
    return n
}

internal fun CborMap.requireUint(key: Long, max: Long = Long.MAX_VALUE): Long =
    uint(key, max) ?: throw CborException("required key $key missing")

internal fun CborMap.bytesOf(key: Long, size: Int? = null, maxSize: Int = Int.MAX_VALUE): ByteArray? {
    val v = get(key) ?: return null
    val b = (v as? CborBytes)?.value ?: throw CborException("key $key is not a byte string")
    if (size != null && b.size != size) throw CborException("key $key has ${b.size} bytes, expected $size")
    if (b.size > maxSize) throw CborException("key $key has ${b.size} bytes, at most $maxSize")
    return b
}

internal fun CborMap.requireBytesOf(key: Long, size: Int? = null, maxSize: Int = Int.MAX_VALUE): ByteArray =
    bytesOf(key, size, maxSize) ?: throw CborException("required key $key missing")

internal fun CborMap.textOf(key: Long, maxChars: Int = Int.MAX_VALUE): String? {
    val v = get(key) ?: return null
    val t = (v as? CborText)?.value ?: throw CborException("key $key is not a text string")
    return truncateChars(t, maxChars)
}

internal fun CborMap.requireTextOf(key: Long, maxChars: Int = Int.MAX_VALUE): String =
    textOf(key, maxChars) ?: throw CborException("required key $key missing")

internal fun CborMap.boolOf(key: Long): Boolean? {
    val v = get(key) ?: return null
    return bool(key) ?: throw CborException("key $key is not a boolean")
}

internal fun CborMap.mapAt(key: Long): CborMap? {
    val v = get(key) ?: return null
    return v as? CborMap ?: throw CborException("key $key is not a map")
}

internal fun CborMap.arrayAt(key: Long): List<CborValue>? {
    val v = get(key) ?: return null
    return array(key) ?: throw CborException("key $key is not an array")
}

internal fun CborValue.asMap(what: String): CborMap = this as? CborMap ?: throw CborException("$what is not a map")

/** Builds a payload map from optional entries, leaving out the absent ones. */
internal fun payload(vararg entries: Pair<Long, CborValue?>): CborMap =
    CborMap(entries.mapNotNull { (k, v) -> v?.let { CborInt(k) to it } })

internal fun Long.cbor(): CborValue = CborInt(this)
internal fun Int.cbor(): CborValue = CborInt(toLong())
internal fun ByteArray.cbor(): CborValue = CborBytes(this)
internal fun String.cbor(): CborValue = CborText(this)

internal fun GatheringId.cbor(): CborValue = CborBytes(bytes)
internal fun CborMap.requireGid(key: Long): GatheringId = GatheringId(requireBytesOf(key, GatheringId.SIZE))

/** Field sizes of section 5. */
object FieldSizes {
    const val SID = 8
    const val TOKEN = 16
    const val KEY = 32
    const val SIGNATURE = 64
    const val DOC_HASH = 8
    const val REQUEST_ID = 4
    const val COMMAND_ID = 4
}

/** The song reference `ref` of section 5: `{1: lib, 2: song, 3: hash, 4: rev?}`. Ids are checked against song-format 3.2. */
object SongRefCodec {
    fun toCbor(ref: SongRef): CborMap = payload(
        1L to ref.library.value.cbor(),
        2L to ref.song.value.cbor(),
        3L to ref.hash.bytes.cbor(),
        4L to ref.revision?.cbor(),
    )

    fun fromCbor(value: CborValue): SongRef {
        val m = value.asMap("ref")
        return SongRef(
            library = LibraryId(m.requireTextOf(1)),
            song = SongId(m.requireTextOf(2)),
            hash = ContentHash(m.requireBytesOf(3, ContentHash.SIZE)),
            revision = m.textOf(4),
        )
    }
}
