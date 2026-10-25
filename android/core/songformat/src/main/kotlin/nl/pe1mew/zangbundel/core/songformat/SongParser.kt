// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.songformat

import nl.pe1mew.zangbundel.core.model.isValidId
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.text.Normalizer

/**
 * The ChordPro-profile parser (song-format sections 5 to 9 and 11; architecture A-10).
 *
 * Input is untrusted (assessment SP-09): the size is checked before decoding, UTF-8 is decoded
 * strictly, and every rule violation becomes a report entry with its 11.3 code, never an exception.
 * A song-level error leaves [Result.song] null; the parse goes on to report every problem it finds.
 *
 * Checks that compare files (S005 duplicate ids, W011 variant groups with one member, W012 notation
 * paths) belong to the library import, which sees all files; they are not made here.
 */
object SongParser {

    class Result(val song: Song?, val report: List<ReportEntry>)

    /**
     * Parses one song file. [path] goes into the report; [fileName] (`vader-jacob.cho`), when given,
     * is checked against the id (S004); songs received over BLE have none.
     */
    fun parse(bytes: ByteArray, path: String = "", fileName: String? = null): Result {
        val report = ArrayList<ReportEntry>()
        fun add(level: ReportLevel, code: String, line: Int? = null, detail: String? = null) {
            report += ReportEntry(level, code, path, line, detail)
        }
        if (bytes.size > Limits.SONG_FILE_MAX_BYTES) {
            add(ReportLevel.SONG_ERROR, "S002", detail = "${bytes.size} bytes")
            return Result(null, report)
        }
        val text = decodeStrict(bytes) ?: run {
            add(ReportLevel.SONG_ERROR, "S001", detail = "not valid UTF-8")
            return Result(null, report)
        }
        text.indexOfFirst { (it < ' ' && it != '\n' && it != '\r' && it != '\t') || it == '\u007F' }.takeIf { it >= 0 }?.let { at ->
            add(ReportLevel.SONG_ERROR, "S001", line = text.substring(0, at).count { it == '\n' } + 1, detail = "control character U+%04X".format(text[at].code))
            return Result(null, report)
        }
        val canonical = Canonical.canonicalise(text)
        if (canonical.changed) add(ReportLevel.WARNING, "W001", detail = deviations(text).joinToString(", "))
        if (canonical.text.toByteArray(Charsets.UTF_8).size > Limits.SONG_FILE_RECOMMENDED_BYTES) add(ReportLevel.WARNING, "W008")

        val body = Body(report, path)
        canonical.text.removeSuffix("\n").split('\n').forEachIndexed { i, line -> body.line(i + 1, line) }
        body.finish()

        val meta = body.meta
        val missing = listOfNotNull(
            "id".takeIf { meta.id == null || !isValidId(meta.id!!) },
            "title".takeIf { meta.title.isNullOrBlank() },
            "language".takeIf { meta.language == null || !LANGUAGE.matches(meta.language!!) },
        )
        if (missing.isNotEmpty()) add(ReportLevel.SONG_ERROR, "S003", detail = missing.joinToString(", "))
        if (fileName != null && meta.id != null && meta.id != fileName.removeSuffix(".cho")) add(ReportLevel.SONG_ERROR, "S004", detail = "${meta.id} in $fileName")
        meta.redistribute?.let { if (it != "yes" && it != "no") add(ReportLevel.SONG_ERROR, "S012", detail = "redistribute $it") }
        meta.year?.let { if (!YEAR.matches(it)) add(ReportLevel.SONG_ERROR, "S012", detail = "year $it") }
        meta.variantGroup?.let { if (!isValidId(it)) add(ReportLevel.SONG_ERROR, "S012", detail = "variant-group $it") }
        meta.tags.filterNot { isValidId(it) }.forEach { add(ReportLevel.SONG_ERROR, "S012", detail = "tag $it") }
        if (body.lineCount == 0) add(ReportLevel.SONG_ERROR, "S010")

        val recommended = listOfNotNull(
            "lyricist/composer".takeIf { meta.lyricists.isEmpty() && meta.composers.isEmpty() },
            "copyright".takeIf { meta.copyright == null },
            "license".takeIf { meta.license == null },
            "source".takeIf { meta.source == null },
            "tag".takeIf { meta.tags.isEmpty() },
        )
        if (recommended.isNotEmpty()) add(ReportLevel.WARNING, "W002", detail = recommended.joinToString(", "))
        meta.title?.let { if (it.length > Limits.TITLE_MAX_CHARS) add(ReportLevel.WARNING, "W003", detail = "${it.length} characters") }
        if (body.lineCount > Limits.CANONICAL_LINES_MAX) add(ReportLevel.WARNING, "W004", detail = "${body.lineCount} lines")
        meta.license?.let { if (it != "public-domain" && it != "proprietary" && !SPDX.matches(it)) add(ReportLevel.WARNING, "W010", detail = it) }
        body.checkTiming()

        if (report.any { it.level == ReportLevel.SONG_ERROR }) return Result(null, report)
        val song = Song(
            canonical = canonical.text,
            hash = Canonical.hash(canonical.text),
            meta = SongMeta(
                id = meta.id!!, title = meta.title!!, language = normaliseLanguage(meta.language!!),
                subtitle = meta.subtitle, sortTitle = meta.sortTitle, lyricists = meta.lyricists, composers = meta.composers,
                artists = meta.artists, year = meta.year, copyright = meta.copyright, license = meta.license, source = meta.source,
                tags = meta.tags, melody = meta.melody, variantGroup = meta.variantGroup, redistribute = meta.redistribute != "no",
                key = meta.key, capo = meta.capo, tempo = meta.tempo, time = meta.time, duration = meta.duration, other = meta.other,
            ),
            body = body.parts,
        )
        return Result(song, report)
    }

