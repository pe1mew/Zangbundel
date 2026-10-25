// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.payload

import nl.pe1mew.zangbundel.core.model.GatheringId
import nl.pe1mew.zangbundel.core.protocol.Crypto
import nl.pe1mew.zangbundel.core.protocol.ProtocolConstants
import nl.pe1mew.zangbundel.core.protocol.ProtocolError

/**
 * Sealed payloads and write framing (ble-protocol 8.4, FR-BLE-17).
 *
 * Every value written to Join, Request, Control or Transfer starts with a framing byte: `0x00`
 * plaintext CBOR, `0x01` a libsodium sealed box to the leader's `ek`. Join, Request and Control are
 * always sealed; Transfer is sealed exactly when it names document 8. Every sealed plaintext carries
 * the gathering's `gid`, and a mismatch is refused. The Join response and document 8 go the other
 * way, sealed to the singer's `xk`.
 *
 * Errors carry the 8.3 code the leader answers with: `0x8D` for a value that cannot be opened or an
 * unknown framing byte, `0x83` for a payload that opens but is malformed, framed the wrong way for
 * its characteristic (8.4 does not name a code for that), or bound to another gathering.
 */
object Sealed {
    const val PLAINTEXT: Byte = ProtocolConstants.FRAMING_PLAINTEXT.toByte()
    const val SEALED: Byte = ProtocolConstants.FRAMING_SEALED.toByte()

    /** A sealed write must fit one characteristic value: framing byte + 48 bytes of seal + plaintext. */
    const val MAX_SEALED_PLAINTEXT = PayloadLimits.GATT_VALUE - 1 - Crypto.SEAL_OVERHEAD_BYTES

    // ---- singer side: writing ----

    fun join(request: JoinRequest, ek: ByteArray, crypto: Crypto): ByteArray = seal(request.encode(), ek, crypto)
    fun request(write: RequestWrite, ek: ByteArray, crypto: Crypto): ByteArray = seal(write.encode(), ek, crypto)
    fun control(command: ControlCommand, ek: ByteArray, crypto: Crypto): ByteArray = seal(command.encode(), ek, crypto)

    /** Plaintext for documents 1–7, sealed for document 8 (which needs [ek]). */
    fun transfer(request: TransferRequest, ek: ByteArray?, crypto: Crypto): ByteArray =
        if (request.doc == DocumentType.REQUESTS) seal(request.encode(), requireNotNull(ek) { "document 8 is sealed to ek" }, crypto)
        else byteArrayOf(PLAINTEXT) + request.encode()

    // ---- leader side: opening writes ----

    fun openJoin(value: ByteArray, ek: Crypto.BoxKeyPair, gid: GatheringId, crypto: Crypto): JoinRequest =
        payload { JoinRequest.decode(openSealed(value, ek, crypto)) }.also { bind(it.gid, gid) }

    fun openRequest(value: ByteArray, ek: Crypto.BoxKeyPair, gid: GatheringId, crypto: Crypto): RequestWrite =
        payload { RequestWrite.decode(openSealed(value, ek, crypto)) }.also { bind(it.gid, gid) }

    fun openControl(value: ByteArray, ek: Crypto.BoxKeyPair, gid: GatheringId, crypto: Crypto): ControlCommand =
        payload { ControlCommand.decode(openSealed(value, ek, crypto)) }.also { bind(it.gid, gid) }

    fun openTransfer(value: ByteArray, ek: Crypto.BoxKeyPair, gid: GatheringId, crypto: Crypto): TransferRequest {
        if (value.isEmpty()) throw ProtocolError(ProtocolConstants.ERR_SEALED, "no framing byte")
        return when (value[0]) {
            PLAINTEXT -> {
                checkSize(value)
                val request = payload { TransferRequest.decode(value.copyOfRange(1, value.size)) }
                if (request.doc == DocumentType.REQUESTS) throw ProtocolError(ProtocolConstants.ERR_MALFORMED, "document 8 must be requested sealed")
                request
            }
            SEALED -> {
                val request = payload { TransferRequest.decode(openSealed(value, ek, crypto)) }
                if (request.doc != DocumentType.REQUESTS) throw ProtocolError(ProtocolConstants.ERR_MALFORMED, "only document 8 is requested sealed")
                bind(request.gid, gid)
                request
            }
            else -> throw ProtocolError(ProtocolConstants.ERR_SEALED, "unknown framing byte")
        }
    }

