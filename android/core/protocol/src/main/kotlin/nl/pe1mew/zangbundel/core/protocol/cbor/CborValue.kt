// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.cbor

/**
 * The CBOR data model the protocol uses (architecture A-08, ble-protocol section 9.1): integers,
 * byte and text strings, arrays, maps, booleans and null. Nothing else exists here, so nothing else
 * can be encoded or decoded.
 */
sealed interface CborValue

/** Unsigned or negative integer within the range of a Kotlin [Long]. */
data class CborInt(val value: Long) : CborValue

class CborBytes(value: ByteArray) : CborValue {
    private val bytes = value.copyOf()
    val value: ByteArray get() = bytes.copyOf()
    val size: Int get() = bytes.size
    internal fun raw(): ByteArray = bytes
    override fun equals(other: Any?): Boolean = other is CborBytes && bytes.contentEquals(other.bytes)
    override fun hashCode(): Int = bytes.contentHashCode()
    override fun toString(): String = "CborBytes(" + bytes.joinToString("") { "%02x".format(it) } + ")"
}

data class CborText(val value: String) : CborValue

data class CborArray(val items: List<CborValue>) : CborValue {
    constructor(vararg items: CborValue) : this(items.toList())
}

/**
 * A map in the order its entries were written. Keys are unique: the decoder rejects duplicates and
 * the constructor refuses them. Protocol payloads use unsigned-integer keys (9.1), so the accessors
 * take a [Long] key.
 */
class CborMap(entries: List<Pair<CborValue, CborValue>>) : CborValue {
    val entries: List<Pair<CborValue, CborValue>> = entries.toList()
    private val index: Map<CborValue, CborValue> = LinkedHashMap<CborValue, CborValue>().also { m ->
        for ((k, v) in this.entries) require(m.put(k, v) == null) { "duplicate map key $k" }
    }

    val size: Int get() = entries.size
    operator fun get(key: Long): CborValue? = index[CborInt(key)]
    operator fun get(key: CborValue): CborValue? = index[key]

    fun int(key: Long): Long? = (get(key) as? CborInt)?.value
    fun bytes(key: Long): ByteArray? = (get(key) as? CborBytes)?.value
    fun text(key: Long): String? = (get(key) as? CborText)?.value
    fun bool(key: Long): Boolean? = (get(key) as? CborBool)?.value
    fun array(key: Long): List<CborValue>? = (get(key) as? CborArray)?.items
    fun map(key: Long): CborMap? = get(key) as? CborMap

    // Required keys (9.1: a decoder MUST reject a payload missing a key marked required).
    // A present key of the wrong type counts as missing.
    fun requireInt(key: Long): Long = int(key) ?: throw missing(key, "integer")
    fun requireBytes(key: Long): ByteArray = bytes(key) ?: throw missing(key, "byte string")
    fun requireText(key: Long): String = text(key) ?: throw missing(key, "text string")
    fun requireBool(key: Long): Boolean = bool(key) ?: throw missing(key, "boolean")
    fun requireArray(key: Long): List<CborValue> = array(key) ?: throw missing(key, "array")
    fun requireMap(key: Long): CborMap = map(key) ?: throw missing(key, "map")

    private fun missing(key: Long, type: String) = CborException("required key $key ($type) missing or of another type")

    override fun equals(other: Any?): Boolean = other is CborMap && entries == other.entries
    override fun hashCode(): Int = entries.hashCode()
    override fun toString(): String = "CborMap($entries)"

    companion object {
        /** Builds a map with unsigned-integer keys, in the given order. */
        fun of(vararg entries: Pair<Long, CborValue>): CborMap = CborMap(entries.map { (k, v) -> CborInt(k) to v })
    }
}

data class CborBool(val value: Boolean) : CborValue

data object CborNull : CborValue

/** Any input the codec refuses. The decoder throws nothing else for any input. */
class CborException(message: String) : Exception(message)
