// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.beacon

import nl.pe1mew.zangbundel.core.model.GatheringId
import nl.pe1mew.zangbundel.core.protocol.Crypto
import nl.pe1mew.zangbundel.core.protocol.ProtocolConstants
import nl.pe1mew.zangbundel.core.protocol.cbor.Cbor
import nl.pe1mew.zangbundel.core.protocol.cbor.CborBytes
import nl.pe1mew.zangbundel.core.protocol.cbor.CborException
import nl.pe1mew.zangbundel.core.protocol.cbor.CborInt
import nl.pe1mew.zangbundel.core.protocol.cbor.CborMap
import nl.pe1mew.zangbundel.core.protocol.cbor.CborText
import nl.pe1mew.zangbundel.core.protocol.payload.gatheringIdOf
import nl.pe1mew.zangbundel.core.protocol.payload.truncateChars
import java.util.Base64

/**
 * The join QR code (ble-protocol 6.5): `{1: ver, 2: gid, 3: pk, 4: code?, 5: name?}` as base64url
 * in the fragment of `https://pe1mew.github.io/Zangbundel/j/#…` or the path of `zangbundel://j/…`.
 *
 * [parse] applies the same bounds as the published page `docs/j/index.html` (20 to 400 base64url
 * characters, no padding, `ver` 1, `gid` 5 bytes, `pk` 32 bytes) and then the check that makes the
 * code unforgeable: `SHA-256(pk)[0..5] == gid`. A link that fails anything is null.
 */
class JoinLink(val gid: GatheringId, pk: ByteArray, code: String? = null, name: String? = null) {
    val pk: ByteArray = pk.copyOf()
    val code: String? = code?.let { truncateChars(it, CODE_MAX_CHARS) }
    val name: String? = name?.let { truncateChars(it, NAME_MAX_CHARS) }

    init { require(this.pk.size == 32) { "pk must be 32 bytes" } }

    fun payload(): ByteArray = Cbor.encode(
        CborMap(
            listOfNotNull(
                CborInt(1) to CborInt(ProtocolConstants.PROTOCOL_MAJOR.toLong()),
                CborInt(2) to CborBytes(gid.bytes),
                CborInt(3) to CborBytes(pk),
                code?.let { CborInt(4) to CborText(it) },
                name?.let { CborInt(5) to CborText(it) },
            ),
        ),
    )

    private fun encoded(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(payload())

    /**
     * The URL the QR code shows (6.5, canonical form with the trailing slash). A test build passes its
     * [build] (`debug`, `fieldtest`), which adds `?app=<build>` so the join page opens that build; a
     * release build passes nothing.
     */
    fun httpsUrl(build: String? = null): String {
        require(build == null || BUILD.matches(build)) { "build" }
        return HTTPS_PREFIX + (build?.let { "?app=$it" } ?: "") + "#" + encoded()
    }

    /** The app's own scheme, used by the join page's button and an in-app scanner. */
    fun schemeUrl(): String = "$SCHEME_PREFIX${encoded()}"

    companion object {
        const val HTTPS_PREFIX = "https://pe1mew.github.io/Zangbundel/j/"
        const val SCHEME_PREFIX = "zangbundel://j/"
        const val CODE_MAX_CHARS = 8
        const val NAME_MAX_CHARS = 40
        private val ENCODED = Regex("^[A-Za-z0-9_-]{20,400}$")
        private val BUILD = Regex("^[a-z]{1,16}$")
        /** The https form, with or without the trailing slash and a test build's `?app=`; group 1 is the payload. */
        private val HTTPS = Regex("""^https://pe1mew\.github\.io/Zangbundel/j/?(?:\?app=[a-z]{1,16})?#(.*)$""")
        private const val MAX_PAYLOAD_BYTES = 300

        /** The link as the camera app or the browser hands it over; null unless it is a valid join link. */
        fun parse(link: String, crypto: Crypto): JoinLink? {
            val encoded = when {
                HTTPS.matches(link) -> HTTPS.matchEntire(link)!!.groupValues[1]
                link.startsWith(SCHEME_PREFIX) -> link.substring(SCHEME_PREFIX.length)
                else -> return null
            }
            if (!ENCODED.matches(encoded)) return null
            val bytes = try { Base64.getUrlDecoder().decode(encoded) } catch (e: IllegalArgumentException) { return null }
            val m = try { Cbor.decodeMap(bytes, MAX_PAYLOAD_BYTES) } catch (e: CborException) { return null }
            if ((m[1] as? CborInt)?.value != ProtocolConstants.PROTOCOL_MAJOR.toLong()) return null
            val gid = (m[2] as? CborBytes)?.value?.takeIf { it.size == GatheringId.SIZE }?.let { GatheringId(it) } ?: return null
            val pk = (m[3] as? CborBytes)?.value?.takeIf { it.size == 32 } ?: return null
            val code = m[4]?.let { (it as? CborText)?.value ?: return null }
            val name = m[5]?.let { (it as? CborText)?.value ?: return null }
            if (gatheringIdOf(pk, crypto) != gid) return null          // 6.5: rejected as malformed
            return JoinLink(gid, pk, code, name)
        }
    }
}
