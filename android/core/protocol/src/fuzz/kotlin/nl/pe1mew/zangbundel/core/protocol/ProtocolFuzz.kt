// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol

import com.code_intelligence.jazzer.api.FuzzedDataProvider
import com.code_intelligence.jazzer.junit.FuzzTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import nl.pe1mew.zangbundel.core.model.GatheringId
import nl.pe1mew.zangbundel.core.protocol.beacon.BeaconCodec
import nl.pe1mew.zangbundel.core.protocol.beacon.JoinLink
import nl.pe1mew.zangbundel.core.protocol.cbor.Cbor
import nl.pe1mew.zangbundel.core.protocol.cbor.CborException
import nl.pe1mew.zangbundel.core.protocol.engine.ResumeRecord
import nl.pe1mew.zangbundel.core.protocol.fec.ReedSolomon
import nl.pe1mew.zangbundel.core.protocol.frame.Carrier
import nl.pe1mew.zangbundel.core.protocol.frame.FrameCodec
import nl.pe1mew.zangbundel.core.protocol.frame.Reassembler
import nl.pe1mew.zangbundel.core.protocol.gatt.LeaderGattService
import nl.pe1mew.zangbundel.core.protocol.payload.ControlCommand
import nl.pe1mew.zangbundel.core.protocol.payload.Deflate
import nl.pe1mew.zangbundel.core.protocol.payload.Info
import nl.pe1mew.zangbundel.core.protocol.payload.JoinRequest
import nl.pe1mew.zangbundel.core.protocol.payload.PayloadException
import nl.pe1mew.zangbundel.core.protocol.payload.PayloadLimits
import nl.pe1mew.zangbundel.core.protocol.payload.RequestWrite
import nl.pe1mew.zangbundel.core.protocol.payload.SetlistDocument
import nl.pe1mew.zangbundel.core.protocol.payload.SignedState
import nl.pe1mew.zangbundel.core.protocol.payload.SongDocument
import nl.pe1mew.zangbundel.core.protocol.payload.StateBody
import nl.pe1mew.zangbundel.core.protocol.payload.TransferHeader
import nl.pe1mew.zangbundel.core.protocol.payload.TransferRequest
import nl.pe1mew.zangbundel.core.testing.JvmSodiumCrypto

/**
 * Jazzer targets for every decoder of bytes from the air or a QR code (test plan 3.4, NFR-SEC-04,
 * assessment SP-09; release check G-09). A decoder may refuse only in its documented way: null, or
 * [PayloadException] / [CborException]; any other exception, a hang or an out-of-memory is a finding.
 * The leader's GATT write handlers must answer every write with a status and never throw. Without
 * JAZZER_FUZZ=1 each target runs its fixed inputs only.
 */
class ProtocolFuzz {
    @FuzzTest(maxDuration = MAX)
    fun cbor(data: ByteArray) = refusing { Cbor.decode(data, PayloadLimits.DOCUMENT_DECODED) }

    /** Every CBOR payload a singer or leader decodes, chosen by the first byte. */
    @FuzzTest(maxDuration = MAX)
    fun payloads(data: FuzzedDataProvider) {
        val which = data.consumeInt(0, 10)
        val bytes = data.consumeRemainingAsBytes()
        refusing {
            when (which) {
                0 -> Info.decode(bytes)
                1 -> SongDocument.decode(bytes)
                2 -> SetlistDocument.decode(bytes)
                3 -> StateBody.decode(bytes)
                4 -> JoinRequest.decode(bytes)
                5 -> RequestWrite.decode(bytes)
                6 -> TransferRequest.decode(bytes)
                7 -> TransferHeader.decode(bytes)
                8 -> ControlCommand.decode(bytes)
                9 -> ResumeRecord.decode(bytes)
                else -> SignedState.verify(bytes, KEYS.publicKey, GID, CRYPTO)
            }
        }
    }

    /** DEFLATE with the output cap of 7.3: a bomb must stop at the cap. */
    @FuzzTest(maxDuration = MAX)
    fun deflate(data: ByteArray) = refusing { Deflate.unframe(Deflate.ENC_DEFLATE, data, PayloadLimits.DOCUMENT_DECODED) }