    // ---- reads sealed to the singer ----

    /** Join response read value: `0x01` ‖ sealed to the singer's `xk` (8.4). */
    fun joinResponse(response: JoinResponse, xk: ByteArray, crypto: Crypto): ByteArray = seal(response.encode(), xk, crypto)

    fun openJoinResponse(value: ByteArray, xk: Crypto.BoxKeyPair, crypto: Crypto): JoinResponse =
        payload { JoinResponse.decode(openSealed(value, xk, crypto)) }

    /**
     * Document 8 for a delegate: the Transfer stream with `enc` = 2 (9.7) is the DEFLATE-compressed
     * document sealed to the requester's `xk`. Returns the stream and the `dh` for the HEADER. `dh`
     * is taken over the inflated bytes, as 8.4 says for this document; section 5 defines `dh` over
     * framed bytes, a conflict recorded as ble-protocol Q20.
     */
    fun requestsStream(document: RequestsDocument, xk: ByteArray, crypto: Crypto): Pair<ByteArray, ByteArray> {
        val bytes = document.encode()
        val stream = crypto.seal(Deflate.compress(bytes), xk)
        return stream to DocEntry.hashOf(bytes, crypto)
    }

    fun openRequestsStream(stream: ByteArray, dh: ByteArray, xk: Crypto.BoxKeyPair, crypto: Crypto): RequestsDocument {
        if (stream.size > PayloadLimits.DOCUMENT_FRAMED + Crypto.SEAL_OVERHEAD_BYTES) throw PayloadException("document 8 stream too large")
        val compressed = crypto.open(stream, xk) ?: throw PayloadException("document 8 cannot be opened")
        val bytes = Deflate.inflate(compressed, PayloadLimits.DOCUMENT_DECODED)
        if (!DocEntry.hashOf(bytes, crypto).contentEquals(dh)) throw PayloadException("document 8 does not match dh")
        return RequestsDocument.decode(bytes)
    }

    // ---- shared ----

    private fun seal(plaintext: ByteArray, recipient: ByteArray, crypto: Crypto): ByteArray {
        require(plaintext.size <= MAX_SEALED_PLAINTEXT) { "sealed plaintext of ${plaintext.size} bytes exceeds $MAX_SEALED_PLAINTEXT" }
        require(recipient.size == FieldSizes.KEY) { "recipient key must be 32 bytes" }
        return byteArrayOf(SEALED) + crypto.seal(plaintext, recipient)
    }

    private fun openSealed(value: ByteArray, keys: Crypto.BoxKeyPair, crypto: Crypto): ByteArray {
        if (value.isEmpty()) throw ProtocolError(ProtocolConstants.ERR_SEALED, "no framing byte")
        checkSize(value)
        when (value[0]) {
            SEALED -> {}
            PLAINTEXT -> throw ProtocolError(ProtocolConstants.ERR_MALFORMED, "this value must be sealed")
            else -> throw ProtocolError(ProtocolConstants.ERR_SEALED, "unknown framing byte")
        }
        return crypto.open(value.copyOfRange(1, value.size), keys)
            ?: throw ProtocolError(ProtocolConstants.ERR_SEALED, "sealed payload cannot be opened")
    }

    private fun checkSize(value: ByteArray) {
        if (value.size > PayloadLimits.GATT_VALUE) throw ProtocolError(ProtocolConstants.ERR_MALFORMED, "value over ${PayloadLimits.GATT_VALUE} bytes")
    }

    private fun bind(found: GatheringId?, expected: GatheringId) {
        if (found != expected) throw ProtocolError(ProtocolConstants.ERR_MALFORMED, "payload bound to another gathering")
    }

    /** Payload errors after opening become `0x83`. */
    private inline fun <T> payload(block: () -> T): T = try {
        block()
    } catch (e: PayloadException) {
        throw ProtocolError(ProtocolConstants.ERR_MALFORMED, e.message ?: "malformed payload")
    }
}
