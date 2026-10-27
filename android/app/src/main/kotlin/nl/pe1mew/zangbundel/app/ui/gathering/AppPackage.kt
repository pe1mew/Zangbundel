// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.gathering

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The app's own installed package, handed to a phone without internet (FR-DIST-05, architecture A-18,
 * guiDesign D-84): only `sourceDir` of this install, never a file it received (assessment SP-20), and only
 * when the install is one APK; a split install could not be installed from one file (FRD constraint 12).
 */
internal object AppPackage {
    fun shareable(context: Context): Boolean = context.applicationInfo.splitSourceDirs.isNullOrEmpty()

    /** Its size in whole megabytes, for the receiver's patience. */
    fun megabytes(context: Context): Long = (File(context.applicationInfo.sourceDir).length() + MB / 2) / MB

    /**
     * Copies the package into the share directory and opens the share sheet for it (Quick Share, Bluetooth).
     * The copy stays until the next share overwrites it: a transfer may read it long after the sheet closes.
     */
    suspend fun share(context: Context, title: String) {
        val copy = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            dir.listFiles { f -> f.name.endsWith(".apk") }?.forEach { it.delete() }
            val name = "zangbundel-" + versionName(context) + ".apk"
            File(context.applicationInfo.sourceDir).copyTo(File(dir, name), overwrite = true)
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", copy)
        val send = Intent(Intent.ACTION_SEND).setType(MIME).putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(copy.name, uri)
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // Only digits, dots and letters, so the name cannot leave the share directory.
    private fun versionName(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
            ?.filter { it.isLetterOrDigit() || it == '.' }?.ifEmpty { null } ?: "app"

    private const val MIME = "application/vnd.android.package-archive"
    private const val MB = 1_000_000L
}
