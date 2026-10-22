// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app

/**
 * The one logging wrapper (architecture A-20). Verbose, debug and info are stripped from release
 * builds by R8 (proguard-rules.pro). Warnings and errors may carry codes and counts, never a display
 * name, request text, token, key, gathering name or song text (NFR-SEC-03).
 */
object Log {
    private const val TAG = "Zangbundel"

    @JvmStatic fun v(message: String) { android.util.Log.v(TAG, message) }
    @JvmStatic fun d(message: String) { android.util.Log.d(TAG, message) }
    @JvmStatic fun i(message: String) { android.util.Log.i(TAG, message) }
    @JvmStatic fun w(message: String) { android.util.Log.w(TAG, message) }
    @JvmStatic fun e(message: String, error: Throwable? = null) { android.util.Log.e(TAG, message, error) }
}