    @FuzzTest(maxDuration = MAX)
    fun beacon(data: FuzzedDataProvider) {
        BeaconCodec.decodeA(data.consumeBytes(40))
        BeaconCodec.decodeB(data.consumeRemainingAsString())
    }

    /** The join link as the camera app delivers it (6.5): text, then base64url, then CBOR. */
    @FuzzTest(maxDuration = MAX)
    fun joinLink(data: String?) {
        if (data == null) return          // Jazzer's empty input; Kotlin would refuse it as a non-null String
        JoinLink.parse(data, CRYPTO)
        JoinLink.parse(JoinLink.HTTPS_PREFIX + data, CRYPTO)
    }

    /** Frames of either carrier into one reassembler, and ranges as a repair delivers them (7.3, 7.5). */
    @FuzzTest(maxDuration = MAX)
    fun reassembly(data: FuzzedDataProvider) {
        val rs = Reassembler(GID16, if (data.consumeBoolean()) Carrier.LEGACY else Carrier.EXTENDED)
        while (data.remainingBytes() > 0) {
            if (data.consumeInt(0, 7) == 0) {
                refusing { rs.acceptRange(data.consumeInt(0, 9), data.consumeInt(0, 0xFFFF), data.consumeInt(-4, 70_000), data.consumeBytes(data.consumeInt(0, 600)), data.consumeInt(-4, 70_000)) }
            } else {
                FrameCodec.decode(data.consumeBytes(data.consumeInt(0, 260)))?.let { rs.accept(it) }
            }
        }
    }

    /** The erasure decoder of 7.3 with any set of frames: never an exception for any subset. */
    @FuzzTest(maxDuration = MAX)
    fun erasure(data: FuzzedDataProvider) {
        val k = data.consumeInt(1, 64)
        val size = data.consumeInt(1, 64)
        val received = HashMap<Int, ByteArray>()
        while (data.remainingBytes() > 0 && received.size < 256) {
            received[data.consumeInt(-2, 260)] = data.consumeBytes(if (data.consumeBoolean()) size else data.consumeInt(0, 70))
        }
        ReedSolomon.decode(k, size, received)
    }

    /** The leader's GATT writes (8.1, 9.3, 9.6, 9.7, 9.8), sealed or plain: always a status, never a throw. */
    @FuzzTest(maxDuration = MAX)
    fun gattWrites(data: FuzzedDataProvider) = runBlocking {
        val service = LeaderGattService(GID, CRYPTO.generateBoxKeyPair(), CRYPTO, SCOPE, clock = { 0L }, joinCode = "4711")
        val client = Client()
        service.onConnected(client)
        while (data.remainingBytes() > 0) {
            val characteristic = WRITABLE[data.consumeInt(0, WRITABLE.size - 1)]
            service.onWrite(client, characteristic, data.consumeBytes(data.consumeInt(0, 512)))
        }
        service.onDisconnected(client)
    }

    private class Client : Transport.ClientHandle {
        override val mtu: Int = 185
        override suspend fun notify(characteristic: Int, value: ByteArray) = Unit
        override suspend fun disconnect() = Unit
    }

    private companion object {
        /** The CI budget of test plan 3.4; the release run passes a longer one (scripts/fuzz.sh). */
        const val MAX = "60s"
        val CRYPTO = JvmSodiumCrypto()
        val KEYS = CRYPTO.generateSigningKeyPair()
        val GID: GatheringId = nl.pe1mew.zangbundel.core.protocol.payload.gatheringIdOf(KEYS.publicKey, CRYPTO)
        val GID16 = ((GID.bytes[0].toInt() and 0xFF) shl 8) or (GID.bytes[1].toInt() and 0xFF)
        val SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val WRITABLE = intArrayOf(ProtocolConstants.CHAR_JOIN, ProtocolConstants.CHAR_TRANSFER, ProtocolConstants.CHAR_REQUEST, ProtocolConstants.CHAR_CONTROL, ProtocolConstants.CHAR_STATE)

        /** Runs [block]; its documented refusals pass, anything else is a finding. */
        inline fun refusing(block: () -> Unit) {
            try { block() } catch (e: PayloadException) { } catch (e: CborException) { }
        }
    }
}
