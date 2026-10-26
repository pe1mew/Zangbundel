// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.payload

import nl.pe1mew.zangbundel.core.model.GatheringId
import nl.pe1mew.zangbundel.core.model.SongRef
import nl.pe1mew.zangbundel.core.protocol.Crypto
import nl.pe1mew.zangbundel.core.protocol.cbor.Cbor
import nl.pe1mew.zangbundel.core.protocol.cbor.CborArray
import nl.pe1mew.zangbundel.core.protocol.cbor.CborBool
import nl.pe1mew.zangbundel.core.protocol.cbor.CborBytes

/** Document types (ble-protocol 7.3). */
object DocumentType {
    const val STATE = 1
    const val INFO = 2
    const val SONG = 3
    const val SETLIST = 4
    const val MESSAGE = 5
    /** Reserved in phase 1: not sent, ignored when received (9.5). */
    const val DOWNLOAD = 6
    const val REQSTAT = 7
    /** GATT only, sealed to a delegate; never broadcast. */
    const val REQUESTS = 8
    /** A setlist song before it is pushed (v0.32, FR-BLE-18): framed as document 3 would frame it, in no manifest (9.5). */
    const val AHEAD = 9
}

/** Info, document 2 and characteristic `0002` (9.2). Key 11 `libs` is reserved and neither sent nor read. */
class Info(
    val major: Int,
    val minor: Int,
    val gid: GatheringId,
    name: String,
    val flags: Int,
    val singers: Long,
    pk: ByteArray,
    val relay: Boolean? = null,
    val hops: Long? = null,
    val app: String? = null,
    val nsets: Int? = null,
    val ext: Boolean? = null,
    ek: ByteArray? = null,
) {
    val name: String = truncateChars(name, NAME_MAX_CHARS)
    val pk: ByteArray = pk.copyOf()
    val ek: ByteArray? = ek?.copyOf()

    init {
        require(major >= 0 && minor >= 0) { "versions are unsigned" }
        require(flags in 0..0xFF) { "flags are u8" }
        require(singers >= 0 && (hops == null || hops >= 0) && (nsets == null || nsets >= 0)) { "counts are unsigned" }
        require(this.pk.size == FieldSizes.KEY) { "pk must be 32 bytes" }
        require(this.ek == null || this.ek.size == FieldSizes.KEY) { "ek must be 32 bytes" }
    }

    /** 9.2: a singer pins [pk] only when `SHA-256(pk)[0..5] == gid`. */
    fun pkMatchesGid(crypto: Crypto): Boolean = gatheringIdOf(pk, crypto) == gid

    fun encode(): ByteArray = Cbor.encode(
        payload(
            1L to major.cbor(), 2L to minor.cbor(), 3L to gid.cbor(), 4L to name.cbor(), 5L to flags.cbor(),
            6L to singers.cbor(), 7L to pk.cbor(), 8L to relay?.let { CborBool(it) }, 9L to hops?.cbor(),
            10L to app?.cbor(), 12L to nsets?.cbor(), 13L to ext?.let { CborBool(it) }, 14L to ek?.cbor(),
        ),
    )

    companion object {
        const val NAME_MAX_CHARS = 40

        fun decode(bytes: ByteArray): Info = decoding("info") {
            val m = decodeMap(bytes, PayloadLimits.GATT_VALUE)
            Info(
                major = m.requireUint(1, Int.MAX_VALUE.toLong()).toInt(),
                minor = m.requireUint(2, Int.MAX_VALUE.toLong()).toInt(),
                gid = m.requireGid(3),
                name = m.requireTextOf(4, NAME_MAX_CHARS),
                flags = m.requireUint(5, 0xFF).toInt(),
                singers = m.requireUint(6),
                pk = m.requireBytesOf(7, FieldSizes.KEY),
                relay = m.boolOf(8),
                hops = m.uint(9),
                app = m.textOf(10),
                nsets = m.uint(12, Int.MAX_VALUE.toLong())?.toInt(),
                ext = m.boolOf(13),
                ek = m.bytesOf(14, FieldSizes.KEY),
            )
        }
    }
}

/** Song, document 3 (9.5): the current song's file exactly as in the library. */
class SongDocument(val ref: SongRef, file: ByteArray, lic: String? = null) {
    val file: ByteArray = file.copyOf()
    val lic: String? = lic?.let { truncateChars(it, LICENCE_MAX_CHARS) }

