// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.library

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** A file from the system file picker: its display name and at most [max] + 1 bytes, so a check past the cap can refuse it without holding more. */
internal suspend fun readPicked(resolver: ContentResolver, uri: Uri, max: Int): Pair<String, ByteArray> = withContext(Dispatchers.IO) {
    // Assessment SP-37 condition 5: the name another app reports is shown and recorded, never trusted.
    val name = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()
        ?.let { plain(it, 100) }?.ifBlank { null } ?: "bestand"
    // A URI the app may not read, or one that vanished, gives no bytes: the check then refuses it (PKG-01 / A001), the app never crashes (SP-37).
    name to (runCatching { resolver.openInputStream(uri)?.use { it.readAtMost(max + 1) } }.getOrNull() ?: ByteArray(0))
}

private fun InputStream.readAtMost(max: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (out.size() < max) {
        val n = read(buffer, 0, minOf(buffer.size, max - out.size()))
        if (n < 0) break
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}

/**
 * Text from a library or another app is shown, never trusted (guiDesign D-87, assessment SP-37 condition 5): cut to
 * [max] characters, with control characters and the bidirectional marks and overrides removed (U+200E, U+200F,
 * U+202A to U+202E, U+2066 to U+2069). Written with code points, so the source holds no invisible characters.
 */
internal fun plain(text: String, max: Int): String =
    text.filterNot { it.isISOControl() || it.code == 0x200E || it.code == 0x200F || it.code in 0x202A..0x202E || it.code in 0x2066..0x2069 }.take(max)