    /**
     * The display text of a content line (9.1): `[…]` tokens removed, escapes resolved (5.4), tabs as
     * spaces, trailing whitespace trimmed. A `[` with no closing `]` is kept as text.
     */
    fun displayText(line: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '\\' && i + 1 < line.length && line[i + 1] in ESCAPABLE -> { out.append(line[i + 1]); i += 2 }
                c == '[' -> {
                    val close = line.indexOf(']', i + 1)
                    if (close < 0) { out.append(c); i++ } else i = close + 1
                }
                c == '\t' -> { out.append(' '); i++ }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString().trimEnd()
    }

    /** `nl-be` → `nl-BE` (6.2): language lower case, script title case, region upper case. */
    fun normaliseLanguage(tag: String): String = tag.split('-').mapIndexed { i, p ->
        when {
            i == 0 -> p.lowercase()
            p.length == 4 && p.all { it.isLetter() } -> p.lowercase().replaceFirstChar { it.uppercase() }
            (p.length == 2 && p.all { it.isLetter() }) || (p.length == 3 && p.all { it.isDigit() }) -> p.uppercase()
            else -> p.lowercase()
        }
    }.joinToString("-")

    // ---- the body, line by line (sections 5.2 to 7.5, 9.1) ----

    private class Body(private val report: MutableList<ReportEntry>, private val path: String) {
        val meta = MetaBuilder()
        val parts = ArrayList<Part>()
        var lineCount = 0
            private set

        private class Open(val name: String, val kind: Kind?, val label: String?, val verseNumber: Int?, val line: Int) {
            val items = ArrayList<Item>()
            var continued = false
        }
        private var env: Open? = null
        private var implicit: Open? = null
        private var verses = 0
        private val choruses = ArrayList<Pair<String?, List<Item.Line>>>()
        private var chorusLines: MutableList<Item.Line>? = null
        private val seenSingle = HashSet<String>()
        private val reported = HashSet<String>()
        private val timing = ArrayList<Pair<Int, String>>()

        private fun add(level: ReportLevel, code: String, line: Int? = null, detail: String? = null) {
            report += ReportEntry(level, code, path, line, detail)
        }

        private fun once(key: String, level: ReportLevel, code: String, line: Int, detail: String) {
            if (reported.add("$code:$key")) add(level, code, line, detail)
        }

        fun line(n: Int, line: String) {
            when {
                line.isBlank() -> blank()
                line.startsWith("#") -> {}
                line.startsWith("{") || line.endsWith("}") -> directive(n, line)
                else -> content(n, line)
            }
        }

        private fun blank() {
            val e = env
            if (e != null) {
                if (e.kind != null && e.items.isNotEmpty() && e.items.last() != Item.Gap) e.items += Item.Gap
            } else closeImplicit()
        }

