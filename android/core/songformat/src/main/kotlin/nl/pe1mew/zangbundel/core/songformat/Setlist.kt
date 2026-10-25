// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.songformat

import nl.pe1mew.zangbundel.core.model.isValidId
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.text.Normalizer
import java.util.Locale

/** A setlist file `.zbs` (song-format section 13), entries in sung order. */
data class Setlist(
    val id: String,
    val title: String,
    val description: String? = null,
    val language: String? = null,
    val entries: List<Entry>,
    /** The canonical text the file was read as (13.1). */
    val canonical: String,
) {
    sealed interface Entry {
        /** The 1-based line in the file, for the report and the leader's page. */
        val line: Int
        data class Song(val library: String, val song: String, override val line: Int) : Entry
        data class Title(val title: String, override val line: Int) : Entry
    }
}

/**
 * Reads a setlist (13.1 to 13.3) and reports the codes of 13.6. [library] and [songIds], when the file
 * comes from a library, give SL101 (a song id this library lacks) and I004 (another library); a
 * setlist imported on its own leaves them out. [fileName] gives SL003.
 */
object SetlistParser {

    class Result(val setlist: Setlist?, val report: List<ReportEntry>)

    fun parse(bytes: ByteArray, path: String = "", fileName: String? = null, library: String? = null, songIds: Set<String>? = null): Result {
        val report = ArrayList<ReportEntry>()
        fun add(level: ReportLevel, code: String, line: Int? = null, detail: String? = null) { report += ReportEntry(level, code, path, line, detail) }
        // Over the size of 13.1 nothing is read; 13.6 has no code of its own, so SL001 names the reason.
        if (bytes.size > Limits.SONG_FILE_MAX_BYTES) { add(ReportLevel.SONG_ERROR, "SL001", detail = "${bytes.size} bytes"); return Result(null, report) }
        val text = try { Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString() } catch (e: CharacterCodingException) {
            add(ReportLevel.SONG_ERROR, "SL001", detail = "not valid UTF-8"); return Result(null, report)
        }
        if (text.any { (it < ' ' && it != '\n' && it != '\r' && it != '\t') || it == '\u007F' }) {
            add(ReportLevel.SONG_ERROR, "SL001", detail = "control character"); return Result(null, report)
        }
        val canonical = Canonical.canonicalise(text)
        if (canonical.changed) add(ReportLevel.WARNING, "SL102")

        var title: String? = null
        var id: String? = null
        var description: String? = null
        var language: String? = null
        val entries = ArrayList<Setlist.Entry>()
        val unknown = HashSet<String>()
        canonical.text.removeSuffix("\n").split('\n').forEachIndexed { i, raw ->
            val n = i + 1
            val line = raw.trim()
            when {
                line.isEmpty() || line.startsWith("#") -> {}
                line.startsWith("{") || line.endsWith("}") -> {
                    val d = directive(line) ?: run { add(ReportLevel.SONG_ERROR, "SL004", n, "malformed directive"); return@forEachIndexed }
                    val (name, value) = d
                    when {
                        name == "title" || name == "t" -> when {
                            title != null -> add(ReportLevel.SONG_ERROR, "SL002", n, "a second title")
                            entries.isNotEmpty() -> add(ReportLevel.SONG_ERROR, "SL002", n, "title after the first entry")
                            else -> title = value
                        }
                        name == "meta" -> {
                            val v = value.orEmpty()
                            val metaName = v.substringBefore(' ').lowercase(Locale.ROOT)
                            val metaValue = v.substringAfter(' ', "").trim()
                            when (metaName) {
                                "id" -> if (id != null) add(ReportLevel.SONG_ERROR, "SL002", n, "a second id") else id = metaValue
                                "description" -> description = metaValue
                                "language" -> language = metaValue
                                else -> if (unknown.add("meta $metaName")) add(ReportLevel.INFO, "I001", n, "meta $metaName")
                            }
                        }
                        else -> if (unknown.add(name)) add(ReportLevel.INFO, "I001", n, name)
                    }
                }
                line.startsWith("? ") -> {
                    val t = line.removePrefix("? ").trim()
                    if (t.isEmpty()) add(ReportLevel.SONG_ERROR, "SL004", n, "empty title") else {
                        entries += Setlist.Entry.Title(t, n)
                        add(ReportLevel.INFO, "I005", n, t)
                    }
                }
                else -> {
                    val m = SONG_ENTRY.matchEntire(line)
                    if (m == null || !isValidId(m.groupValues[1]) || !isValidId(m.groupValues[2])) { add(ReportLevel.SONG_ERROR, "SL004", n, line); return@forEachIndexed }
                    val entry = Setlist.Entry.Song(m.groupValues[1], m.groupValues[2], n)
                    entries += entry
                    if (library != null) {
                        if (entry.library != library) add(ReportLevel.INFO, "I004", n, entry.library)
                        else if (songIds != null && entry.song !in songIds) add(ReportLevel.WARNING, "SL101", n, entry.song)
                    }
                }
            }
        }
        if (title.isNullOrBlank() || id.isNullOrBlank()) add(ReportLevel.SONG_ERROR, "SL002", detail = listOfNotNull("title".takeIf { title.isNullOrBlank() }, "id".takeIf { id.isNullOrBlank() }).joinToString(", ") + " missing")
        else if (fileName != null && id != fileName.removeSuffix(EXTENSION)) add(ReportLevel.SONG_ERROR, "SL003", detail = "$id in $fileName")
        else if (!isValidId(id!!)) add(ReportLevel.SONG_ERROR, "SL002", detail = "id $id")
        if (entries.isEmpty()) add(ReportLevel.SONG_ERROR, "SL005")
        if (entries.size > MAX_ENTRIES) add(ReportLevel.WARNING, "SL103", detail = "${entries.size} entries")
        title?.let { if (it.length > Limits.TITLE_MAX_CHARS) add(ReportLevel.WARNING, "SL104", detail = "${it.length} characters") }
        if (report.any { it.level == ReportLevel.SONG_ERROR }) return Result(null, report)
        return Result(Setlist(id!!, title!!, description, language, entries, canonical.text), report)
    }

