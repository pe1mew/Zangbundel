// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.platform.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The leader's resume record on disk (architecture 5.4, assessment SP-19, SP-27):
 * `noBackupFilesDir/gathering/resume.cbor`, encrypted with AES-GCM under a key held in the Android
 * Keystore (non-exportable, made once per installation, no user authentication), so a copy of the
 * file alone is useless. Written atomically: a temporary file, then a rename. Platform API only.
 *
 * The file is the IV followed by the ciphertext with its tag. What it holds is the caller's
 * (`ResumeRecord` in `core/protocol`).
 */
class ResumeStore(private val dir: File) {
    private val file = File(dir, FILE_NAME)
    private val temp = File(dir, "$FILE_NAME.tmp")

    @Synchronized
    fun write(plain: ByteArray) {
        dir.mkdirs()
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(plain)
        temp.writeBytes(sealed)
        if (!temp.renameTo(file)) {
            temp.delete()
            error("resume record not written")
        }
    }

    /** The record's bytes; null when there is none, or when it cannot be read or decrypted (the caller deletes it). */
    @Synchronized
    fun read(): ByteArray? {
        if (!file.isFile || file.length() > MAX_FILE_BYTES) return null
        return runCatching {
            val sealed = file.readBytes()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
            cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
        }.getOrNull()
    }

    val exists: Boolean get() = file.isFile

    @Synchronized
    fun delete() {
        file.delete()
        temp.delete()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    companion object {
        private const val FILE_NAME = "resume.cbor"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "zangbundel-resume"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        /** Above any record `ResumeRecord` accepts, plus the IV and tag. */
        private const val MAX_FILE_BYTES = 512 * 1024L

        fun open(context: Context) = ResumeStore(File(context.noBackupFilesDir, "gathering"))
    }
}
