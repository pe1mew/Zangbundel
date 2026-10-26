// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.payload

import nl.pe1mew.zangbundel.core.model.GatheringId
import nl.pe1mew.zangbundel.core.model.SongRef
import nl.pe1mew.zangbundel.core.protocol.Crypto
import nl.pe1mew.zangbundel.core.protocol.cbor.Cbor
import nl.pe1mew.zangbundel.core.protocol.cbor.CborArray
import nl.pe1mew.zangbundel.core.protocol.cbor.CborBool
import nl.pe1mew.zangbundel.core.protocol.cbor.CborBytes
import nl.pe1mew.zangbundel.core.protocol.cbor.CborException
import nl.pe1mew.zangbundel.core.protocol.cbor.CborInt
import nl.pe1mew.zangbundel.core.protocol.cbor.CborMap
import nl.pe1mew.zangbundel.core.protocol.cbor.CborValue

/** One entry of the state manifest `docs` (9.4): `[dh, enc, len, gatt?]`. */
class DocEntry(dh: ByteArray, val enc: Int, val len: Int, val gatt: Boolean = false) {
    val dh: ByteArray = dh.copyOf()

    init {
        require(this.dh.size == FieldSizes.DOC_HASH) { "dh must be ${FieldSizes.DOC_HASH} bytes" }
        require(enc == Deflate.ENC_RAW || enc == Deflate.ENC_DEFLATE) { "enc must be 0 or 1" }
        require(len in 0..PayloadLimits.DOCUMENT_FRAMED) { "len out of range" }
    }

    /** `dv` of an ordinary document: the first two bytes of `dh` (section 5). */
    val dv: Int get() = ((dh[0].toInt() and 0xFF) shl 8) or (dh[1].toInt() and 0xFF)

    internal fun toCbor(): CborValue =
        CborArray(listOfNotNull(CborBytes(dh), CborInt(enc.toLong()), CborInt(len.toLong()), if (gatt) CborBool(true) else null))

    override fun equals(other: Any?): Boolean =
        other is DocEntry && dh.contentEquals(other.dh) && enc == other.enc && len == other.len && gatt == other.gatt
    override fun hashCode(): Int = dh.contentHashCode() * 31 + len

    companion object {
        /** `dh` (section 5): the first 8 bytes of SHA-256 over the framed bytes. */
        fun hashOf(framed: ByteArray, crypto: Crypto): ByteArray = crypto.sha256(framed).copyOf(FieldSizes.DOC_HASH)

        fun of(framed: ByteArray, enc: Int, crypto: Crypto, gatt: Boolean = false): DocEntry =
            DocEntry(hashOf(framed, crypto), enc, framed.size, gatt)

        internal fun fromCbor(v: CborValue): DocEntry {
            val items = (v as? CborArray)?.items ?: throw CborException("manifest entry is not an array")
            if (items.size !in 3..4) throw CborException("manifest entry has ${items.size} items")
            val dh = (items[0] as? CborBytes)?.value ?: throw CborException("manifest dh is not a byte string")
            val enc = (items[1] as? CborInt)?.value ?: throw CborException("manifest enc is not an integer")
            val len = (items[2] as? CborInt)?.value ?: throw CborException("manifest len is not an integer")
            val gatt = items.getOrNull(3)?.let { (it as? CborBool)?.value ?: throw CborException("manifest gatt is not a boolean") } ?: false
            if (len !in 0..PayloadLimits.DOCUMENT_FRAMED.toLong()) throw CborException("manifest len out of range")
            return DocEntry(dh, enc.toInt(), len.toInt(), gatt)
        }
    }
}