    /** 9.5, FR-BLE-04: the file must hash to `ref.hash` before it is shown. */
    fun fileMatchesHash(crypto: Crypto): Boolean = crypto.sha256(file).copyOf(ref.hash.bytes.size).contentEquals(ref.hash.bytes)

    fun encode(): ByteArray = Cbor.encode(payload(1L to SongRefCodec.toCbor(ref), 2L to file.cbor(), 3L to lic?.cbor()))

    companion object {
        const val LICENCE_MAX_CHARS = 200

        fun decode(bytes: ByteArray): SongDocument = decoding("song document") {
            val m = decodeMap(bytes, PayloadLimits.DOCUMENT_DECODED)
            SongDocument(
                ref = SongRefCodec.fromCbor(m[1] ?: throw PayloadException("song document: required key 1 missing")),
                file = m.requireBytesOf(2),
                lic = m.textOf(3, LICENCE_MAX_CHARS),
            )
        }
    }
}

/** Setlist, document 4 (9.5). Upcoming items may be left out (FRD Q2). */
class SetlistDocument(val items: List<Item>, ahead: List<ByteArray> = emptyList()) {
    /**
     * v0.32 key 2 (FR-BLE-18): per setlist position, the `dh` of that song as document 9, or an empty
     * array for a song not broadcast [FR-LIB-22]. No title or reference: the singer only lays out its
     * reception view by position (guiDesign D-81).
     */
    val ahead: List<ByteArray> = ahead.map { it.copyOf() }

    init { require(this.ahead.all { it.isEmpty() || it.size == FieldSizes.DOC_HASH }) { "ahead entries are empty or a dh" } }

    fun withAhead(ahead: List<ByteArray>) = SetlistDocument(items, ahead)

    class Item(val ref: SongRef, title: String, val status: Int, val votes: Long? = null) {
        val title: String = truncateChars(title, TITLE_MAX_CHARS)
        init {
            require(status in 0..3) { "setlist status 0 to 3" }
            require(votes == null || votes >= 0) { "votes are unsigned" }
        }
    }

    fun encode(): ByteArray = Cbor.encode(
        payload(
            // v0.35: key 1 is not sent when empty; the leader sends the order alone (key 2).
            1L to if (items.isEmpty()) null else CborArray(items.map { payload(1L to SongRefCodec.toCbor(it.ref), 2L to it.title.cbor(), 3L to it.status.cbor(), 4L to it.votes?.cbor()) }),
            2L to if (ahead.isEmpty()) null else CborArray(ahead.map { it.cbor() }),
        ),
    )

    companion object {
        const val UPCOMING = 0
        const val CURRENT = 1
        const val SUNG = 2
        const val SKIPPED = 3
        const val TITLE_MAX_CHARS = 60

        fun decode(bytes: ByteArray): SetlistDocument = decoding("setlist document") {
            val m = decodeMap(bytes, PayloadLimits.DOCUMENT_DECODED)
            SetlistDocument(
                m.arrayAt(1).orEmpty().map { v ->
                    val e = v.asMap("setlist item")
                    Item(
                        ref = SongRefCodec.fromCbor(e[1] ?: throw PayloadException("setlist item: ref missing")),
                        title = e.requireTextOf(2, TITLE_MAX_CHARS),
                        status = e.requireUint(3, 3).toInt(),
                        votes = e.uint(4),
                    )
                },
                m.arrayAt(2).orEmpty().map { v ->
                    val b = (v as? CborBytes)?.value ?: throw PayloadException("setlist ahead: not a byte string")
                    if (b.isNotEmpty() && b.size != FieldSizes.DOC_HASH) throw PayloadException("setlist ahead: ${b.size} bytes")
                    b
                },
            )
        }
    }
}

/** Message, document 5 (9.5): `{1: text ≤ 200, 2: ts}`. */
class MessageDocument(text: String, val ts: Long) {
    val text: String = truncateChars(text, TEXT_MAX_CHARS)
    init { require(ts >= 0) { "ts is unsigned" } }

    fun encode(): ByteArray = Cbor.encode(payload(1L to text.cbor(), 2L to ts.cbor()))

    companion object {
        const val TEXT_MAX_CHARS = 200

        fun decode(bytes: ByteArray): MessageDocument = decoding("message document") {
            val m = decodeMap(bytes, PayloadLimits.DOCUMENT_DECODED)
            MessageDocument(m.requireTextOf(1, TEXT_MAX_CHARS), m.requireUint(2))
        }
    }
}

