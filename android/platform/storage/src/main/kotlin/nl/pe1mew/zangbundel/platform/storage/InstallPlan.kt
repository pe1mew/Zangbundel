// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.platform.storage

import nl.pe1mew.zangbundel.core.songformat.ArchiveImport
import nl.pe1mew.zangbundel.core.songformat.ReportLevel
import java.security.SecureRandom

/**
 * What installing one checked archive writes (architecture section 6, song-format 12.7 steps 6 and 7):
 * the files under names the app chooses, never the archive's own paths, and the rows of the library
 * index. Built from an [ArchiveImport.Result] that was not refused.
 */
class InstallPlan private constructor(
    val library: LibraryRow,
    /** File name in the installed revision's directory → bytes. */
    val files: Map<String, ByteArray>,
    val songs: List<SongRow>,
    val setlists: List<SetlistRow>,
    val report: List<ReportRow>,
) {
    val libraryId: String get() = library.id

    companion object {
        const val LIBRARY_FILE = "library.json"
        const val LICENSE_FILE = "license.txt"
        const val MANIFEST_FILE = "manifest.json"
        const val SONGS = "songs/"
        const val SETLISTS = "setlists/"

        /**
         * [source] is the URL or file name the archive came from; [now] the install time in epoch
         * milliseconds. Notation attachments (P3) are not installed yet.
         */
        fun from(result: ArchiveImport.Result, source: String?, now: Long, random: SecureRandom = SecureRandom()): InstallPlan {
            require(!result.refused) { "a refused archive is never installed" }
            val checked = result.library!!
            val library = checked.library!!
            val revision = result.revision!!
            val files = LinkedHashMap<String, ByteArray>()
            files[LIBRARY_FILE] = result.files.getValue("zangbundel.json")
            files[LICENSE_FILE] = result.files.getValue(library.license.file)
            result.manifestBytes?.let { files[MANIFEST_FILE] = it }
            val songs = checked.songs.map { imported ->
                val s = imported.song
                val name = SONGS + s.meta.id + ".cho"
                files[name] = s.canonical.toByteArray(Charsets.UTF_8)
                SongRow(
                    libraryId = library.id, songId = s.meta.id, fileName = name, sourcePath = imported.path,
                    title = s.meta.title, sortTitle = s.meta.sortTitle, language = s.meta.language,
                    hash = s.hash.bytes.toHex(), tags = s.meta.tags, variantGroup = s.meta.variantGroup,
                    redistribute = s.meta.redistribute, licenseSummary = s.meta.licenceSummary(library.name),
                    searchText = s.lines.joinToString("\n"),
                )
            }
            val setlists = checked.setlists.map { imported ->
                val s = imported.setlist
                val name = SETLISTS + s.id + ".zbs"
                files[name] = s.canonical.toByteArray(Charsets.UTF_8)
                SetlistRow(library.id, s.id, name, s.title, s.description, s.entries.size)
            }
            val row = LibraryRow(
                id = library.id, name = library.name, description = library.description,
                revision = revision, directory = revision + "-" + ByteArray(4).also(random::nextBytes).toHex(),
                version = result.manifest?.version, created = result.manifest?.created, source = source,
                repository = result.manifest?.sourceRepository, ref = result.manifest?.sourceRef,
                archiveSha256 = result.archiveSha256, formatVersion = library.formatVersion,
                licenseId = library.license.id, licenseSummary = library.license.summary, installedAt = now,
            )
            val report = result.report.map { ReportRow(it.level.name, it.code, it.path, it.line, it.detail) }
            return InstallPlan(row, files, songs, setlists, report)
        }

        private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    }
}

/** One installed library revision: what the library screen and the report show (song-format 12.7 step 7). */
data class LibraryRow(
    val id: String,
    val name: String,
    val description: String,
    val revision: String,
    /** The revision's directory: the revision plus a random suffix, new for every install, so a reinstall never overwrites files in use. */
    val directory: String,
    val version: String?,
    val created: String?,
    /** Where the archive came from: a URL or a file name. */
    val source: String?,
    /** The git repository and tag or branch from the manifest [FR-LIB-17]; null for a snapshot. */
    val repository: String?,
    val ref: String?,
    val archiveSha256: String,
    val formatVersion: String,
    val licenseId: String,
    val licenseSummary: String,
    val installedAt: Long,
)

/** A song of an installed library: enough to list, search and match it by hash (ble-protocol 10.3, FR-BLE-12). */
data class SongRow(
    val libraryId: String,
    val songId: String,
    val fileName: String,
    val sourcePath: String,
    val title: String,
    val sortTitle: String?,
    val language: String,
    /** The content hash in hex, 32 characters (song-format 8.2). */
    val hash: String,
    val tags: List<String>,
    val variantGroup: String?,
    val redistribute: Boolean,
    val licenseSummary: String,
    /** The canonical line list, one line each: what lyric search indexes [FR-LIB-31]. */
    val searchText: String,
)

data class SetlistRow(val libraryId: String, val setlistId: String, val fileName: String, val title: String, val description: String?, val entries: Int)

/** A report line kept until the next import (song-format 11.2). */
data class ReportRow(val level: String, val code: String, val path: String, val line: Int?, val detail: String?) {
    val isError: Boolean get() = level == ReportLevel.LIBRARY_ERROR.name || level == ReportLevel.SONG_ERROR.name
}
