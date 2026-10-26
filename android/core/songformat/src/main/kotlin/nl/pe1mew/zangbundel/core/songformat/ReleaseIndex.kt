// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.songformat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** A library's release index `releases.json` (song-format 12.6). */
data class ReleaseIndex(val library: String, val releases: List<Release>) {
    data class Release(val version: String, val revision: String, val created: String, val url: String, val size: Long, val sha256: String) {
        internal val createdAt: OffsetDateTime get() = OffsetDateTime.parse(created)
    }

    /** The release to install: ordered by `created`, never by version string (12.6). */
    val newest: Release? get() = releases.maxByOrNull { it.createdAt }

    companion object {
        const val FORMAT = "zangbundel-releases"
        /** An index lists releases, not songs; a few hundred kilobytes is already a long history. */
        const val MAX_BYTES = 1024 * 1024

        /**
         * Reads an index, untrusted (assessment SP-09): bounded size and depth; a release with a
         * malformed field, a non-HTTPS URL, a bad revision or SHA-256 is left out. Null when the file
         * is not an index at all.
         */
        fun parse(bytes: ByteArray): ReleaseIndex? {
            if (bytes.size > MAX_BYTES) return null
            val text = try { Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString() } catch (e: CharacterCodingException) { return null }
            if (LibraryFileParser.depth(text) > LibraryFileParser.MAX_DEPTH) return null
            val root = try { Json.parseToJsonElement(text) as? JsonObject } catch (e: Exception) { null } ?: return null
            fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (root.str("format") != FORMAT) return null
            val library = root.str("library") ?: return null
            val releases = (root["releases"] as? JsonArray ?: return null).mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val url = o.str("url")?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
                val revision = o.str("revision")?.takeIf { REVISION.matches(it) } ?: return@mapNotNull null
                val sha = o.str("sha256")?.lowercase()?.takeIf { SHA256.matches(it) } ?: return@mapNotNull null
                val created = o.str("created")?.takeIf { isTime(it) } ?: return@mapNotNull null
                val size = (o["size"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull?.takeIf { it > 0 } ?: return@mapNotNull null
                Release(o.str("version") ?: "", revision, created, url, size, sha)
            }
            return ReleaseIndex(library, releases)
        }

        private fun isTime(s: String) = try { OffsetDateTime.parse(s); true } catch (e: DateTimeParseException) { false }
        private val REVISION = Regex("^([0-9a-f]{40}|[0-9a-f]{64})$")
        private val SHA256 = Regex("^[0-9a-f]{64}$")
    }
}

/**
 * What a URL the user pasted points at (guiDesign D-50, song-format 12.6): an archive, a release index,
 * or a repository whose index is its `releases.json` on the default branch. Only HTTPS (A-14).
 */
sealed interface LibraryUrl {
    val url: String
    data class Archive(override val url: String) : LibraryUrl
    data class Index(override val url: String) : LibraryUrl
    /** Not HTTPS, or a host whose raw-file URL the app does not know: the user pastes the index or archive URL. */
    data class Unsupported(override val url: String, val reason: String) : LibraryUrl

    companion object {
        fun of(input: String): LibraryUrl {
            // An address typed without a scheme ("github.com/groep/bundel") is taken as HTTPS; http:// stays refused.
            val url = input.trim().let { if ("://" in it) it else "https://$it" }
            if (!url.startsWith("https://")) return Unsupported(url, "not https")
            val path = url.substringBefore('?').substringBefore('#').trimEnd('/')
            val lower = path.lowercase()
            if (lower.endsWith(".zbl") || lower.endsWith(".zip")) return Archive(url)
            if (lower.endsWith(".json")) return Index(url)
            GITHUB.matchEntire(path)?.let { m -> return Index("https://raw.githubusercontent.com/${m.groupValues[1]}/${m.groupValues[2].removeSuffix(".git")}/HEAD/releases.json") }
            GITLAB.matchEntire(path)?.let { m -> return Index("https://gitlab.com/${m.groupValues[1]}/${m.groupValues[2].removeSuffix(".git")}/-/raw/HEAD/releases.json") }
            return Unsupported(url, "unknown host: paste the URL of releases.json or of a .zbl archive")
        }

        private val GITHUB = Regex("^https://github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)$")
        private val GITLAB = Regex("^https://gitlab\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)$")
    }
}
