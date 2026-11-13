// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.songformat

/** Bounds of song-format sections 5.5 and 12.2. Every parser in this module checks them before reading. */
object Limits {
    const val SONG_FILE_MAX_BYTES = 64 * 1024
    const val SONG_FILE_RECOMMENDED_BYTES = 8 * 1024
    const val TITLE_MAX_CHARS = 60
    const val LYRIC_LINE_MAX_CHARS = 120
    const val CANONICAL_LINES_MAX = 254
    /**
     * Above this the song is refused (S013, proposal 2026-11-12-chorus-repeat-bound). It is the most lines a
     * 64 KiB file could hold written out, so only chorus repeats can pass it: a repeat inside a chorus definition
     * doubles that chorus, and without a bound a few kilobytes grow to billions of lines (fuzz finding, G-09).
     */
    const val CANONICAL_LINES_HARD_MAX = 32 * 1024
    /** `zangbundel.json` is a few hundred bytes; 64 KiB bounds it before parsing (assessment SP-09). */
    const val LIBRARY_FILE_MAX_BYTES = 64 * 1024
    const val ARCHIVE_MAX_BYTES = 64L * 1024 * 1024
    const val ARCHIVE_WARN_BYTES = 16L * 1024 * 1024
    const val ARCHIVE_UNCOMPRESSED_MAX_BYTES = 256L * 1024 * 1024
    const val ARCHIVE_MAX_RATIO = 20
    const val ARCHIVE_MAX_ENTRIES = 10_000
    const val ARCHIVE_ENTRY_NAME_MAX_BYTES = 255
}

/** Report levels of song-format section 11.1. */
enum class ReportLevel { LIBRARY_ERROR, SONG_ERROR, WARNING, INFO }

/** One line of the import report (song-format section 11.2). Codes are the stable strings of 11.3 and 12.8. */
data class ReportEntry(
    val level: ReportLevel,
    val code: String,
    val path: String,
    val line: Int? = null,
    val detail: String? = null,
)
