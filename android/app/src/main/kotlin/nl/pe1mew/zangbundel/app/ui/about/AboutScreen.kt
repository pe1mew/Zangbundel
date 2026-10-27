// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.about

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.app.ZangbundelApp
import nl.pe1mew.zangbundel.app.ui.ConfigurationScreen
import nl.pe1mew.zangbundel.app.ui.gathering.InstallPanel
import nl.pe1mew.zangbundel.app.ui.songbook.ColourSquare
import nl.pe1mew.zangbundel.app.ui.textPanel
import nl.pe1mew.zangbundel.app.ui.theme.Palette
import nl.pe1mew.zangbundel.core.protocol.ProtocolConstants
import nl.pe1mew.zangbundel.core.songformat.ArchiveImport
import nl.pe1mew.zangbundel.core.songformat.LibraryFileParser
import nl.pe1mew.zangbundel.platform.storage.LibraryRow
import java.security.MessageDigest

/**
 * About (FR-GEN-07, guiDesign section 7): the app's version and build, the song-format, archive and
 * BLE protocol versions it implements [NFR-MAINT-01], each installed library's version or revision and
 * date, the short fingerprint of the signing certificate (assessment SP-20), and the privacy screen.
 */
@Composable
fun AboutScreen(onPrivacy: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as ZangbundelApp).container }
    var libraries by remember { mutableStateOf<List<LibraryRow>>(emptyList()) }
    LaunchedEffect(Unit) { libraries = container.storage.index.all().sortedBy { it.name } }
    val code = remember { versionCode(context) }
    val fingerprint = remember { signingFingerprint(context) }
    var install by remember { mutableStateOf(false) }   // D-83
    BackHandler(enabled = install) { install = false }
    if (install) {
        InstallPanel { install = false }
        return
    }
    ConfigurationScreen {
        Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.about_title), style = MaterialTheme.typography.headlineMedium)
            Column(Modifier.fillMaxWidth().textPanel().padding(16.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.about_version, container.versionName, code))
                Text(
                    stringResource(R.string.about_formats, LibraryFileParser.IMPLEMENTED_VERSION, ArchiveImport.IMPLEMENTED_ARCHIVE_VERSION,
                        "${ProtocolConstants.PROTOCOL_MAJOR}.${ProtocolConstants.PROTOCOL_MINOR}"),
                    style = MaterialTheme.typography.bodySmall, color = Palette.OffWhite,
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.about_free), style = MaterialTheme.typography.bodySmall, color = Palette.OffWhite)
                // Section 7 (v0.56, the owner's choice): the source as a link in this tile, opened in the browser.
                val source = stringResource(R.string.about_source_url)
                Text(source.removePrefix("https://"), style = MaterialTheme.typography.bodySmall, color = Palette.FireOrange,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.padding(top = 4.dp).clickable { open(context, source) }.padding(vertical = 8.dp))
            }
            Text(stringResource(R.string.about_libraries), style = MaterialTheme.typography.titleMedium)
            Column(Modifier.fillMaxWidth().textPanel().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (libraries.isEmpty()) Text(stringResource(R.string.about_no_libraries))
                for (l in libraries) {
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        ColourSquare(l.colour)
                        Spacer(Modifier.padding(start = 12.dp))
                        Column {
                            Text(l.name)
                            Text(
                                listOfNotNull(l.version?.let { stringResource(R.string.about_library_version, it) }, stringResource(R.string.about_library_revision, l.revision.take(12)), l.created?.take(10))
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = Palette.OffWhite,
                            )
                        }
                    }
                }
            }
            Text(stringResource(R.string.about_signature), style = MaterialTheme.typography.titleMedium)
            Column(Modifier.fillMaxWidth().textPanel().padding(16.dp)) {
                Text(fingerprint ?: "?", fontFamily = FontFamily.Monospace)
                Text(stringResource(R.string.about_signature_note), style = MaterialTheme.typography.bodySmall, color = Palette.OffWhite)
            }
            // D-83: show or send the install link, also outside a campfire [FR-DIST-01, FR-DIST-04].
            OutlinedButton(onClick = { install = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.about_install)) }
            OutlinedButton(onClick = onPrivacy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_privacy)) }
            // Section 7 (v0.56): the manual in the app's language, in the browser; the app itself does not go online for it.
            val manual = stringResource(R.string.about_manual_url)
            OutlinedButton(onClick = { open(context, manual) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.about_manual)) }
            Text(stringResource(R.string.about_links_note), style = MaterialTheme.typography.bodySmall, color = Palette.OffWhite)
        }
    }
}

/** Hands [url] to the browser; a phone without one keeps the About screen as it is. */
private fun open(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)) }
}

private fun versionCode(context: Context): Long {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
}

/** The signing certificate's SHA-256 as the README prints it, shortened: first three and last two bytes (`57:86:7B:…:9B:10`). */
private fun signingFingerprint(context: Context): String? {
    val pm = context.packageManager
    val cert = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners?.firstOrNull()
    } else {
        @Suppress("DEPRECATION") pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
    } ?: return null
    val hex = MessageDigest.getInstance("SHA-256").digest(cert.toByteArray()).map { "%02X".format(it) }
    return hex.take(3).joinToString(":") + ":…:" + hex.takeLast(2).joinToString(":")
}
