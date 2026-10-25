// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.beacon

import nl.pe1mew.zangbundel.core.model.GatheringId
import nl.pe1mew.zangbundel.core.protocol.ProtocolConstants
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** The discovery beacon of ble-protocol 6.1: 10 field bytes plus the (truncated) gathering name. */
class Beacon(
    val ver: Int,
    val gid: GatheringId,
    val seq: Int,
    val flags: Int,
    val pos: Int,
    val name: String,
) {
    init {
        require(ver in 0..0xFF && seq in 0..0xFFFF && flags in 0..0xFF && pos in 0..0xFF) { "beacon field out of range" }
    }

    fun has(flag: Int): Boolean = flags and flag != 0
    val ended: Boolean get() = has(ProtocolConstants.FLAG_ENDED)
    val relay: Boolean get() = has(ProtocolConstants.FLAG_RELAY)
    val busy: Boolean get() = has(ProtocolConstants.FLAG_BUSY)
    val codeRequired: Boolean get() = has(ProtocolConstants.FLAG_CODE)

    /** The 10 field bytes, big-endian (section 2). */
    fun fields(): ByteArray {
        val b = ByteArray(FIELD_BYTES)
        b[0] = ver.toByte()
        gid.bytes.copyInto(b, 1)
        b[6] = (seq ushr 8).toByte(); b[7] = seq.toByte()
        b[8] = flags.toByte()
        b[9] = pos.toByte()
        return b
    }

    override fun toString(): String = "Beacon(ver=$ver $gid seq=$seq flags=0x%02x pos=$pos \"$name\")".format(flags)

    companion object {
        const val FIELD_BYTES = 10

        internal fun fromFields(b: ByteArray, name: String) = Beacon(
            ver = b[0].toInt() and 0xFF,
            gid = GatheringId(b.copyOfRange(1, 6)),
            seq = ((b[6].toInt() and 0xFF) shl 8) or (b[7].toInt() and 0xFF),
            flags = b[8].toInt() and 0xFF,
            pos = b[9].toInt() and 0xFF,
            name = name,
        )
    }
}

/**
 * Beacon encodings (ble-protocol 6.2, 6.3). Everything decoded comes from the air and is untrusted:
 * the decoders return null for anything that is not a well-formed beacon and never throw.
 */
object BeaconCodec {
    /** Encoding A: manufacturer data after the company identifier, at most 10 + 17 bytes (6.2). */
    const val NAME_BYTES_A = 17
    /** Encoding B: local name `"ZB"` + Base32 of the fields + `" "` + at most 10 name bytes (6.3). */
    const val NAME_BYTES_B = 10
    private const val PREFIX_B = "ZB"

    /**
     * The manufacturer-data payload of encoding A (Android leader). The transport puts it after the
     * company identifier `0xFFFF` in the scan response; the advertisement itself carries only the
     * flags and the service UUID, never the device name.
     */
    fun encodeA(beacon: Beacon): ByteArray = beacon.fields() + truncateUtf8(beacon.name, NAME_BYTES_A)

    /**
     * Decodes encoding A. Data frames (magic `0xD1`, `0xD2`) and the radio check's test marker
     * (`0x7F`) are not beacons; the first byte of a beacon is its protocol major version (7.2).
     */
    fun decodeA(payload: ByteArray): Beacon? {
        if (payload.size < Beacon.FIELD_BYTES || payload.size > Beacon.FIELD_BYTES + NAME_BYTES_A) return null
        val ver = payload[0].toInt() and 0xFF
        if (ver == 0 || ver >= NOT_A_VERSION) return null
        val name = utf8(payload.copyOfRange(Beacon.FIELD_BYTES, payload.size)) ?: return null
        return Beacon.fromFields(payload, name)
    }

    /** Encoding B (iOS leader): the local name; at most 29 bytes (6.3). */
    fun encodeB(beacon: Beacon): String {
        val name = String(truncateUtf8(beacon.name, NAME_BYTES_B), Charsets.UTF_8)
        return PREFIX_B + Base32.encode(beacon.fields()) + " " + name
    }

    fun decodeB(localName: String): Beacon? {
        if (!localName.startsWith(PREFIX_B) || localName.length < PREFIX_B.length + Base32.FIELDS_CHARS) return null
        val fields = Base32.decode(localName.substring(PREFIX_B.length, PREFIX_B.length + Base32.FIELDS_CHARS)) ?: return null
        val rest = localName.substring(PREFIX_B.length + Base32.FIELDS_CHARS)
        val name = when {
            rest.isEmpty() -> ""
            rest.startsWith(" ") -> rest.substring(1)
            else -> return null
        }
        if (name.toByteArray(Charsets.UTF_8).size > NAME_BYTES_B) return null
        val ver = fields[0].toInt() and 0xFF
        if (ver == 0 || ver >= NOT_A_VERSION) return null
        return Beacon.fromFields(fields, name)
    }

    /** The UTF-8 bytes of [text], cut at a character boundary to at most [maxBytes] (6.1). */
    fun truncateUtf8(text: String, maxBytes: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val bytes = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8)
            if (out.size() + bytes.size > maxBytes) break
            out.write(bytes)
            i += Character.charCount(cp)
        }
        return out.toByteArray()
    }

    private fun utf8(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (e: java.nio.charset.CharacterCodingException) {
        null
    }

    /** First byte values that are frame magics or test markers, never versions (7.2, SP-23). */
    private const val NOT_A_VERSION = 0x7F
}

/** RFC 4648 Base32, upper case, without padding; just enough for the 10 beacon field bytes. */
internal object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    /** 10 bytes = 80 bits = 16 characters, no padding needed. */
    const val FIELDS_CHARS = 16

    fun encode(bytes: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                sb.append(ALPHABET[(buffer ushr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) sb.append(ALPHABET[(buffer shl (5 - bits)) and 31])
        return sb.toString()
    }

    fun decode(text: String): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in text) {
            val v = ALPHABET.indexOf(c)
            if (v < 0) return null
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                out.write((buffer ushr (bits - 8)) and 0xFF)
                bits -= 8
            }
        }
        return out.toByteArray()
    }
}
