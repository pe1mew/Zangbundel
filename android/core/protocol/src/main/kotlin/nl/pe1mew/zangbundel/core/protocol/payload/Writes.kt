// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.payload

import nl.pe1mew.zangbundel.core.model.GatheringId
import nl.pe1mew.zangbundel.core.model.SongRef
import nl.pe1mew.zangbundel.core.protocol.cbor.Cbor
import nl.pe1mew.zangbundel.core.protocol.cbor.CborArray
import nl.pe1mew.zangbundel.core.protocol.cbor.CborBool
import nl.pe1mew.zangbundel.core.protocol.cbor.CborException
import nl.pe1mew.zangbundel.core.protocol.cbor.CborInt
import nl.pe1mew.zangbundel.core.protocol.cbor.CborMap

// Plaintext payloads of the GATT writes (ble-protocol 9.3, 9.6, 9.7, 9.8). Join, Request, Control
// and the Transfer request for document 8 travel sealed (8.4); sealing is a separate layer.

/** Join request (9.3), sealed to the leader's `ek`. Key 6 `libs` is reserved and neither sent nor read. */
class JoinRequest(
    val major: Int,
    val minor: Int,
    sid: ByteArray,
    name: String? = null,
    code: String? = null,
    val caps: Long? = null,
    val app: String? = null,
    val gid: GatheringId,
    xk: ByteArray,
) {
    val sid: ByteArray = sid.copyOf()
    val name: String? = name?.let { truncateChars(it, NAME_MAX_CHARS) }
    val code: String? = code?.let { truncateChars(it, CODE_MAX_CHARS) }
    val xk: ByteArray = xk.copyOf()

    init {
        require(major >= 0 && minor >= 0) { "versions are unsigned" }
        require(this.sid.size == FieldSizes.SID) { "sid must be 8 bytes" }
        require(caps == null || caps >= 0) { "caps are unsigned" }
        require(this.xk.size == FieldSizes.KEY) { "xk must be 32 bytes" }
    }

    fun encode(): ByteArray = Cbor.encode(
        payload(
            1L to major.cbor(), 2L to minor.cbor(), 3L to sid.cbor(), 4L to name?.cbor(), 5L to code?.cbor(),
            7L to caps?.cbor(), 8L to app?.cbor(), 9L to gid.cbor(), 10L to xk.cbor(),
        ),
    )

    companion object {
        const val NAME_MAX_CHARS = 24
        const val CODE_MAX_CHARS = 8
        const val CAP_RELAY = 1L shl 0
        const val CAP_EXTENDED = 1L shl 1
        const val CAP_LEGACY = 1L shl 2

        fun decode(bytes: ByteArray): JoinRequest = decoding("join request") {
            val m = decodeMap(bytes, PayloadLimits.GATT_VALUE)
            JoinRequest(
                major = m.requireUint(1, Int.MAX_VALUE.toLong()).toInt(),
                minor = m.requireUint(2, Int.MAX_VALUE.toLong()).toInt(),
                sid = m.requireBytesOf(3, FieldSizes.SID),
                name = m.textOf(4, NAME_MAX_CHARS),
                code = m.textOf(5, CODE_MAX_CHARS),
                caps = m.uint(7),
                app = m.textOf(8),
                gid = m.requireGid(9),
                xk = m.requireBytesOf(10, FieldSizes.KEY),
            )
        }
    }
}