/** Request statuses, document 7 (9.5): anonymous, keyed by the singer-chosen request ID. */
class ReqstatDocument(val items: List<Item>, val rseq: Long) {
    class Item(rid: ByteArray, val status: Int, val setpos: Long? = null) {
        val rid: ByteArray = rid.copyOf()
        init {
            require(this.rid.size == FieldSizes.REQUEST_ID) { "rid must be 4 bytes" }
            require(status in 0..4) { "request status 0 to 4" }
            require(setpos == null || setpos >= 0) { "setpos is unsigned" }
        }
    }

    init { require(rseq >= 0) { "rseq is unsigned" } }

    fun encode(): ByteArray = Cbor.encode(
        payload(
            1L to CborArray(items.map { payload(1L to it.rid.cbor(), 2L to it.status.cbor(), 3L to it.setpos?.cbor()) }),
            2L to rseq.cbor(),
        ),
    )

    companion object {
        const val PENDING = 0
        const val ACCEPTED = 1
        const val DECLINED = 2
        const val SUNG = 3
        const val WITHDRAWN = 4

        fun decode(bytes: ByteArray): ReqstatDocument = decoding("reqstat document") {
            val m = decodeMap(bytes, PayloadLimits.DOCUMENT_DECODED)
            ReqstatDocument(
                (m.arrayAt(1) ?: throw PayloadException("reqstat document: required key 1 missing")).map { v ->
                    val e = v.asMap("reqstat item")
                    Item(e.requireBytesOf(1, FieldSizes.REQUEST_ID), e.requireUint(2, 4).toInt(), e.uint(3))
                },
                m.requireUint(2),
            )
        }
    }
}

/**
 * The delegates' request list, document 8 (9.5). It carries display names, so it travels only over
 * GATT, sealed to the delegate's `xk` (8.4), and is never broadcast.
 */
class RequestsDocument(val items: List<Item>, val rseq: Long, val open: Boolean) {
    class Item(
        rid: ByteArray,
        sid: ByteArray,
        name: String,
        val ref: SongRef? = null,
        text: String? = null,
        val status: Int,
        val ts: Long,
        val count: Long? = null,
    ) {
        val rid: ByteArray = rid.copyOf()
        val sid: ByteArray = sid.copyOf()
        val name: String = truncateChars(name, JoinRequest.NAME_MAX_CHARS)
        val text: String? = text?.let { truncateChars(it, RequestWrite.TEXT_MAX_CHARS) }
        init {
            require(this.rid.size == FieldSizes.REQUEST_ID && this.sid.size == FieldSizes.SID) { "rid 4 bytes, sid 8 bytes" }
            require(status in 0..4) { "request status 0 to 4" }
            require(ts >= 0 && (count == null || count >= 0)) { "ts and count are unsigned" }
        }
    }

    init { require(rseq >= 0) { "rseq is unsigned" } }

    fun encode(): ByteArray = Cbor.encode(
        payload(
            1L to CborArray(items.map {
                payload(
                    1L to it.rid.cbor(), 2L to it.sid.cbor(), 3L to it.name.cbor(), 4L to it.ref?.let { r -> SongRefCodec.toCbor(r) },
                    5L to it.text?.cbor(), 6L to it.status.cbor(), 7L to it.ts.cbor(), 8L to it.count?.cbor(),
                )
            }),
            2L to rseq.cbor(),
            3L to CborBool(open),
        ),
    )

    companion object {
        fun decode(bytes: ByteArray): RequestsDocument = decoding("requests document") {
            val m = decodeMap(bytes, PayloadLimits.DOCUMENT_DECODED)
            RequestsDocument(
                (m.arrayAt(1) ?: throw PayloadException("requests document: required key 1 missing")).map { v ->
                    val e = v.asMap("requests item")
                    Item(
                        rid = e.requireBytesOf(1, FieldSizes.REQUEST_ID),
                        sid = e.requireBytesOf(2, FieldSizes.SID),
                        name = e.requireTextOf(3, JoinRequest.NAME_MAX_CHARS),
                        ref = e[4]?.let { SongRefCodec.fromCbor(it) },
                        text = e.textOf(5, RequestWrite.TEXT_MAX_CHARS),
                        status = e.requireUint(6, 4).toInt(),
                        ts = e.requireUint(7),
                        count = e.uint(8),
                    )
                },
                m.requireUint(2),
                m.boolOf(3) ?: throw PayloadException("requests document: required key 3 missing"),
            )
        }
    }
}
