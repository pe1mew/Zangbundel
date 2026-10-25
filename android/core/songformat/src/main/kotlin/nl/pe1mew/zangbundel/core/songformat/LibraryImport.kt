// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.songformat

/**
 * Checks a library's files the way an import does (song-format sections 3, 4.3 and 11): the library
 * file, the licence file, every `.cho` under `songs/`, and what only shows across files: duplicate
 * ids (S005), variant groups with one member (W011) and notation paths that do not exist (W012).
 *
 * [files] maps paths relative to the repository root, with `/`, to their bytes; where they come from
 * (a release archive, a snapshot, a git checkout) is the caller's. Nothing is stored here.
 */
object LibraryImport {

    class Imported(val path: String, val song: Song)

    class Result(val library: Library?, val songs: List<Imported>, val report: List<ReportEntry>) {
        /** Songs skipped for a song error (11.1), each counted once. */
        val skipped: Int get() = report.filter { it.level == ReportLevel.SONG_ERROR }.map { it.path }.distinct().size
        val warnings: Int get() = report.count { it.level == ReportLevel.WARNING }
        val refused: Boolean get() = library == null
    }

    /**
     * [appVersion], [url] and [installed] go to [LibraryFileParser]. [allowEmpty] is the user's
     * confirmation that an empty library is meant (L008 becomes a warning).
     */
    fun check(
        files: Map<String, ByteArray>,
        appVersion: String,
        url: String? = null,
        installed: Map<String, String> = emptyMap(),
        allowEmpty: Boolean = false,
    ): Result {
        val report = ArrayList<ReportEntry>()
        val json = files[LibraryFileParser.PATH] ?: run {
            report += ReportEntry(ReportLevel.LIBRARY_ERROR, "L001", LibraryFileParser.PATH)
            return Result(null, emptyList(), report)
        }
        val parsed = LibraryFileParser.parse(json, appVersion, url, installed)
        report += parsed.report
        val library = parsed.library ?: return Result(null, emptyList(), report)
        if (library.license.file !in files) {
            report += ReportEntry(ReportLevel.LIBRARY_ERROR, "L007", library.license.file)
            return Result(null, emptyList(), report)
        }

        val underSongs = files.keys.filter { it.startsWith(SONGS) }.sorted()
        val songPaths = underSongs.filter { it.endsWith(EXTENSION) }
        val ignored = underSongs.size - songPaths.size
        if (songPaths.isEmpty()) {
            if (!allowEmpty) {
                report += ReportEntry(ReportLevel.LIBRARY_ERROR, "L008", SONGS)
                return Result(null, emptyList(), report)
            }
            report += ReportEntry(ReportLevel.WARNING, "L008", SONGS)
        }
        if (ignored > 0) report += ReportEntry(ReportLevel.INFO, "I003", SONGS, detail = "$ignored files")

        val parsedSongs = ArrayList<Imported>()
        for (path in songPaths) {
            val result = SongParser.parse(files.getValue(path), path, path.substringAfterLast('/'))
            report += result.report
            result.song?.let { parsedSongs += Imported(path, it) }
        }

        // S005: every file of a duplicated id is reported; the first in path order is imported.
        val songs = ArrayList<Imported>()
        for ((id, group) in parsedSongs.groupBy { it.song.meta.id }) {
            songs += group.first()
            if (group.size > 1) group.forEach { report += ReportEntry(ReportLevel.SONG_ERROR, "S005", it.path, detail = "$id in ${group.size} files") }
        }
        songs.sortBy { it.path }

        // W011: a variant group needs a second member in this library to mean anything.
        val groups = songs.mapNotNull { it.song.meta.variantGroup }.groupingBy { it }.eachCount()
        songs.filter { it.song.meta.variantGroup?.let { g -> groups.getValue(g) == 1 } == true }
            .forEach { report += ReportEntry(ReportLevel.WARNING, "W011", it.path, detail = it.song.meta.variantGroup) }
        // W012: a notation attachment (10.3) must exist in the repository.
        for (s in songs) s.song.meta.other["notation"].orEmpty().filter { it !in files }
            .forEach { report += ReportEntry(ReportLevel.WARNING, "W012", s.path, detail = it) }

        return Result(library, songs, report)
    }

    const val SONGS = "songs/"
    /** Only `.cho`, lower case (3.2); other files under `songs/` are ignored (I003). */
    const val EXTENSION = ".cho"
}
