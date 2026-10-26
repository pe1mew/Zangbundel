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
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "bestand"
    name to (resolver.openInputStream(uri)?.use { it.readAtMost(max + 1) } ?: ByteArray(0))
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
