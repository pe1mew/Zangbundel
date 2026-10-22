// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol

/**
 * Cryptography as the protocol needs it (architecture A-09): Ed25519 signatures, X25519 sealed
 * boxes (libsodium `crypto_box_seal`), SHA-256 and the platform's CSPRNG. `platform/crypto`
 * implements it with lazysodium-android; JVM tests use lazysodium-java. The core never sees a
 * crypto library directly.
 */
interface Crypto {
    fun generateSigningKeyPair(): SigningKeyPair
    fun sign(message: ByteArray, secretKey: ByteArray): ByteArray
    fun verify(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean

    fun generateBoxKeyPair(): BoxKeyPair
    /** libsodium sealed box to `recipientPublicKey`; 48 bytes of overhead (ble-protocol section 8.4). */
    fun seal(plaintext: ByteArray, recipientPublicKey: ByteArray): ByteArray
    /** Returns null when the box cannot be opened (error `0x8D`). */
    fun open(sealed: ByteArray, keyPair: BoxKeyPair): ByteArray?

    fun sha256(data: ByteArray): ByteArray
    fun randomBytes(count: Int): ByteArray

    class SigningKeyPair(val publicKey: ByteArray, val secretKey: ByteArray)
    class BoxKeyPair(val publicKey: ByteArray, val secretKey: ByteArray)

    companion object {
        const val SIGN_PUBLIC_KEY_BYTES = 32
        const val SIGN_SECRET_KEY_BYTES = 64
        const val SIGNATURE_BYTES = 64
        const val BOX_PUBLIC_KEY_BYTES = 32
        const val BOX_SECRET_KEY_BYTES = 32
        const val SEAL_OVERHEAD_BYTES = 48
    }
}
