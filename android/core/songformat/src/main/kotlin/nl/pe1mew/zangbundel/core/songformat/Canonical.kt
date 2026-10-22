// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.songformat

import nl.pe1mew.zangbundel.core.model.ContentHash
import java.security.MessageDigest
import java.text.Normalizer

/**
 * Canonical form and content hash of a song file (song-format section 8).
 *
 * Steps, in order: decode UTF-8 (invalid input is rejected by the caller), drop a leading BOM,
 * normalise to NFC, CRLF and lone CR become LF, trailing spaces and tabs are removed per line,
 * trailing empty lines are removed and exactly one LF ends the file.
 *
 * The platform calls (NFC, SHA-256) are confined to this file (architecture A-05).
 */
object Canonical {
    /** Returns the canonical text; `changed` reports whether the input deviated (report code W001). */
    fun canonicalise(text: String): Result {
        var s = text.removePrefix("﻿")
        s = Normalizer.normalize(s, Normalizer.Form.NFC)
        s = s.replace("\r\n", "\n").replace('\r', '\n')
        val lines = s.split('\n').map { it.trimEnd(' ', '\t') }.toMutableList()
        while (lines.isNotEmpty() && lines.last().isEmpty()) lines.removeAt(lines.size - 1)
        val canonical = lines.joinToString("\n") + "\n"
        return Result(canonical, changed = canonical != text)
    }

    fun hash(canonicalText: String): ContentHash {
        val digest = MessageDigest.getInstance("SHA-256").digest(canonicalText.toByteArray(Charsets.UTF_8))
        return ContentHash(digest.copyOf(ContentHash.SIZE))
    }

    data class Result(val text: String, val changed: Boolean)
}