/** Join response (9.3), sealed to the singer's `xk`. `tok` and `seq` are present exactly when accepted. */
class JoinResponse(
    val result: Int,
    tok: ByteArray? = null,
    val seq: Int? = null,
    val stay: Boolean? = null,
    val relay: Boolean? = null,
    val maxPending: Long? = null,
    val msg: String? = null,
    val role: Int? = null,
) {
    val tok: ByteArray? = tok?.copyOf()

    init {
        require(result in 0..5) { "result 0 to 5" }
        require(this.tok == null || this.tok.size == FieldSizes.TOKEN) { "tok must be 16 bytes" }
        require(seq == null || seq in 0..0xFFFF) { "seq is u16" }
        require(result != ACCEPTED || (this.tok != null && seq != null)) { "an accepted join carries tok and seq" }
        require(maxPending == null || maxPending >= 0) { "max_pending is unsigned" }
        require(role == null || role in 0..1) { "role 0 or 1" }
    }

    fun encode(): ByteArray = Cbor.encode(
        payload(
            1L to result.cbor(), 2L to tok?.cbor(), 3L to seq?.cbor(), 4L to stay?.let { CborBool(it) },
            5L to relay?.let { CborBool(it) }, 6L to maxPending?.cbor(), 7L to msg?.cbor(), 8L to role?.cbor(),
        ),
    )

    companion object {
        const val ACCEPTED = 0
        const val WRONG_CODE = 1
        const val FULL = 2
        const val VERSION_NOT_SUPPORTED = 3
        const val ENDED = 4
        const val TRY_LATER = 5
        const val DEFAULT_MAX_PENDING = 3L

        fun decode(bytes: ByteArray): JoinResponse = decoding("join response") {
            val m = decodeMap(bytes, PayloadLimits.GATT_VALUE)
            JoinResponse(
                result = m.requireUint(1, 5).toInt(),
                tok = m.bytesOf(2, FieldSizes.TOKEN),
                seq = m.uint(3, 0xFFFF)?.toInt(),
                stay = m.boolOf(4),
                relay = m.boolOf(5),
                maxPending = m.uint(6),
                msg = m.textOf(7),
                role = m.uint(8, 1)?.toInt(),
            )
        }
    }
}

/** Request write (9.6), sealed. Adding needs a song reference or a free-text title. */
class RequestWrite(
    tok: ByteArray,
    val op: Int,
    rid: ByteArray,
    val ref: SongRef? = null,
    text: String? = null,
    val gid: GatheringId,
) {
    val tok: ByteArray = tok.copyOf()
    val rid: ByteArray = rid.copyOf()
    val text: String? = text?.let { truncateChars(it, TEXT_MAX_CHARS) }

    init {
        require(this.tok.size == FieldSizes.TOKEN) { "tok must be 16 bytes" }
        require(op == ADD || op == WITHDRAW) { "op 1 or 2" }
        require(this.rid.size == FieldSizes.REQUEST_ID) { "rid must be 4 bytes" }
        require(op != ADD || ref != null || this.text != null) { "adding a request needs ref or text" }
    }

    fun encode(): ByteArray = Cbor.encode(
        payload(
            1L to tok.cbor(), 2L to op.cbor(), 3L to rid.cbor(), 4L to ref?.let { SongRefCodec.toCbor(it) },
            5L to text?.cbor(), 6L to gid.cbor(),
        ),
    )

    companion object {
        const val ADD = 1
        const val WITHDRAW = 2
        const val TEXT_MAX_CHARS = 100

        fun decode(bytes: ByteArray): RequestWrite = decoding("request") {
            val m = decodeMap(bytes, PayloadLimits.GATT_VALUE)
            RequestWrite(
                tok = m.requireBytesOf(1, FieldSizes.TOKEN),
                op = m.requireUint(2, 2).toInt(),
                rid = m.requireBytesOf(3, FieldSizes.REQUEST_ID),
                ref = m[4]?.let { SongRefCodec.fromCbor(it) },
                text = m.textOf(5, TEXT_MAX_CHARS),
                gid = m.requireGid(6),
            )
        }
    }
}

/** A byte range `[off, len]` of a framed document (9.7). */
data class ByteRange(val off: Int, val len: Int) {
    init { require(off >= 0 && len >= 0) { "ranges are unsigned" } }
}

private fun rangesToCbor(ranges: List<ByteRange>) = CborArray(ranges.map { CborArray(it.off.cbor(), it.len.cbor()) })

private fun rangesFromCbor(m: CborMap, key: Long): List<ByteRange>? = m.arrayAt(key)?.map { v ->
    val pair = (v as? CborArray)?.items ?: throw CborException("range is not an array")
    if (pair.size != 2) throw CborException("range has ${pair.size} items")
    val off = (pair[0] as? CborInt)?.value ?: throw CborException("range offset is not an integer")
    val len = (pair[1] as? CborInt)?.value ?: throw CborException("range length is not an integer")
    if (off !in 0..PayloadLimits.DOCUMENT_FRAMED.toLong() || len !in 0..PayloadLimits.DOCUMENT_FRAMED.toLong()) throw CborException("range out of bounds")
    ByteRange(off.toInt(), len.toInt())
}

