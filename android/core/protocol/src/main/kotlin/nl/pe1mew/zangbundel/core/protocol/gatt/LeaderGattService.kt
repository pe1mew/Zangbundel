// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.gatt

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import nl.pe1mew.zangbundel.core.model.GatheringId
import nl.pe1mew.zangbundel.core.protocol.Crypto
import nl.pe1mew.zangbundel.core.protocol.ProtocolConstants
import nl.pe1mew.zangbundel.core.protocol.ProtocolError
import nl.pe1mew.zangbundel.core.protocol.Transport
import nl.pe1mew.zangbundel.core.protocol.engine.SlotPolicy
import nl.pe1mew.zangbundel.core.protocol.payload.ByteRange
import nl.pe1mew.zangbundel.core.protocol.payload.ControlCommand
import nl.pe1mew.zangbundel.core.protocol.payload.DocEntry
import nl.pe1mew.zangbundel.core.protocol.payload.DocumentType
import nl.pe1mew.zangbundel.core.protocol.payload.FieldSizes
import nl.pe1mew.zangbundel.core.protocol.payload.JoinRequest
import nl.pe1mew.zangbundel.core.protocol.payload.JoinResponse
import nl.pe1mew.zangbundel.core.protocol.payload.Sealed
import nl.pe1mew.zangbundel.core.protocol.payload.TransferHeader

/**
 * The leader's GATT service (ble-protocol 8, 9.3, 9.7, 10.8): Info and State reads, registration
 * with the singer table, Transfer of documents 1–7 with paced Data notifications, and the
 * connection slots of [SlotPolicy].
 *
 * Not built yet: song requests (Request is answered `0x81`, requests closed, which is what a leader
 * with requests closed answers anyway) and the delegate commands of Control (answered `0x89`, not a
 * delegate, since there are no delegates yet); Control op 10, leave, is served. Document 8 is
 * answered `0x86`.
 *
 * Every value written here is untrusted (assessment SP-09): sealed writes are opened by [Sealed],
 * ranges are clamped, and anything malformed is answered with its 8.3 code, never thrown.
 */
