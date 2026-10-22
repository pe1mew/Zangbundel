// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.model

/** Library id: song-format section 3.2 syntax, at most 64 characters. */
@JvmInline
value class LibraryId(val value: String) {
    init { require(isValidId(value)) { "invalid library id" } }
    override fun toString(): String = value
}

/** Song id within a library: the file name without `.cho` (song-format section 3.2). */
@JvmInline
value class SongId(val value: String) {
    init { require(isValidId(value)) { "invalid song id" } }
    override fun toString(): String = value
}

private val ID_PATTERN = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")

/** Shared syntax of library ids, song ids, tags and variant groups (song-format section 3.2). */
fun isValidId(value: String): Boolean = value.length in 1..64 && ID_PATTERN.matches(value)

/**
 * Song content hash: the first 16 bytes of SHA-256 over the canonical form
 * (song-format section 8.2, ble-protocol section 5). Matching between devices is by this value only.
 */
class ContentHash(bytes: ByteArray) {
    val bytes: ByteArray = bytes.copyOf()
    init { require(bytes.size == SIZE) { "content hash must be $SIZE bytes" } }
    fun toHex(): String = bytes.joinToString("") { "%02x".format(it) }
    override fun equals(other: Any?): Boolean = other is ContentHash && bytes.contentEquals(other.bytes)
    override fun hashCode(): Int = bytes.contentHashCode()
    override fun toString(): String = "ContentHash(${toHex()})"
    companion object {
        const val SIZE = 16
        fun fromHex(hex: String): ContentHash {
            require(hex.length == SIZE * 2) { "content hash hex must be ${SIZE * 2} characters" }
            return ContentHash(ByteArray(SIZE) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() })
        }
    }
}