        private fun content(n: Int, line: String) {
            val e = env
            if (e != null && e.kind == null) return                       // tab, grid, abc, unknown: skipped (10.1)
            if ('\t' in line) add(ReportLevel.WARNING, "W007", n)
            val text = displayText(line)
            if (text.isEmpty()) return                                   // a chord-only line (9.1)
            if (text.length > Limits.LYRIC_LINE_MAX_CHARS) add(ReportLevel.WARNING, "W005", n, "${text.length} characters")
            val target = e ?: implicit ?: Open("", Kind.VERSE, null, ++verses, n).also { implicit = it }
            val item = Item.Line(++lineCount, text)
            target.items += item
            if (e?.kind == Kind.CHORUS) chorusLines?.add(item)
        }

        private fun directive(n: Int, line: String) {
            if (!(line.startsWith("{") && line.endsWith("}")) || line.length < 2 || hasUnescapedBrace(line.substring(1, line.length - 1))) {
                add(ReportLevel.SONG_ERROR, "S011", n)
                return
            }
            val inner = line.substring(1, line.length - 1)
            val colon = inner.indexOf(':')
            val rawName = (if (colon < 0) inner else inner.substring(0, colon)).trim()
            val value = if (colon < 0) null else unescape(inner.substring(colon + 1).trim())
            if (!NAME.matches(rawName)) {
                add(ReportLevel.SONG_ERROR, "S011", n, rawName)
                return
            }
            if (rawName != rawName.lowercase()) once(rawName, ReportLevel.WARNING, "W006", n, rawName)
            val name = SHORT_FORMS[rawName.lowercase()] ?: rawName.lowercase()
            when {
                name == "meta" -> {
                    val v = value.orEmpty()
                    val split = v.indexOfFirst { it.isWhitespace() }
                    val metaName = (if (split < 0) v else v.substring(0, split)).lowercase()
                    val metaValue = if (split < 0) "" else v.substring(split).trim()
                    field(n, metaName, metaValue, viaMeta = true)
                }
                name in FIELDS || name in MULTI || name in KNOWN_UNUSED_FIELDS -> field(n, name, value.orEmpty(), viaMeta = false)
                name.startsWith("start_of_") -> start(n, name.removePrefix("start_of_"), value)
                name.startsWith("end_of_") -> end(n, name.removePrefix("end_of_"))
                name == "chorus" -> chorus(n, value)
                name in REMARKS -> remark(REMARKS.getValue(name), value.orEmpty())
                name == "new_song" -> add(ReportLevel.SONG_ERROR, "S009", n)
                name in LAYOUT -> {}
                name == "x_zb_time" || name == "x_zb_syllables" -> {
                    once(name, ReportLevel.INFO, "I001", n, name)            // P2 data, ignored by a P1 app (14.2)
                    if (name == "x_zb_time") timing += n to value.orEmpty()
                }
                else -> once(name, ReportLevel.INFO, "I001", n, name)
            }
        }

        /** 6.1, 6.2: single-valued fields once (S006), multi-valued repeated, unknown meta kept opaque (10.1). */
        private fun field(n: Int, name: String, value: String, viaMeta: Boolean) {
            when (name) {
                in MULTI -> meta.multi(name, value)
                in FIELDS, in KNOWN_UNUSED_FIELDS -> {
                    if (!seenSingle.add(name)) add(ReportLevel.SONG_ERROR, "S006", n, name) else meta.single(name, value)
                }
                else -> {
                    if (viaMeta) once("meta $name", ReportLevel.INFO, "I001", n, "meta $name")
                    meta.other.getOrPut(name) { ArrayList() }.add(value)
                }
            }
        }

        private fun start(n: Int, envName: String, label: String?) {
            if (env != null) { add(ReportLevel.SONG_ERROR, "S007", n, "start_of_$envName inside ${env!!.name}"); return }
            closeImplicit()
            val kind = when (envName) {
                "verse" -> Kind.VERSE
                "chorus" -> Kind.CHORUS
                "bridge" -> Kind.BRIDGE
                else -> {
                    if (envName !in P3_ENVIRONMENTS) once(envName, ReportLevel.INFO, "I002", n, envName)
                    null
                }
            }
            env = Open(envName, kind, label, if (kind == Kind.VERSE) ++verses else null, n)
            if (kind == Kind.CHORUS) chorusLines = ArrayList()
        }

