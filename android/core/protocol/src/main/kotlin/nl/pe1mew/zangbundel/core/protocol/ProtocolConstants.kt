// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol

/** Constants of design/ble-protocol.md kept in one place (architecture section 7, "UUIDs in one constants file"). */
object ProtocolConstants {
    const val PROTOCOL_MAJOR = 1
    const val PROTOCOL_MINOR = 0

    /** Manufacturer-specific data company identifier used by beacon and data frames (6.2, Q1: provisional). */
    const val COMPANY_ID = 0xFFFF

    /** Frame magic bytes (7.2). */
    const val MAGIC_LEGACY_FRAME = 0xD1
    const val MAGIC_EXTENDED_FRAME = 0xD2

    const val LEGACY_FRAME_DATA_BYTES = 16
    const val EXTENDED_FRAME_MAX_DATA_BYTES = 984
    const val LEGACY_DOCUMENT_MAX_BYTES = 255 * LEGACY_FRAME_DATA_BYTES

    /** Beacon flag bits (6.1). */
    const val FLAG_CODE = 1 shl 0
    const val FLAG_REQ_OPEN = 1 shl 1
    const val FLAG_BUSY = 1 shl 2
    const val FLAG_ENDED = 1 shl 3
    const val FLAG_RELAY = 1 shl 4
    const val FLAG_KARAOKE = 1 shl 5
    const val FLAG_BCAST = 1 shl 6
    const val FLAG_EXT = 1 shl 7

    /** GATT base UUID `7e2bXXXX-9f1c-4a7d-b2d4-5a4e47424c45` (8.1). */
    const val UUID_BASE_PREFIX = "7e2b"
    const val UUID_BASE_SUFFIX = "-9f1c-4a7d-b2d4-5a4e47424c45"
    const val CHAR_SERVICE = 0x0001
    const val CHAR_INFO = 0x0002
    const val CHAR_JOIN = 0x0003
    const val CHAR_STATE = 0x0004
    const val CHAR_TRANSFER = 0x0005
    const val CHAR_DATA = 0x0006
    const val CHAR_REQUEST = 0x0007
    const val CHAR_TIME_RESERVED = 0x0008
    const val CHAR_CONTROL = 0x0009

    fun uuidOf(attribute: Int): String = "%s%04x%s".format(UUID_BASE_PREFIX, attribute, UUID_BASE_SUFFIX)

    /** Application error codes (8.3). */
    const val ERR_BAD_TOKEN = 0x80
    const val ERR_REQUESTS_CLOSED = 0x81
    const val ERR_TOO_MANY_PENDING = 0x82
    const val ERR_MALFORMED = 0x83
    const val ERR_BUSY = 0x84
    const val ERR_NOT_SERVED_HERE = 0x85
    const val ERR_UNKNOWN_DOCUMENT = 0x86
    const val ERR_VERSION = 0x87
    const val ERR_ENDED = 0x88
    const val ERR_NOT_DELEGATE = 0x89
    const val ERR_LEADER_LACKS_SONG = 0x8A
    const val ERR_UPLOAD_REJECTED = 0x8B
    const val ERR_RANGE = 0x8C
    const val ERR_SEALED = 0x8D

    /** Write framing byte (8.4). */
    const val FRAMING_PLAINTEXT = 0x00
    const val FRAMING_SEALED = 0x01
}