/** The body of the state record (9.4), the part that is signed. */
class StateBody(
    val gid: GatheringId,
    val seq: Int,
    val ts: Long,
    val status: Int,
    val flags: Int,
    val cur: SongRef? = null,
    title: String? = null,
    val pos: Int? = null,
    val singers: Long? = null,
    val docs: Map<Int, DocEntry> = emptyMap(),
    val rseq: Long,
    dlg: List<ByteArray> = emptyList(),
    val pt: Long? = null,
    ek: ByteArray,
    /** v0.35: the current song's setlist position, 1-based (9.4 key 15). */
    val cpos: Int? = null,
    sung: ByteArray? = null,
) {
    /** v0.35: the setlist positions pushed so far as a bitmap, bit i for position i + 1 (9.4 key 16). */
    val sung: ByteArray? = sung?.copyOf()

    /** Whether setlist position [position] (1-based) has been pushed in this gathering (v0.35). */
    fun wasSung(position: Int): Boolean {
        val b = sung ?: return false
        val i = position - 1
        return i >= 0 && i / 8 < b.size && (b[i / 8].toInt() shr (i % 8)) and 1 == 1
    }

    val title: String? = title?.let { truncateChars(it, TITLE_MAX_CHARS) }
    val dlg: List<ByteArray> = dlg.map { it.copyOf() }
    val ek: ByteArray = ek.copyOf()

    init {
        require(seq in 0..0xFFFF) { "seq is u16" }
        require(ts >= 0) { "ts is unsigned" }
        require(status == STATUS_RUNNING || status == STATUS_ENDED) { "status must be 0 or 1" }
        require(flags in 0..0xFF) { "flags are the beacon's u8" }
        require(pos == null || pos in 0..255) { "pos is u8" }
        require(singers == null || singers >= 0) { "singers is unsigned" }
        require(docs.keys.all { it in MANIFEST_DOCUMENTS }) { "manifest lists only documents 3 to 7" }
        require(rseq >= 0) { "rseq is unsigned" }
        require(this.dlg.size <= MAX_DELEGATES && this.dlg.all { it.size == FieldSizes.SID }) { "dlg holds at most 8 singer IDs of 8 bytes" }
        require(pt == null || pt >= 0) { "pt is unsigned" }
        require(this.ek.size == FieldSizes.KEY) { "ek must be 32 bytes" }
        require(cpos == null || cpos in 1..255) { "cpos is a setlist position, 1 to 255" }
        require(this.sung == null || this.sung.size <= SUNG_MAX_BYTES) { "sung is at most $SUNG_MAX_BYTES bytes" }
    }

    /**
     * The body as the leader signs it (9.4, decided 2026-10-25, Q17): when it exceeds [limit], leave
     * out `title`, then the library revision `rev` inside `cur`, then `pt`, each only if still needed.
     * Null when even that does not fit, which the other limits make impossible.
     */
    fun fittedTo(limit: Int = PayloadLimits.STATE_BODY): StateBody? {
        var b = this
        if (b.encode().size <= limit) return b
        b = b.with(title = null)
        if (b.encode().size <= limit) return b
        b = b.with(cur = b.cur?.copy(revision = null))
        if (b.encode().size <= limit) return b
        b = b.with(pt = null)
        return b.takeIf { it.encode().size <= limit }
    }

    private fun with(title: String? = this.title, cur: SongRef? = this.cur, pt: Long? = this.pt) = StateBody(
        gid = gid, seq = seq, ts = ts, status = status, flags = flags, cur = cur, title = title, pos = pos,
        singers = singers, docs = docs, rseq = rseq, dlg = dlg, pt = pt, ek = ek, cpos = cpos, sung = sung,
    )

    fun encode(): ByteArray = Cbor.encode(
        payload(
            1L to gid.cbor(),
            2L to seq.cbor(),
            3L to ts.cbor(),
            4L to status.cbor(),
            5L to flags.cbor(),
            6L to cur?.let { SongRefCodec.toCbor(it) },
            7L to title?.cbor(),
            8L to pos?.cbor(),
            9L to singers?.cbor(),
            10L to CborMap(docs.toSortedMap().map { (doc, e) -> CborInt(doc.toLong()) to e.toCbor() }),
            11L to rseq.cbor(),
            12L to dlg.takeIf { it.isNotEmpty() }?.let { list -> CborArray(list.map { CborBytes(it) }) },
            13L to pt?.cbor(),
            14L to ek.cbor(),
            15L to cpos?.cbor(),
            16L to sung?.takeIf { it.isNotEmpty() }?.cbor(),
        ),
    )

    companion object {
        const val STATUS_RUNNING = 0
        const val STATUS_ENDED = 1
        const val TITLE_MAX_CHARS = 60
        const val MAX_DELEGATES = 8
        /** v0.35: 32 bytes are 256 setlist positions. */
        const val SUNG_MAX_BYTES = 32

        /** The bitmap of key 16 for the 1-based setlist [positions]. */
        fun sungBitmap(positions: Collection<Int>): ByteArray? {
            val valid = positions.filter { it in 1..SUNG_MAX_BYTES * 8 }
            if (valid.isEmpty()) return null
            val out = ByteArray((valid.max() + 7) / 8)
            for (p in valid) out[(p - 1) / 8] = (out[(p - 1) / 8].toInt() or (1 shl ((p - 1) % 8))).toByte()
            return out
        }
        val MANIFEST_DOCUMENTS = 3..7

        fun decode(bytes: ByteArray): StateBody = decoding("state body") {
            val m = decodeMap(bytes, PayloadLimits.STATE_BODY)
            val docs = LinkedHashMap<Int, DocEntry>()
            val manifest = m.mapAt(10) ?: throw CborException("required key 10 (docs) missing")
            for ((k, v) in manifest.entries) {
                val doc = (k as? CborInt)?.value ?: throw CborException("manifest key is not an integer")
                if (doc in MANIFEST_DOCUMENTS.first.toLong()..MANIFEST_DOCUMENTS.last.toLong()) docs[doc.toInt()] = DocEntry.fromCbor(v)
                // Other document numbers: unknown to this version, ignored (9.1).
            }
            StateBody(
                gid = m.requireGid(1),
                seq = m.requireUint(2, 0xFFFF).toInt(),
                ts = m.requireUint(3),
                status = m.requireUint(4, 1).toInt(),
                flags = m.requireUint(5, 0xFF).toInt(),
                cur = m[6]?.let { SongRefCodec.fromCbor(it) },
                title = m.textOf(7, TITLE_MAX_CHARS),
                pos = m.uint(8, 255)?.toInt(),
                singers = m.uint(9),
                docs = docs,
                rseq = m.requireUint(11),
                dlg = m.arrayAt(12)?.map { (it as? CborBytes)?.value ?: throw CborException("dlg entry is not a byte string") } ?: emptyList(),
                pt = m.uint(13),
                ek = m.requireBytesOf(14, FieldSizes.KEY),
                cpos = m.uint(15, 255)?.toInt()?.takeIf { it >= 1 },
                sung = m.bytesOf(16, maxSize = SUNG_MAX_BYTES),
            )
        }
    }
}

