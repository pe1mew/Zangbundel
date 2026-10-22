// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.platform.storage

import android.content.Context
import java.io.File

/**
 * Runs at every app start before anything else (architecture section 5.4, FR-DATA-08): deletes
 * every singer-side gathering file, a leader resume record older than the resume window, leftover
 * staging directories and the share cache.
 */
class StartupSweep(private val context: Context, private val resumeWindowMillis: Long = 10 * 60 * 1000L) {

    fun run(nowMillis: Long = System.currentTimeMillis()) {
        val gathering = File(context.noBackupFilesDir, "gathering")
        gathering.listFiles()?.forEach { file ->
            val keep = file.name == RESUME_FILE && nowMillis - file.lastModified() <= resumeWindowMillis
            if (!keep) file.deleteRecursively()
        }
        File(context.filesDir, "libraries").listFiles()?.forEach { library ->
            library.listFiles()?.filter { it.name.startsWith(".staging-") }?.forEach { it.deleteRecursively() }
        }
        File(context.cacheDir, "share").deleteRecursively()
    }

    companion object {
        const val RESUME_FILE = "resume.cbor"
    }
}
