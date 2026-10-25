// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol

/**
 * A refusal the leader answers with an application error code of ble-protocol 8.3, as the ATT
 * status of the write. [code] is one of the `ERR_` constants in [ProtocolConstants].
 */
class ProtocolError(val code: Int, message: String) : Exception(message) {
    init { require(code in 0x80..0x8E) { "application error codes are 0x80 to 0x8E" } }
    override fun toString(): String = "ProtocolError(0x%02X: %s)".format(code, message)
}