/**
 * The state record (document 1, characteristic `0004`): `[body (bstr), sig (bstr 64)]`, `sig` the
 * Ed25519 signature over the body bytes (9.4). [bodyBytes] are kept exactly as signed or received;
 * nothing is re-encoded for verification (architecture A-08).
 */
class SignedState private constructor(val body: StateBody, bodyBytes: ByteArray, signature: ByteArray) {
    val bodyBytes: ByteArray = bodyBytes.copyOf()
    val signature: ByteArray = signature.copyOf()

    /** `dv` of the state record is `seq` (section 5). */
    val dv: Int get() = body.seq

    fun encode(): ByteArray = Cbor.encode(CborArray(CborBytes(bodyBytes), CborBytes(signature)))

    /**
     * Whether a singer that last accepted a state with [lastTs] takes this one (9.4): `ts` must
     * increase, except that an ended gathering is always taken.
     */
    fun supersedes(lastTs: Long?): Boolean = lastTs == null || body.ts > lastTs || body.status == StateBody.STATUS_ENDED

    companion object {
        /**
         * Signs a body with the gathering's Ed25519 secret key, after fitting it to 440 bytes
         * ([StateBody.fittedTo]); [SignedState.body] is the body as signed.
         */
        fun sign(body: StateBody, secretKey: ByteArray, crypto: Crypto): SignedState {
            val fitted = requireNotNull(body.fittedTo()) { "state body does not fit ${PayloadLimits.STATE_BODY} bytes" }
            val bytes = fitted.encode()
            return SignedState(fitted, bytes, crypto.sign(bytes, secretKey))
        }

        /**
         * Verifies a received state record against the pinned key [pk] and the gathering [gid]
         * (9.4, 7.5). The signature is checked before the body is parsed.
         */
        fun verify(value: ByteArray, pk: ByteArray, gid: GatheringId, crypto: Crypto): SignedState = decoding("state record") {
            val outer = Cbor.decode(value, PayloadLimits.GATT_VALUE) as? CborArray ?: throw CborException("state record is not an array")
            if (outer.items.size != 2) throw CborException("state record has ${outer.items.size} items")
            val bodyBytes = (outer.items[0] as? CborBytes)?.value ?: throw CborException("state body is not a byte string")
            val sig = (outer.items[1] as? CborBytes)?.value ?: throw CborException("state signature is not a byte string")
            if (sig.size != FieldSizes.SIGNATURE) throw CborException("signature has ${sig.size} bytes")
            if (bodyBytes.size > PayloadLimits.STATE_BODY) throw CborException("state body exceeds ${PayloadLimits.STATE_BODY} bytes")
            if (!crypto.verify(bodyBytes, sig, pk)) throw PayloadException("state record: signature does not verify")
            val body = StateBody.decode(bodyBytes)
            if (body.gid != gid) throw PayloadException("state record: gid of another gathering")
            SignedState(body, bodyBytes, sig)
        }
    }
}

/** `gid` (section 5): the first 5 bytes of SHA-256 over the Ed25519 public key. */
fun gatheringIdOf(pk: ByteArray, crypto: Crypto): GatheringId = GatheringId(crypto.sha256(pk).copyOf(GatheringId.SIZE))
