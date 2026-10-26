// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.setlists

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands a text file to the platform share sheet (guiDesign D-59, assessment SP-25): written to
 * `cacheDir/share/`, the one directory the FileProvider exposes, and deleted when the sheet returns
 * (architecture section 6; the startup sweep removes whatever a crash left). Nothing is sent by the
 * app itself: the leader picks the receiving app.
 */
@Composable
internal fun rememberShare(): (name: String, text: String) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<File?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        pending?.delete()
        pending = null
    }
    return remember(launcher) {
        { name, text ->
            // The name is a setlist id plus `.zbs`: id syntax, so it cannot leave the share directory.
            val file = File(File(context.cacheDir, "share").apply { mkdirs() }, name).apply { writeText(text, Charsets.UTF_8) }
            pending = file
            val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            launcher.launch(Intent.createChooser(send, null))
        }
    }
}
