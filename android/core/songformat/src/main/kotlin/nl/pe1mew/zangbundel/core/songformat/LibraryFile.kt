// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.songformat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import nl.pe1mew.zangbundel.core.model.isValidId

/** The library file `zangbundel.json` (song-format section 4.2), values as written. */
data class Library(
    val id: String,
    val name: String,
    val description: String,
    val maintainer: Maintainer,
    val license: LibraryLicense,
    val formatVersion: String,
    val minAppVersion: String,
    val homepage: String? = null,
    val releaseIndex: String? = null,
    val languages: List<String> = emptyList(),
    /** Localised tag labels: tag → (language → label) [FR-LIB-30]. */
    val tags: Map<String, Map<String, String>> = emptyMap(),
) {
    data class Maintainer(val name: String, val email: String?, val url: String?)
    data class LibraryLicense(val id: String, val file: String, val summary: String)
}

/**
 * Reads `zangbundel.json` (4.2, 4.3) and reports the library codes of 11.3: L002 not valid JSON or a
 * MUST field missing or of the wrong type, L003 wrong `format`, L004 a `format_version` major this app
 * does not implement, L005 a `min_app_version` newer than the app, L006 an invalid id or one that an
 * installed library from another URL already owns. Unknown fields are ignored (4.2); MAY fields of
 * the wrong type are ignored too.
 *
 * Untrusted input (assessment SP-09): the size and the nesting depth are checked before the JSON
 * parser, which recurses, sees the bytes.
 */
object LibraryFileParser {

    class Result(val library: Library?, val report: List<ReportEntry>)

    /**
     * [appVersion] is this app's `major.minor.patch`; [installed] maps the ids of configured libraries
     * to their repository URLs and [url] is the one being added, for L006's clash rule.
     */
    fun parse(bytes: ByteArray, appVersion: String, url: String? = null, installed: Map<String, String> = emptyMap()): Result {
        val report = ArrayList<ReportEntry>()
        fun refuse(code: String, detail: String): Result {
            report += ReportEntry(ReportLevel.LIBRARY_ERROR, code, PATH, detail = detail)
            return Result(null, report)
        }
        if (bytes.size > Limits.LIBRARY_FILE_MAX_BYTES) return refuse("L002", "${bytes.size} bytes")
        val text = try { Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString() } catch (e: java.nio.charset.CharacterCodingException) {
            return refuse("L002", "not valid UTF-8")
        }
        if (depth(text) > MAX_DEPTH) return refuse("L002", "nested deeper than $MAX_DEPTH")
        val root = try { Json.parseToJsonElement(text) } catch (e: Exception) { return refuse("L002", "not valid JSON") } as? JsonObject
            ?: return refuse("L002", "not a JSON object")

        fun string(o: JsonObject, key: String): String? = (o[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        fun required(o: JsonObject, key: String, where: String = ""): String = string(o, key) ?: throw Missing("$where$key")

        return try {
            val format = required(root, "format")
            if (format != FORMAT) return refuse("L003", format)
            val formatVersion = required(root, "format_version")
            val fv = FORMAT_VERSION.matchEntire(formatVersion) ?: throw Missing("format_version")
            if (fv.groupValues[1].toInt() != SUPPORTED_MAJOR) return refuse("L004", formatVersion)
            val id = required(root, "id")
            val name = required(root, "name")
            if (name.length > NAME_MAX_CHARS) throw Missing("name over $NAME_MAX_CHARS characters")
            val description = required(root, "description")
            val m = root["maintainer"] as? JsonObject ?: throw Missing("maintainer")
            val maintainer = Library.Maintainer(required(m, "name", "maintainer."), string(m, "email"), string(m, "url"))
            if (maintainer.email == null && maintainer.url == null) throw Missing("maintainer.email or maintainer.url")
            val l = root["license"] as? JsonObject ?: throw Missing("license")
            val license = Library.LibraryLicense(required(l, "id", "license."), required(l, "file", "license."), required(l, "summary", "license."))
            val minApp = required(root, "min_app_version")
            if (!SEMVER.matches(minApp)) throw Missing("min_app_version")
            if (compareVersions(minApp, appVersion) > 0) return refuse("L005", "$minApp, this app is $appVersion")
            if (!isValidId(id)) return refuse("L006", "id $id")
            installed[id]?.let { owner -> if (url != null && owner != url) return refuse("L006", "$id belongs to $owner") }
            val library = Library(
                id = id, name = name, description = description, maintainer = maintainer, license = license,
                formatVersion = formatVersion, minAppVersion = minApp,
                homepage = string(root, "homepage"), releaseIndex = string(root, "release_index"),
                languages = (root["languages"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }.orEmpty(),
                tags = (root["tags"] as? JsonObject)?.mapNotNull { (tag, labels) ->
                    (labels as? JsonObject)?.let { o -> tag to o.mapNotNull { (lang, v) -> (v as? JsonPrimitive)?.takeIf { it.isString }?.let { lang to it.content } }.toMap() }
                }?.toMap().orEmpty(),
            )
            Result(library, report)
        } catch (e: Missing) {
            refuse("L002", e.message ?: "missing field")
        }
    }

    /** `major.minor.patch` compared numerically; a part beyond `Int` counts as larger than any. */
    fun compareVersions(a: String, b: String): Int {
        val pa = a.split('.').map { it.toIntOrNull() ?: Int.MAX_VALUE }
        val pb = b.split('.').map { it.toIntOrNull() ?: Int.MAX_VALUE }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val c = (pa.getOrElse(i) { 0 }).compareTo(pb.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    /** The deepest nesting of objects and arrays, strings skipped; also bounds the archive manifest. */
    internal fun depth(text: String): Int {
        var depth = 0
        var max = 0
        var inString = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inString) {
                if (c == '\\') i++ else if (c == '"') inString = false
            } else when (c) {
                '"' -> inString = true
                '{', '[' -> { depth++; max = maxOf(max, depth) }
                '}', ']' -> depth--
            }
            i++
        }
        return max
    }

    private class Missing(what: String) : Exception(what)

    const val PATH = "zangbundel.json"
    const val FORMAT = "zangbundel-library"
    /** The format major this app implements (10.4). */
    const val SUPPORTED_MAJOR = 1
    /** The format version this app implements in full, for the About screen (1.1: setlists, section 13). */
    const val IMPLEMENTED_VERSION = "1.1"
    const val NAME_MAX_CHARS = 80
    /** Objects and arrays nest at most three deep in a valid file (tags); far more is refused. */
    const val MAX_DEPTH = 16
    private val FORMAT_VERSION = Regex("^([0-9]+)\\.([0-9]+)$")
    private val SEMVER = Regex("^[0-9]+\\.[0-9]+\\.[0-9]+$")
}