/** Ascending and not overlapping (9.7). */
private fun checkRanges(ranges: List<ByteRange>?, max: Int) {
    if (ranges == null) return
    require(ranges.size <= max) { "at most $max ranges" }
    for (i in 1 until ranges.size) require(ranges[i].off >= ranges[i - 1].off + ranges[i - 1].len) { "ranges must ascend without overlap" }
}

/** Transfer request (9.7). Plaintext except for document 8, which is sealed and carries `tok` and `gid`. */
class TransferRequest(
    val doc: Int,
    val dv: Int? = null,
    hash: ByteArray? = null,
    val ranges: List<ByteRange>? = null,
    tok: ByteArray? = null,
    val gid: GatheringId? = null,
) {
    val hash: ByteArray? = hash?.copyOf()
    val tok: ByteArray? = tok?.copyOf()

    init {
        require(doc in 1..8) { "doc 1 to 8" }
        require(dv == null || dv in 0..0xFFFF) { "dv is u16" }
        require(this.hash == null || this.hash.size == 16) { "hash must be 16 bytes" }
        require(doc != DocumentType.SONG || this.hash != null) { "a song transfer carries hash" }
        require(doc != DocumentType.REQUESTS || (this.tok != null && gid != null)) { "document 8 needs tok and gid" }
        require(this.tok == null || this.tok.size == FieldSizes.TOKEN) { "tok must be 16 bytes" }
        checkRanges(ranges, MAX_RANGES)
    }

    fun encode(): ByteArray = Cbor.encode(
        payload(
            1L to doc.cbor(), 2L to dv?.cbor(), 3L to hash?.cbor(), 4L to ranges?.let { rangesToCbor(it) },
            5L to tok?.cbor(), 6L to gid?.cbor(),
        ),
    )

    companion object {
        const val MAX_RANGES = 32

        fun decode(bytes: ByteArray): TransferRequest = decoding("transfer request") {
            val m = decodeMap(bytes, PayloadLimits.GATT_VALUE)
            TransferRequest(
                doc = m.requireUint(1, 8).toInt(),
                dv = m.uint(2, 0xFFFF)?.toInt(),
                hash = m.bytesOf(3, 16),
                ranges = rangesFromCbor(m, 4),
                tok = m.bytesOf(5, FieldSizes.TOKEN),
                gid = m.bytesOf(6, GatheringId.SIZE)?.let { GatheringId(it) },
            )
        }
    }
}

/** HEADER of a transfer (9.7, notification type `0x01`): `0x01` followed by this CBOR map. */
class TransferHeader(val dv: Int, val len: Int, val enc: Int, dh: ByteArray, val ranges: List<ByteRange>) {
    val dh: ByteArray = dh.copyOf()

    init {
        require(dv in 0..0xFFFF) { "dv is u16" }
        require(len in 0..PayloadLimits.DOCUMENT_FRAMED) { "len out of range" }
        require(enc in 0..2) { "enc 0, 1 or 2" }
        require(this.dh.size == FieldSizes.DOC_HASH) { "dh must be 8 bytes" }
        checkRanges(ranges, TransferRequest.MAX_RANGES)
    }

    fun encode(): ByteArray = byteArrayOf(TYPE) + Cbor.encode(
        payload(1L to dv.cbor(), 2L to len.cbor(), 3L to enc.cbor(), 4L to dh.cbor(), 5L to rangesToCbor(ranges)),
    )

    companion object {
        const val TYPE: Byte = 0x01
        const val ENC_RAW = 0
        const val ENC_DEFLATE = 1
        const val ENC_DEFLATE_SEALED = 2

        fun decode(notification: ByteArray): TransferHeader = decoding("transfer header") {
            if (notification.isEmpty() || notification[0] != TYPE) throw PayloadException("not a transfer HEADER")
            val m = decodeMap(notification.copyOfRange(1, notification.size), PayloadLimits.GATT_VALUE)
            TransferHeader(
                dv = m.requireUint(1, 0xFFFF).toInt(),
                len = m.requireUint(2, PayloadLimits.DOCUMENT_FRAMED.toLong()).toInt(),
                enc = m.requireUint(3, 2).toInt(),
                dh = m.requireBytesOf(4, FieldSizes.DOC_HASH),
                ranges = rangesFromCbor(m, 5) ?: throw PayloadException("transfer header: required key 5 missing"),
            )
        }
    }
}

