// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.gatt

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withTimeoutOrNull
import nl.pe1mew.zangbundel.core.protocol.Crypto
import nl.pe1mew.zangbundel.core.protocol.ProtocolConstants
import nl.pe1mew.zangbundel.core.protocol.Transport
import nl.pe1mew.zangbundel.core.protocol.payload.ByteRange
import nl.pe1mew.zangbundel.core.protocol.payload.JoinRequest
import nl.pe1mew.zangbundel.core.protocol.payload.JoinResponse
import nl.pe1mew.zangbundel.core.protocol.payload.PayloadLimits
import nl.pe1mew.zangbundel.core.protocol.payload.Sealed
import nl.pe1mew.zangbundel.core.protocol.payload.TransferHeader
import nl.pe1mew.zangbundel.core.protocol.payload.TransferRequest

/**
 * The singer's side of one GATT connection (ble-protocol 9.3, 9.7, 10.2, 10.4): read Info and
 * State, register, and fetch documents or missing ranges. Failures surface as
 * [Transport.GattException]; a transfer the leader ends with ERROR or that breaks off comes back as
 * [Transfer.Failed].
 */
class GattClient(private val connection: Transport.Connection, private val crypto: Crypto) {
    private var data: ReceiveChannel<ByteArray>? = null

    suspend fun readInfo(): ByteArray = connection.read(ProtocolConstants.CHAR_INFO)
    suspend fun readState(): ByteArray = connection.read(ProtocolConstants.CHAR_STATE)

    /** 9.3: write the Join sealed to `ek`, then read the response sealed to [xk] on the same connection. */
    suspend fun join(request: JoinRequest, ek: ByteArray, xk: Crypto.BoxKeyPair): JoinResponse {
        connection.write(ProtocolConstants.CHAR_JOIN, Sealed.join(request, ek, crypto))
        val answer = connection.read(ProtocolConstants.CHAR_JOIN)
        return try {
            Sealed.openJoinResponse(answer, xk, crypto)
        } catch (e: Exception) {
            throw Transport.GattException(ProtocolConstants.ERR_SEALED, "join response cannot be opened: ${e.message}")
        }
    }

    sealed interface Transfer {
        class Done(val header: TransferHeader, val ranges: List<Pair<ByteRange, ByteArray>>) : Transfer
        /** The leader refused the write or ended the transfer with ERROR ([code], 8.3), or it broke off ([code] null); [why] for diagnostics. */
        class Failed(val code: Int?, val why: String) : Transfer
    }

    /**
     * 9.7: subscribe to Data once per connection, write the plaintext Transfer request, collect the
     * notifications until END. A transfer silent for [idleMs] has broken off.
     */
    suspend fun transfer(request: TransferRequest, idleMs: Long = TRANSFER_IDLE_MS): Transfer {
        val channel = data ?: connection.subscribe(ProtocolConstants.CHAR_DATA).also { data = it }
        try {
            connection.write(ProtocolConstants.CHAR_TRANSFER, Sealed.transfer(request, null, crypto))
        } catch (e: Transport.GattException) {
            if (e.status == Transport.CONNECTION_FAILED) throw e
            return Transfer.Failed(e.status, "write refused")
        }
        val receiver = TransferStream.Receiver(PayloadLimits.DOCUMENT_FRAMED)
        while (true) {
            val n = withTimeoutOrNull(idleMs) { channel.receiveCatching().getOrNull() } ?: return Transfer.Failed(null, "silent for $idleMs ms")
            if (receiver.accept(n)) break
        }
        val result = receiver.result() ?: return Transfer.Failed(receiver.errorCode, receiver.failure ?: "no result")
        return Transfer.Done(receiver.header!!, result)
    }

    suspend fun close() = connection.close()

    companion object {
        const val TRANSFER_IDLE_MS = 2_000L
    }
}