    /** `{name}` or `{name: value}` (5.3), or null when malformed. */
    private fun directive(line: String): Pair<String, String?>? {
        if (!line.startsWith("{") || !line.endsWith("}") || line.length < 2) return null
        val inner = line.substring(1, line.length - 1)
        if ('{' in inner || '}' in inner) return null
        val colon = inner.indexOf(':')
        val name = (if (colon < 0) inner else inner.substring(0, colon)).trim()
        if (!Regex("[A-Za-z_]+").matches(name)) return null
        return name.lowercase(Locale.ROOT) to (if (colon < 0) null else inner.substring(colon + 1).trim())
    }

    const val EXTENSION = ".zbs"
    const val MAX_ENTRIES = 200
    /** `<library id>/<song id>`, then optionally whitespace and a `#` comment (13.3). */
    private val SONG_ENTRY = Regex("^([^/\\s]+)/([^/\\s#]+)(?:\\s+#.*)?$")
}

/** Resolution of setlist entries against the installed libraries (13.5). */
object SetlistResolver {

    sealed interface Resolution {
        val entry: Setlist.Entry
        data class Resolved(override val entry: Setlist.Entry, val library: String, val song: SongMeta) : Resolution
        data class Unresolved(override val entry: Setlist.Entry, val reason: Reason, val candidates: List<Pair<String, SongMeta>> = emptyList()) : Resolution
    }

    /** The reasons the leader's page shows under a skipped entry (guiDesign D-74). */
    enum class Reason { LIBRARY_NOT_INSTALLED, SONG_UNKNOWN, NO_SINGLE_TITLE_MATCH }

    /** [installed] maps library ids to the metadata of their songs. */
    fun resolve(setlist: Setlist, installed: Map<String, List<SongMeta>>): List<Resolution> {
        val byTitle = installed.flatMap { (lib, songs) -> songs.map { lib to it } }
            .flatMap { pair -> listOfNotNull(pair.second.title, pair.second.sortTitle).map { fold(it) to pair } }
            .groupBy({ it.first }, { it.second })
        return setlist.entries.map { e ->
            when (e) {
                is Setlist.Entry.Song -> {
                    val songs = installed[e.library]
                    val meta = songs?.firstOrNull { it.id == e.song }
                    when {
                        songs == null -> Resolution.Unresolved(e, Reason.LIBRARY_NOT_INSTALLED)
                        meta == null -> Resolution.Unresolved(e, Reason.SONG_UNKNOWN)
                        else -> Resolution.Resolved(e, e.library, meta)
                    }
                }
                is Setlist.Entry.Title -> {
                    val matches = byTitle[fold(e.title)].orEmpty().distinctBy { it.first to it.second.id }
                    if (matches.size == 1) Resolution.Resolved(e, matches[0].first, matches[0].second)
                    else Resolution.Unresolved(e, Reason.NO_SINGLE_TITLE_MATCH, matches)
                }
            }
        }
    }

    /** Case- and accent-insensitive form of a title (13.5): NFD without combining marks, lower case, single spaces. */
    fun fold(title: String): String =
        Normalizer.normalize(title, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")
}

/** Writes a setlist as 13.5 asks for an export: canonical, titles as comments, `? <title>` for entries never resolved. */
object SetlistWriter {
    fun write(setlist: Setlist, resolutions: List<SetlistResolver.Resolution>): String {
        val out = StringBuilder()
        out.append("{title: ").append(setlist.title).append("}\n")
        out.append("{meta: id ").append(setlist.id).append("}\n")
        setlist.description?.let { out.append("{meta: description ").append(it).append("}\n") }
        setlist.language?.let { out.append("{meta: language ").append(it).append("}\n") }
        out.append('\n')
        for (r in resolutions) {
            when (val e = r.entry) {
                is Setlist.Entry.Song -> {
                    out.append(e.library).append('/').append(e.song)
                    if (r is SetlistResolver.Resolution.Resolved) out.append("  # ").append(r.song.title)
                    out.append('\n')
                }
                is Setlist.Entry.Title -> when (r) {
                    is SetlistResolver.Resolution.Resolved -> out.append(r.library).append('/').append(r.song.id).append("  # ").append(r.song.title).append('\n')
                    else -> out.append("? ").append(e.title).append('\n')
                }
            }
        }
        return Canonical.canonicalise(out.toString()).text
    }
}