/** Control command (9.8), sealed. Which optional keys are required depends on [op]. */
class ControlCommand(
    tok: ByteArray,
    cid: ByteArray,
    val op: Int,
    val ref: SongRef? = null,
    rid: ByteArray? = null,
    val idx: Long? = null,
    data: ByteArray? = null,
    val len: Long? = null,
    val gid: GatheringId,
    val hc: String? = null,
) {
    val tok: ByteArray = tok.copyOf()
    val cid: ByteArray = cid.copyOf()
    val rid: ByteArray? = rid?.copyOf()
    val data: ByteArray? = data?.copyOf()

    init {
        require(this.tok.size == FieldSizes.TOKEN) { "tok must be 16 bytes" }
        require(this.cid.size == FieldSizes.COMMAND_ID) { "cid must be 4 bytes" }
        require(op in PUSH_SONG..LEAVE) { "op 1 to 10" }
        require(this.rid == null || this.rid.size == FieldSizes.REQUEST_ID) { "rid must be 4 bytes" }
        when (op) {
            PUSH_SONG, ADD_TO_SETLIST -> require(ref != null) { "op $op needs ref" }
            ACCEPT_REQUEST, DECLINE_REQUEST, PUSH_REQUEST_NOW -> require(this.rid != null) { "op $op needs rid" }
            UPLOAD_CHUNK -> {
                require(idx != null && idx >= 0 && this.data != null) { "op 8 needs idx and data" }
                require(this.data.size <= UPLOAD_CHUNK_MAX_BYTES) { "op 8 carries at most $UPLOAD_CHUNK_MAX_BYTES bytes" }
                require(idx != 0L || len != null) { "op 8 chunk 0 carries len" }
            }
            BECOME_DELEGATE -> require(hc != null && hc.length == 4 && hc.all { it in '0'..'9' }) { "op 9 needs a four-digit hc" }
        }
        require(len == null || len >= 0) { "len is unsigned" }
    }

    fun encode(): ByteArray = Cbor.encode(
        payload(
            1L to tok.cbor(), 2L to cid.cbor(), 3L to op.cbor(), 4L to ref?.let { SongRefCodec.toCbor(it) },
            5L to rid?.cbor(), 6L to idx?.cbor(), 7L to data?.cbor(), 8L to len?.cbor(), 9L to gid.cbor(), 10L to hc?.cbor(),
        ),
    )

    companion object {
        const val PUSH_SONG = 1
        const val CLEAR_SONG = 2
        const val NEXT_IN_SETLIST = 3
        const val ADD_TO_SETLIST = 4
        const val ACCEPT_REQUEST = 5
        const val DECLINE_REQUEST = 6
        const val PUSH_REQUEST_NOW = 7
        const val UPLOAD_CHUNK = 8
        const val BECOME_DELEGATE = 9
        const val LEAVE = 10
        /**
         * Op 8 keeps the sealed write inside 512 bytes (9.8): 1 framing byte, 48 bytes of seal and
         * 46 bytes of the other keys in the worst case (chunk 0 with `len` = 65 536) leave 417.
         * Protocol v0.15 said 432, which overruns by 15 bytes.
         */
        const val UPLOAD_CHUNK_MAX_BYTES = 417

        fun decode(bytes: ByteArray): ControlCommand = decoding("control command") {
            val m = decodeMap(bytes, PayloadLimits.GATT_VALUE)
            ControlCommand(
                tok = m.requireBytesOf(1, FieldSizes.TOKEN),
                cid = m.requireBytesOf(2, FieldSizes.COMMAND_ID),
                op = m.requireUint(3, LEAVE.toLong()).toInt(),
                ref = m[4]?.let { SongRefCodec.fromCbor(it) },
                rid = m.bytesOf(5, FieldSizes.REQUEST_ID),
                idx = m.uint(6),
                data = m.bytesOf(7, maxSize = UPLOAD_CHUNK_MAX_BYTES),
                len = m.uint(8),
                gid = m.requireGid(9),
                hc = m.textOf(10), // not truncated: a code is exactly four digits or wrong
            )
        }
    }
}