        private fun end(n: Int, envName: String) {
            val e = env
            if (e == null || e.name != envName) { add(ReportLevel.SONG_ERROR, "S007", n, "end_of_$envName without start_of_$envName"); return }
            if (e.kind == Kind.CHORUS) chorusLines?.let { choruses += e.label to it.toList() }
            chorusLines = null
            emit(e)
            env = null
        }

        /** 7.2: repeat the chorus defined with exactly this label, else the most recent one. */
        private fun chorus(n: Int, label: String?) {
            if (choruses.isEmpty()) { add(ReportLevel.SONG_ERROR, "S008", n); return }
            val source = choruses.lastOrNull { label != null && it.first == label } ?: choruses.last()
            closeImplicit()
            val e = env
            if (e != null && e.kind != null) { emit(e); e.items.clear(); e.continued = true }
            val lines = source.second.map { Item.Line(++lineCount, it.text) }
            if (e?.kind == Kind.CHORUS) chorusLines?.addAll(lines)
            parts += Part.Section(Kind.CHORUS, label ?: source.first, null, repeat = true, items = lines)
        }

        private fun remark(style: RemarkStyle, text: String) {
            val e = env
            when {
                e != null && e.kind == null -> {}
                e != null -> e.items += Item.Remark(text, style)
                implicit != null -> implicit!!.items += Item.Remark(text, style)
                else -> parts += Part.Remark(text, style)
            }
        }

        private fun closeImplicit() {
            implicit?.let { emit(it) }
            implicit = null
        }

        private fun emit(o: Open) {
            val kind = o.kind ?: return
            val items = o.items.dropLastWhile { it == Item.Gap }
            if (items.isEmpty() && o.continued) return
            parts += Part.Section(kind, o.label, o.verseNumber, repeat = false, items = items)
        }

        fun finish() {
            env?.let { add(ReportLevel.SONG_ERROR, "S007", it.line, "start_of_${it.name} not closed") }
            env = null
            closeImplicit()
        }

        /** 10.2 shape of `{x_zb_time: n start [end]}`: a warning for a P1 app (W009). */
        fun checkTiming() {
            var lastN = 0
            var lastStart = -1.0
            for ((line, value) in timing) {
                val p = value.split(Regex("\\s+")).filter { it.isNotEmpty() }
                val n = p.getOrNull(0)?.toIntOrNull()
                val start = p.getOrNull(1)?.takeIf { TIME.matches(it) }?.toDouble()
                val end = p.getOrNull(2)?.let { if (TIME.matches(it)) it.toDouble() else Double.NaN }
                val problem = when {
                    p.size !in 2..3 || n == null || start == null || (end != null && end.isNaN()) -> "malformed"
                    n <= lastN -> "line $n not after $lastN"
                    start <= lastStart -> "start $start not after $lastStart"
                    n > lineCount -> "line $n beyond the $lineCount lines"
                    end != null && end <= start -> "end $end not after start $start"
                    else -> null
                }
                if (problem != null) add(ReportLevel.WARNING, "W009", line, problem)
                if (n != null) lastN = maxOf(lastN, n)
                if (start != null) lastStart = maxOf(lastStart, start)
            }
        }
    }

    private class MetaBuilder {
        var id: String? = null
        var title: String? = null
        var language: String? = null
        var subtitle: String? = null
        var sortTitle: String? = null
        val lyricists = ArrayList<String>()
        val composers = ArrayList<String>()
        val artists = ArrayList<String>()
        val tags = ArrayList<String>()
        var year: String? = null
        var copyright: String? = null
        var license: String? = null
        var source: String? = null
        var melody: String? = null
        var variantGroup: String? = null
        var redistribute: String? = null
        var key: String? = null
        var capo: String? = null
        var tempo: String? = null
        var time: String? = null
        var duration: String? = null
        val other = LinkedHashMap<String, MutableList<String>>()

        fun single(name: String, v: String) {
            when (name) {
                "id" -> id = v
                "title" -> title = v
                "language" -> language = v
                "subtitle" -> subtitle = v
                "sorttitle" -> sortTitle = v
                "year" -> year = v
                "copyright" -> copyright = v
                "license" -> license = v
                "source" -> source = v
                "melody" -> melody = v
                "variant-group" -> variantGroup = v
                "redistribute" -> redistribute = v
                "key" -> key = v
                "capo" -> capo = v
                "tempo" -> tempo = v
                "time" -> time = v
                "duration" -> duration = v
                else -> other.getOrPut(name) { ArrayList() }.add(v)          // album, notation: kept, not used in P1
            }
        }

