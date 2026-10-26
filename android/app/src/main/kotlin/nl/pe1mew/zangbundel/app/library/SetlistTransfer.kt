// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.library

import nl.pe1mew.zangbundel.core.songformat.Limits
import nl.pe1mew.zangbundel.core.songformat.ReportEntry
import nl.pe1mew.zangbundel.core.songformat.ReportLevel
import nl.pe1mew.zangbundel.core.songformat.Setlist
import nl.pe1mew.zangbundel.core.songformat.SetlistParser
import nl.pe1mew.zangbundel.core.songformat.SetlistResolver
import nl.pe1mew.zangbundel.core.songformat.SetlistWriter
import nl.pe1mew.zangbundel.core.songformat.SongMeta
import nl.pe1mew.zangbundel.platform.storage.LibrarySetlist
import nl.pe1mew.zangbundel.platform.storage.PreparedEntry
import nl.pe1mew.zangbundel.platform.storage.Songbook

/**
 * Setlists in and out of the app as `.zbs` files (guiDesign D-59, song-format section 13): importing
 * from a file or an HTTPS address, exporting for the share sheet, and copying a setlist a library ships.
 * On import every line is resolved against the installed libraries (13.5); a song that is not
 * installed is kept with its identifiers and shows as *niet gevonden*, so it completes itself once
 * its library is added, and a title that matches no single song stays a title-only entry.
 */
class SetlistTransfer(private val songbook: Songbook, private val book: SetlistBook, private val downloads: Downloads) {

    sealed interface Imported {
        /** [missing] counts entries no installed song answers; [warnings] are the SL1xx codes. */
        data class Done(val uid: Long, val title: String, val missing: Int, val warnings: List<ReportEntry>) : Imported
        data class Refused(val report: List<ReportEntry>) : Imported
        data class Failed(val reason: LibraryRepository.Reason) : Imported
    }

    /** A file the leader picked or was handed; untrusted bytes, bounded by the parser (SP-09). */
    suspend fun import(bytes: ByteArray): Imported {
        // A shared file may have been renamed on the way, so the id is not checked against the file name (no SL003 here).
        val parsed = SetlistParser.parse(bytes)
        val setlist = parsed.setlist
        if (setlist == null || parsed.report.any { it.level == ReportLevel.SONG_ERROR || it.level == ReportLevel.LIBRARY_ERROR }) {
            return Imported.Refused(parsed.report)
        }
        val entries = entries(setlist)
        val uid = book.createWith(setlist.id, setlist.title, entries)
        val missing = entries.count { it.songKey == null || it.songKey !in installedKeys() }
        return Imported.Done(uid, setlist.title, missing, parsed.report.filter { it.level == ReportLevel.WARNING })
    }

    /** An HTTPS address of a `.zbs` file (SP-25): one GET, capped at the setlist size limit, then [import]. */
    suspend fun importUrl(input: String): Imported {
        val url = input.trim().let { if ("://" in it) it else "https://$it" }
        if (!url.startsWith("https://")) return Imported.Failed(LibraryRepository.Reason.UNSUPPORTED_URL)
        val fetched = try { downloads.fetch(url, Limits.SONG_FILE_MAX_BYTES.toLong()) } catch (e: Exception) { null }
        if (fetched == null || fetched.code != 200) return Imported.Failed(LibraryRepository.Reason.NETWORK)
        return import(fetched.bytes)
    }

    /** The canonical `.zbs` text of a prepared setlist and its file name (13.5: titles as comments, `? title` for titles never resolved). */
    suspend fun export(uid: Long): Pair<String, String>? {
        val setlist = book.setlist(uid) ?: return null
        val entries = book.entries(uid).mapIndexed { i, e ->
            if (e.libraryId != null && e.songId != null) Setlist.Entry.Song(e.libraryId!!, e.songId!!, i + 1) else Setlist.Entry.Title(e.title, i + 1)
        }
        val model = Setlist(setlist.id, setlist.title, entries = entries, canonical = "")
        return setlist.id + SetlistParser.EXTENSION to SetlistWriter.write(model, SetlistResolver.resolve(model, installed()))
    }

    /** A library's own setlist as its lines (D-59), read from the installed revision. */
    suspend fun read(shipped: LibrarySetlist): List<PreparedEntry>? {
        val bytes = runCatching { songbook.read(shipped.libraryId, shipped.directory, shipped.fileName) }.getOrNull() ?: return null
        return SetlistParser.parse(bytes).setlist?.let { entries(it) }
    }

    /** *Dupliceren* on a library's setlist: an editable copy under [title]. */
    suspend fun duplicate(shipped: LibrarySetlist, title: String): Long? = read(shipped)?.let { book.createWith(null, title, it) }

    private suspend fun entries(setlist: Setlist): List<PreparedEntry> =
        SetlistResolver.resolve(setlist, installed()).map { r ->
            when (r) {
                is SetlistResolver.Resolution.Resolved -> PreparedEntry(r.library, r.song.id, r.song.title)
                is SetlistResolver.Resolution.Unresolved -> when (val e = r.entry) {
                    is Setlist.Entry.Song -> PreparedEntry(e.library, e.song, e.library + "/" + e.song)
                    is Setlist.Entry.Title -> PreparedEntry(null, null, e.title)
                }
            }
        }

    private suspend fun installed(): Map<String, List<SongMeta>> =
        songbook.entries().groupBy({ it.libraryId }, { SongMeta(id = it.songId, title = it.title, language = it.language, sortTitle = it.sortTitle) })

    private suspend fun installedKeys(): Set<String> = songbook.entries().mapTo(HashSet()) { it.libraryId + "/" + it.songId }
}