class LeaderGattService(
    private val gid: GatheringId,
    private val ek: Crypto.BoxKeyPair,
    private val crypto: Crypto,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val slots: SlotPolicy = SlotPolicy(),
    private val maxSingers: Int = DEFAULT_MAX_SINGERS,
    private val joinCode: String? = null,
    /** Called with the new value whenever `BUSY` (10.8) changes, so the beacon can follow. */
    private val onBusyChanged: (Boolean) -> Unit = {},
) : Transport.ServerHandler {

    /** One version of a document as the leader serves it: the framed bytes the carousel sends. */
    class Served(val doc: Int, val dv: Int, bytes: ByteArray, val enc: Int, songHash: ByteArray? = null, crypto: Crypto) {
        val bytes: ByteArray = bytes.copyOf()
        val songHash: ByteArray? = songHash?.copyOf()
        val dh: ByteArray = DocEntry.hashOf(this.bytes, crypto)
    }

    private class Singer(val tok: ByteArray, var xk: ByteArray, var lastSeen: Long)

    private class Client(val slot: Long?) {
        var joinAnswer: ByteArray? = null
        var transfer: Job? = null
        var failed = false
    }

    private val lock = Any()
    private var info: ByteArray = ByteArray(0)
    private var state: ByteArray = ByteArray(0)
    private var seq: Int = 0
    private var ended = false
    /** The two most recently published versions per document type (9.7: `0x86` once one is gone). */
    private val documents = HashMap<Int, ArrayDeque<Served>>()
    private val singers = LinkedHashMap<String, Singer>()
    private val clients = HashMap<Transport.ClientHandle, Client>()
    private var busy = false

    /** Registered singers (FR-GATH-12): the singer table, less those that left or fell silent (10.9). */
    val singerCount: Int get() = synchronized(lock) { singers.size }

    /** Transfers served since start, for diagnostics. */
    @Volatile var transfersServed: Int = 0
        private set

    /** The current Info value (9.2) and signed state record (9.4), and the documents the carousel now sends. */
    fun publish(info: ByteArray, state: ByteArray, seq: Int, documents: List<Served>) = synchronized(lock) {
        this.info = info.copyOf()
        this.state = state.copyOf()
        this.seq = seq
        for (d in documents) {
            val versions = this.documents.getOrPut(d.doc) { ArrayDeque() }
            if (versions.lastOrNull()?.dv == d.dv) versions.removeLast()
            versions.addLast(d)
            while (versions.size > VERSIONS_KEPT) versions.removeFirst()
        }
    }

    /** 10.10 step 3: from now on every write is refused with `0x88`. */
    fun end() = synchronized(lock) { ended = true }

    /** 10.9: singers without GATT activity for 10 minutes are removed from the count. */
    fun tick() = synchronized(lock) {
        val now = clock()
        singers.values.removeAll { now - it.lastSeen >= SILENT_SINGER_MS }
    }

    // ---- Transport.ServerHandler ----

    override fun onConnected(client: Transport.ClientHandle) {
        val slot = synchronized(lock) {
            val slot = slots.accept()
            clients[client] = Client(slot)
            updateBusy()
            slot
        }
        // 10.8: a connection arriving at max_conn is disconnected at once; the singer backs off.
        if (slot == null) scope.launch { client.disconnect() }
    }

    override fun onDisconnected(client: Transport.ClientHandle) {
        synchronized(lock) {
            val c = clients.remove(client) ?: return
            c.transfer?.cancel()
            c.slot?.let { slots.release(it, successful = !c.failed) }
            updateBusy()
        }
    }

    override suspend fun onWrite(client: Transport.ClientHandle, characteristic: Int, value: ByteArray): Int {
        val c = synchronized(lock) { clients[client] } ?: return ProtocolConstants.ERR_BUSY
        if (c.slot == null) return ProtocolConstants.ERR_BUSY
        return try {
            if (synchronized(lock) { ended }) throw ProtocolError(ProtocolConstants.ERR_ENDED, "the gathering has ended")
            when (characteristic) {
                ProtocolConstants.CHAR_JOIN -> join(c, value)
                ProtocolConstants.CHAR_TRANSFER -> transfer(client, c, value)
                ProtocolConstants.CHAR_REQUEST -> request(value)
                ProtocolConstants.CHAR_CONTROL -> control(value)
                else -> ATT_WRITE_NOT_PERMITTED
            }
        } catch (e: ProtocolError) {
            e.code
        } catch (e: IllegalArgumentException) {
            ProtocolConstants.ERR_MALFORMED          // a payload that decodes but breaks a field rule
        }
    }

    override suspend fun onRead(client: Transport.ClientHandle, characteristic: Int): ByteArray = synchronized(lock) {
        when (characteristic) {
            ProtocolConstants.CHAR_INFO -> info.copyOf()
            ProtocolConstants.CHAR_STATE -> state.copyOf()
            ProtocolConstants.CHAR_JOIN -> clients[client]?.joinAnswer?.copyOf() ?: ByteArray(0)
            else -> ByteArray(0)
        }
    }

    // ---- writes ----

    /** 9.3: open the sealed Join, answer by a sealed response the singer reads on this connection. */
    private fun join(c: Client, value: ByteArray): Int {
        val request = Sealed.openJoin(value, ek, gid, crypto)
        val response = synchronized(lock) {
            val key = request.sid.toHex()
            val known = singers[key]
            when {
                request.major != ProtocolConstants.PROTOCOL_MAJOR -> JoinResponse(JoinResponse.VERSION_NOT_SUPPORTED, msg = "Update the app to join this gathering")
                joinCode != null && request.code != joinCode -> JoinResponse(JoinResponse.WRONG_CODE)
                known != null -> {
                    // A known sid registers again: the same token, counted once (9.3).
                    known.xk = request.xk
                    known.lastSeen = clock()
                    accepted(known.tok)
                }
                singers.size >= maxSingers -> JoinResponse(JoinResponse.FULL)
                else -> {
                    val tok = crypto.randomBytes(FieldSizes.TOKEN)
                    singers[key] = Singer(tok, request.xk, clock())
                    accepted(tok)
                }
            }
        }
        val sealed = Sealed.joinResponse(response, request.xk, crypto)
        synchronized(lock) { c.joinAnswer = sealed }
        return 0
    }

    private fun accepted(tok: ByteArray) =
        JoinResponse(JoinResponse.ACCEPTED, tok = tok, seq = seq, stay = false, maxPending = JoinResponse.DEFAULT_MAX_PENDING)

    /** 9.7: answer the write, then stream HEADER, DATA and END paced to the connection. */
    private fun transfer(client: Transport.ClientHandle, c: Client, value: ByteArray): Int {
        val request = Sealed.openTransfer(value, ek, gid, crypto)
        if (request.doc == DocumentType.REQUESTS) throw ProtocolError(ProtocolConstants.ERR_UNKNOWN_DOCUMENT, "document 8 is not served yet")
        val notifications = synchronized(lock) {
            if (c.transfer?.isActive == true) throw ProtocolError(ProtocolConstants.ERR_BUSY, "one transfer at a time")
            val versions = documents[request.doc] ?: throw ProtocolError(ProtocolConstants.ERR_UNKNOWN_DOCUMENT, "no such document")
            val served = (if (request.dv == null) versions.lastOrNull() else versions.lastOrNull { it.dv == request.dv })
                ?: throw ProtocolError(ProtocolConstants.ERR_UNKNOWN_DOCUMENT, "version ${request.dv} is gone")
            if (request.doc == DocumentType.SONG && served.songHash != null && !served.songHash.contentEquals(request.hash)) {
                throw ProtocolError(ProtocolConstants.ERR_UNKNOWN_DOCUMENT, "song hash does not match")
            }
            val ranges = ByteRange.clamp(request.ranges ?: listOf(ByteRange(0, served.bytes.size)), served.bytes.size)
            val header = TransferHeader(served.dv, served.bytes.size, served.enc, served.dh, ranges)
            TransferStream.notifications(header, served.bytes, client.mtu)
        }
        val job = scope.launch {
            try {
                for (n in notifications) client.notify(ProtocolConstants.CHAR_DATA, n)
                transfersServed++
            } catch (e: Transport.GattException) {
                synchronized(lock) { c.failed = true }
            }
        }
        synchronized(lock) { c.transfer = job }
        return 0
    }

    private fun request(value: ByteArray): Int {
        val write = Sealed.openRequest(value, ek, gid, crypto)
        seen(write.tok) ?: return ProtocolConstants.ERR_BAD_TOKEN
        return ProtocolConstants.ERR_REQUESTS_CLOSED
    }

    private fun control(value: ByteArray): Int {
        val command = Sealed.openControl(value, ek, gid, crypto)
        val key = seen(command.tok) ?: return ProtocolConstants.ERR_BAD_TOKEN
        if (command.op != ControlCommand.LEAVE) return ProtocolConstants.ERR_NOT_DELEGATE
        synchronized(lock) { singers.remove(key) }     // 10.9: removed from the count at once
        return 0
    }

    /** The table key of the singer holding [tok], marking it seen; null for an unknown token. */
    private fun seen(tok: ByteArray): String? = synchronized(lock) {
        val entry = singers.entries.firstOrNull { it.value.tok.contentEquals(tok) } ?: return null
        entry.value.lastSeen = clock()
        entry.key
    }

    private fun updateBusy() {
        val now = slots.busy
        if (now != busy) { busy = now; onBusyChanged(now) }
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    companion object {
        /** The singer table's default limit (9.3: result 2 refers to it, not to connection slots). */
        const val DEFAULT_MAX_SINGERS = 100
        const val VERSIONS_KEPT = 2
        const val SILENT_SINGER_MS = 10 * 60_000L
        /** ATT "write not permitted", for a characteristic that takes no writes. */
        const val ATT_WRITE_NOT_PERMITTED = 0x03
    }
}