        fun multi(name: String, v: String) {
            when (name) {
                "lyricist" -> lyricists += v
                "composer" -> composers += v
                "artist" -> artists += v
                "tag" -> tags += v
            }
        }
    }

    // ---- helpers ----

    private fun decodeStrict(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (e: CharacterCodingException) {
        null
    }

    /** The deviations W001 names (8.3). */
    private fun deviations(text: String): List<String> = listOfNotNull(
        "BOM".takeIf { text.startsWith("﻿") },
        "CRLF".takeIf { '\r' in text },
        "trailing whitespace".takeIf { text.split('\n').any { it.removeSuffix("\r").let { l -> l.endsWith(" ") || l.endsWith("\t") } } },
        "final newline".takeIf { !text.endsWith("\n") || text.endsWith("\n\n") || text.endsWith("\r\n\r\n") },
        "NFD".takeIf { !Normalizer.isNormalized(text, Normalizer.Form.NFC) },
    )

    private fun hasUnescapedBrace(s: String): Boolean {
        var i = 0
        while (i < s.length) {
            if (s[i] == '\\') { i += 2; continue }
            if (s[i] == '{' || s[i] == '}') return true
            i++
        }
        return false
    }

    /** Escapes of 5.4 in directive values, so a label or title can carry a literal brace or bracket. */
    private fun unescape(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            if (s[i] == '\\' && i + 1 < s.length && s[i + 1] in ESCAPABLE) { out.append(s[i + 1]); i += 2 } else { out.append(s[i]); i++ }
        }
        return out.toString()
    }

    private const val ESCAPABLE = "[]{}\\"
    private val NAME = Regex("[A-Za-z_]+")
    private val LANGUAGE = Regex("^[a-zA-Z]{2,3}(-[a-zA-Z0-9]{1,8})*$")
    private val YEAR = Regex("^[0-9]{4}$")
    private val TIME = Regex("^[0-9]+(\\.[0-9]{1,3})?$")
    /** SPDX license identifier syntax, `LicenseRef-` included; the list itself is not consulted. */
    private val SPDX = Regex("^[A-Za-z0-9][A-Za-z0-9.+-]*$")

    private val SHORT_FORMS = mapOf(
        "t" to "title", "st" to "subtitle",
        "sov" to "start_of_verse", "eov" to "end_of_verse", "soc" to "start_of_chorus", "eoc" to "end_of_chorus",
        "sob" to "start_of_bridge", "eob" to "end_of_bridge", "sot" to "start_of_tab", "eot" to "end_of_tab",
        "sog" to "start_of_grid", "eog" to "end_of_grid",
        "c" to "comment", "ci" to "comment_italic", "cb" to "comment_box", "ns" to "new_song",
    )

    /** Single-valued fields (6.2); also reachable as `{meta: name value}`. */
    private val FIELDS = setOf(
        "id", "title", "language", "subtitle", "sorttitle", "copyright", "license", "source", "melody",
        "variant-group", "redistribute", "key", "capo", "tempo", "time", "duration", "year",
    )
    private val MULTI = setOf("lyricist", "composer", "artist", "tag")
    /** ChordPro standard metadata the profile names but does not use (6.1, 10.3): kept, once each. */
    private val KNOWN_UNUSED_FIELDS = setOf("album", "notation")
    private val REMARKS = mapOf("comment" to RemarkStyle.PLAIN, "comment_italic" to RemarkStyle.ITALIC, "comment_box" to RemarkStyle.BOX)
    /** 7.5: accepted and ignored. */
    private val LAYOUT = setOf(
        "new_page", "np", "new_physical_page", "npp", "column_break", "colb", "columns", "col", "pagetype",
        "textfont", "textsize", "textcolour", "chordfont", "chordsize", "chordcolour", "tabfont", "tabsize", "tabcolour",
        "titles", "image", "grid", "g", "no_grid", "ng",
    )
    /** P3 environments a P1 app skips without telling (10.3); other unknown ones are I002. */
    private val P3_ENVIRONMENTS = setOf("tab", "grid", "abc")
}
