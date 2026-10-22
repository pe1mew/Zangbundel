// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.platform.crypto

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.Box
import com.goterl.lazysodium.interfaces.Sign
import nl.pe1mew.zangbundel.core.protocol.Crypto
import java.security.MessageDigest
import java.security.SecureRandom

/** The protocol's Crypto interface on libsodium (architecture A-09, ble-protocol section 8.4). */
class SodiumCrypto : Crypto {
    private val sodium = LazySodiumAndroid(SodiumAndroid())
    private val random = SecureRandom()

    override fun generateSigningKeyPair(): Crypto.SigningKeyPair {
        val pk = ByteArray(Sign.PUBLICKEYBYTES)
        val sk = ByteArray(Sign.SECRETKEYBYTES)
        check(sodium.cryptoSignKeypair(pk, sk)) { "crypto_sign_keypair failed" }
        return Crypto.SigningKeyPair(pk, sk)
    }

    override fun sign(message: ByteArray, secretKey: ByteArray): ByteArray {
        val sig = ByteArray(Sign.BYTES)
        check(sodium.cryptoSignDetached(sig, message, message.size.toLong(), secretKey)) { "crypto_sign_detached failed" }
        return sig
    }

    override fun verify(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean =
        signature.size == Sign.BYTES && publicKey.size == Sign.PUBLICKEYBYTES &&
            sodium.cryptoSignVerifyDetached(signature, message, message.size, publicKey)

    override fun generateBoxKeyPair(): Crypto.BoxKeyPair {
        val pk = ByteArray(Box.PUBLICKEYBYTES)
        val sk = ByteArray(Box.SECRETKEYBYTES)
        check(sodium.cryptoBoxKeypair(pk, sk)) { "crypto_box_keypair failed" }
        return Crypto.BoxKeyPair(pk, sk)
    }

    override fun seal(plaintext: ByteArray, recipientPublicKey: ByteArray): ByteArray {
        val out = ByteArray(plaintext.size + Box.SEALBYTES)
        check(sodium.cryptoBoxSeal(out, plaintext, plaintext.size.toLong(), recipientPublicKey)) { "crypto_box_seal failed" }
        return out
    }

    override fun open(sealed: ByteArray, keyPair: Crypto.BoxKeyPair): ByteArray? {
        if (sealed.size < Box.SEALBYTES) return null
        val out = ByteArray(sealed.size - Box.SEALBYTES)
        val ok = sodium.cryptoBoxSealOpen(out, sealed, sealed.size.toLong(), keyPair.publicKey, keyPair.secretKey)
        return if (ok) out else null
    }

    override fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    override fun randomBytes(count: Int): ByteArray = ByteArray(count).also { random.nextBytes(it) }
}
