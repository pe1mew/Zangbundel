// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.engine

import nl.pe1mew.zangbundel.core.protocol.Crypto
import nl.pe1mew.zangbundel.core.protocol.cbor.Cbor
import nl.pe1mew.zangbundel.core.protocol.cbor.CborArray
import nl.pe1mew.zangbundel.core.protocol.cbor.CborInt
import nl.pe1mew.zangbundel.core.protocol.gatt.LeaderGattService
import nl.pe1mew.zangbundel.core.protocol.payload.FieldSizes
import nl.pe1mew.zangbundel.core.protocol.payload.asMap
import nl.pe1mew.zangbundel.core.protocol.payload.arrayAt
import nl.pe1mew.zangbundel.core.protocol.payload.cbor
import nl.pe1mew.zangbundel.core.protocol.payload.decodeMap
import nl.pe1mew.zangbundel.core.protocol.payload.decoding
import nl.pe1mew.zangbundel.core.protocol.payload.payload
import nl.pe1mew.zangbundel.core.protocol.payload.requireBytesOf
import nl.pe1mew.zangbundel.core.protocol.payload.requireTextOf
import nl.pe1mew.zangbundel.core.protocol.payload.requireUint
import nl.pe1mew.zangbundel.core.protocol.payload.textOf
import nl.pe1mew.zangbundel.core.protocol.payload.uint

/**
 * What a leader persists to resume its gathering after the app died (ble-protocol 10.1 step 2, 10.9,
 * v0.39; architecture 5.4): the [session] the protocol needs to carry on unnoticed, and the setlist with
 * what was sung so the leader's page and the singers' current song come back as they were.
 *
 * Plain bytes here; `ResumeStore` in `platform/storage` encrypts them (assessment SP-19, SP-27). The
 * record is read back from the phone's own storage, but it is decoded as strictly as anything from the
 * air: a record that does not decode is refused, and the caller deletes it.
 */
class ResumeRecord(
    val session: LeaderSession.Resume,
    val name: String,
    /** How the gathering is joined: [PROTECTION_OPEN], [PROTECTION_CODE] or [PROTECTION_QR_ONLY]; the code is in [session]. */
    val protection: Int,
    /** The setlist as started, in order; empty for a gathering without one. */
    val setlist: List<Position>,
    /** Setlist positions in the order first pushed. */
    val pushed: List<Int>,
    val current: Int?,
    /** Wall-clock milliseconds: when the gathering started, and when this record was written. */
    val startedWall: Long,
    val writtenWall: Long,
    /** guiDesign D-62: the most singers at once so far. */
    val peak: Int,
) {
    /** One setlist line: its `library-id/song-id` when it names an installed song, and its title. */
    class Position(val songKey: String?, val title: String)

    fun encode(): ByteArray = Cbor.encode(
        payload(
            1L to VERSION.cbor(),
            2L to session.signing.publicKey.cbor(), 3L to session.signing.secretKey.cbor(),
            4L to session.box.publicKey.cbor(), 5L to session.box.secretKey.cbor(),
            6L to name.cbor(), 7L to session.joinCode?.cbor(), 8L to protection.cbor(),
            9L to session.seq.cbor(), 10L to session.ts.cbor(),
            11L to CborArray(session.singers.map { payload(1L to it.sid.cbor(), 2L to it.tok.cbor(), 3L to it.xk.cbor()) }),
            12L to CborArray(setlist.map { payload(1L to it.songKey?.cbor(), 2L to it.title.cbor()) }),
            13L to CborArray(pushed.map { it.cbor() }),
            14L to current?.cbor(),
            15L to startedWall.cbor(), 16L to writtenWall.cbor(), 17L to peak.cbor(),
        ),
    )

    companion object {
        const val VERSION = 1
        const val PROTECTION_OPEN = 0
        const val PROTECTION_CODE = 1
        const val PROTECTION_QR_ONLY = 2

        /** Far above a record of 255 singers and a long setlist; a larger file is not one of ours. */
        const val MAX_BYTES = 256 * 1024
        private const val TEXT_MAX_CHARS = 200
        private const val SECRET_MAX = 64

        fun decode(bytes: ByteArray): ResumeRecord = decoding("resume record") {
            val m = decodeMap(bytes, MAX_BYTES)
            require(m.requireUint(1) == VERSION.toLong()) { "unknown resume record version" }
            val singers = m.arrayAt(11).orEmpty().map { v ->
                val s = v.asMap("singer")
                LeaderGattService.Registered(
                    s.requireBytesOf(1, FieldSizes.SID), s.requireBytesOf(2, FieldSizes.TOKEN), s.requireBytesOf(3, FieldSizes.KEY),
                )
            }
            val setlist = m.arrayAt(12).orEmpty().map { v ->
                val p = v.asMap("setlist position")
                Position(p.textOf(1, TEXT_MAX_CHARS), p.requireTextOf(2, TEXT_MAX_CHARS))
            }
            val pushed = m.arrayAt(13).orEmpty().map { (it as? CborInt)?.value?.toInt() ?: throw IllegalArgumentException("pushed position is not an integer") }
            require(pushed.all { it in setlist.indices }) { "pushed position outside the setlist" }
            val current = m.uint(14, Int.MAX_VALUE.toLong())?.toInt()
            require(current == null || current in setlist.indices) { "current position outside the setlist" }
            val protection = m.requireUint(8, PROTECTION_QR_ONLY.toLong()).toInt()
            ResumeRecord(
                session = LeaderSession.Resume(
                    signing = Crypto.SigningKeyPair(m.requireBytesOf(2, FieldSizes.KEY), m.requireBytesOf(3, maxSize = SECRET_MAX)),
                    box = Crypto.BoxKeyPair(m.requireBytesOf(4, FieldSizes.KEY), m.requireBytesOf(5, maxSize = SECRET_MAX)),
                    joinCode = m.textOf(7, TEXT_MAX_CHARS),
                    seq = m.requireUint(9, 0xFFFF).toInt(),
                    ts = m.requireUint(10),
                    singers = singers,
                ),
                name = m.requireTextOf(6, TEXT_MAX_CHARS),
                protection = protection,
                setlist = setlist,
                pushed = pushed,
                current = current,
                startedWall = m.requireUint(15),
                writtenWall = m.requireUint(16),
                peak = m.requireUint(17, Int.MAX_VALUE.toLong()).toInt(),
            )
        }
    }
}
