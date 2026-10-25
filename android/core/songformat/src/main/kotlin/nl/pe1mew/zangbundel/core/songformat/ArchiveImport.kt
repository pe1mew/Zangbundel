// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.songformat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** The manifest `zangbundel-archive.json` of a release archive (song-format 12.4). */
data class ArchiveManifest(
    val archiveVersion: String,
    val library: String,
    val formatVersion: String,
    val version: String,
    val revision: String,
    val created: String,
    val sourceRepository: String,
    val sourceRef: String,
    val songs: List<Song>,
    val skipped: List<Skipped> = emptyList(),
    val notation: List<File> = emptyList(),
    val setlists: List<File> = emptyList(),
    val builder: String? = null,
    val previousVersion: String? = null,
    val previousRevision: String? = null,
) {
    data class Song(val path: String, val id: String, val hash: String, val size: Long)
    data class Skipped(val path: String, val code: String, val message: String?)
    data class File(val path: String, val sha256: String, val size: Long)
}

/**
 * Opens a release archive or a repository snapshot (song-format 12.5, 12.7 steps 1 to 5; 12.8): bounds
 * the container ([ZipReader]), reads the manifest, refuses what this app cannot use, verifies every
 * song against the manifest, and runs the library checks of section 11 on the content ([LibraryImport]).
 *
 * Installing (step 6) and recording (step 7) are the caller's; [Result.files] holds the verified
 * content by repository path, and [Result.revision] the revision to record.
 */
object ArchiveImport {

    class Result(
        val library: LibraryImport.Result?,
        val manifest: ArchiveManifest?,
        /** The manifest revision, or `snapshot-<16 hex of the archive's SHA-256>` for a snapshot (12.5). */
        val revision: String?,
        val archiveSha256: String,
        val files: Map<String, ByteArray>,
        val report: List<ReportEntry>,
    ) {
        val refused: Boolean get() = library == null || library.refused
    }

    fun open(zip: ByteArray, appVersion: String, url: String? = null, installed: Map<String, String> = emptyMap()): Result {
        val report = ArrayList<ReportEntry>()
        val sha = sha256Hex(zip)
        fun refused(code: String, detail: String?, path: String = ""): Result {
            report += ReportEntry(ReportLevel.LIBRARY_ERROR, code, path, detail = detail)
            return Result(null, null, null, sha, emptyMap(), report)
        }
        val reader = try { ZipReader.open(zip) } catch (e: ZipReader.Refused) { return refused(e.code, e.message) }
        if (zip.size > Limits.ARCHIVE_WARN_BYTES) report += ReportEntry(ReportLevel.WARNING, "A101", "", detail = "${zip.size} bytes")
        val byName = reader.entries.filterNot { it.isDirectory }.associateBy { it.name }

        // Release archive (manifest at the root) or snapshot (zangbundel.json at the root or in one top directory).
        val release = MANIFEST in byName
        val prefix = when {
            release || LibraryFileParser.PATH in byName -> ""
            else -> {
                val tops = byName.keys.map { it.substringBefore('/') + "/" }.toSet()
                val top = tops.singleOrNull()?.takeIf { t -> byName.keys.all { it.startsWith(t) } && (t + LibraryFileParser.PATH) in byName }
                top ?: return refused("A001", "no zangbundel.json at the root or in one top-level directory")
            }
        }
        val entries = byName.filterKeys { it.startsWith(prefix) }.mapKeys { it.key.removePrefix(prefix) }
        fun read(path: String, cap: Long): ByteArray? {
            val e = entries[path] ?: return null
            if (e.size > cap) throw ZipReader.Refused("A003", "$path: ${e.size} bytes")
            return reader.read(e)
        }

        return try {
            val manifest = if (release) parseManifest(read(MANIFEST, JSON_CAP)!!) ?: return refused("A007", "manifest", MANIFEST) else null
            if (manifest != null) {
                if (manifest.archiveVersion.substringBefore('.').toIntOrNull() != SUPPORTED_ARCHIVE_MAJOR) return refused("A008", manifest.archiveVersion, MANIFEST)
                if (!REVISION.matches(manifest.revision) || !isUtcTime(manifest.created)) return refused("A010", "${manifest.revision} ${manifest.created}", MANIFEST)
            }
            val libraryBytes = read(LibraryFileParser.PATH, JSON_CAP) ?: return refused("A001", "no zangbundel.json")
            val libraryFile = LibraryFileParser.parse(libraryBytes, appVersion, url, installed)
            val library = libraryFile.library ?: run {
                report += libraryFile.report
                return Result(null, manifest, null, sha, emptyMap(), report)
            }
            if (manifest != null && (manifest.library != library.id || manifest.formatVersion != library.formatVersion)) {
                return refused("A009", "manifest ${manifest.library} ${manifest.formatVersion}, library ${library.id} ${library.formatVersion}", MANIFEST)
            }

            val files = LinkedHashMap<String, ByteArray>()
            files[LibraryFileParser.PATH] = libraryBytes
            read(library.license.file, JSON_CAP)?.let { files[library.license.file] = it }
            val songPaths = entries.keys.filter { it.startsWith(LibraryImport.SONGS) && it.endsWith(LibraryImport.EXTENSION) }.sorted()
            for (path in songPaths) {
                if (entries.getValue(path).size > Limits.SONG_FILE_MAX_BYTES) {
                    report += ReportEntry(ReportLevel.SONG_ERROR, "S002", path, detail = "${entries.getValue(path).size} bytes")
                    continue
                }
                files[path] = reader.read(entries.getValue(path))
            }
            // Other files under songs/ are counted by the library check (I003), not read.
            entries.keys.filter { it.startsWith(LibraryImport.SONGS) && !it.endsWith(LibraryImport.EXTENSION) }.forEach { files[it] = ByteArray(0) }

            if (manifest != null) {
                verifySongs(manifest, files, report)?.let { return refused(it.first, it.second, it.third) }
                verifyListed(manifest.notation, "A103", entries, reader, files, report)
                verifyListed(manifest.setlists, "A104", entries, reader, files, report)
                manifest.skipped.forEach { report += ReportEntry(ReportLevel.SONG_ERROR, it.code, it.path, detail = it.message) }
            } else {
                report += ReportEntry(ReportLevel.WARNING, "A102", "")
                entries.keys.filter { it.startsWith(NOTATION) && entries.getValue(it).size <= NOTATION_CAP }.forEach { files[it] = reader.read(entries.getValue(it)) }
            }

            val checked = LibraryImport.check(files, appVersion, url, installed)
            report += checked.report
            val revision = manifest?.revision ?: "snapshot-" + sha.substring(0, 16)
            Result(checked, manifest, revision, sha, if (checked.refused) emptyMap() else toInstall(files, checked, library), report)
        } catch (e: ZipReader.Refused) {
            refused(e.code, e.message)
        }
    }

    /**
     * What step 6 installs: the library and licence files, every imported song in canonical form (8.3;
     * identical bytes for a release archive), and the verified attachments. Skipped songs and other
     * files under `songs/` stay out.
     */
    private fun toInstall(files: Map<String, ByteArray>, checked: LibraryImport.Result, library: Library): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        out[LibraryFileParser.PATH] = files.getValue(LibraryFileParser.PATH)
        out[library.license.file] = files.getValue(library.license.file)
        checked.songs.forEach { out[it.path] = it.song.canonical.toByteArray(Charsets.UTF_8) }
        files.filterKeys { !it.startsWith(LibraryImport.SONGS) && it !in out }.forEach { (k, v) -> out[k] = v }
        return out
    }

    /** A013: downloaded bytes against the `sha256` of the release index, before [open] (12.7). */
    fun verifyDownload(zip: ByteArray, expectedSha256: String): ReportEntry? =
        if (sha256Hex(zip).equals(expectedSha256, ignoreCase = true)) null
        else ReportEntry(ReportLevel.LIBRARY_ERROR, "A013", "", detail = "archive bytes do not match the release index")

    /** 12.7 step 4: canonical bytes (A011), the manifest's hash, nothing missing and nothing unlisted (A012). */
    private fun verifySongs(manifest: ArchiveManifest, files: Map<String, ByteArray>, report: MutableList<ReportEntry>): Triple<String, String, String>? {
        val listed = manifest.songs.associateBy { it.path }
        for (path in files.keys.filter { it.startsWith(LibraryImport.SONGS) && it.endsWith(LibraryImport.EXTENSION) }) {
            if (path !in listed) return Triple("A012", "not in the manifest", path)
        }
        for (song in manifest.songs) {
            val bytes = files[song.path] ?: if (report.any { it.path == song.path && it.code == "S002" }) continue else return Triple("A012", "listed but missing", song.path)
            val text = try { Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString() } catch (e: CharacterCodingException) { null }
            val canonical = text?.let { Canonical.canonicalise(it) }
            if (canonical == null || canonical.changed) return Triple("A011", "not in canonical form", song.path)
            if (Canonical.hash(canonical.text).toHex() != song.hash) return Triple("A012", "hash differs from the manifest", song.path)
        }
        return null
    }

    /** A103, A104: a listed attachment whose SHA-256 does not match, or that is missing, is dropped with a warning. */
    private fun verifyListed(
        listed: List<ArchiveManifest.File>,
        code: String,
        entries: Map<String, ZipReader.Entry>,
        reader: ZipReader,
        files: MutableMap<String, ByteArray>,
        report: MutableList<ReportEntry>,
    ) {
        for (f in listed) {
            val e = entries[f.path]
            val bytes = if (e != null && e.size <= NOTATION_CAP) reader.read(e) else null
            if (bytes == null || sha256Hex(bytes) != f.sha256) report += ReportEntry(ReportLevel.WARNING, code, f.path, detail = if (bytes == null) "missing" else "sha256 differs")
            else files[f.path] = bytes
        }
    }

    /** 12.4; null when it is not valid JSON or a MUST field is missing or of the wrong type (A007). */
    fun parseManifest(bytes: ByteArray): ArchiveManifest? {
        val text = try { Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString() } catch (e: CharacterCodingException) { return null }
        if (LibraryFileParser.depth(text) > LibraryFileParser.MAX_DEPTH) return null
        val root = try { Json.parseToJsonElement(text) as? JsonObject } catch (e: Exception) { null } ?: return null
        fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
        fun JsonObject.num(k: String): Long? = (this[k] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
        fun JsonObject.objects(k: String): List<JsonObject>? = (this[k] as? JsonArray)?.map { it as? JsonObject ?: return null }
        if (root.str("format") != FORMAT) return null
        val source = root["source"] as? JsonObject ?: return null
        val songs = root.objects("songs")?.map { s ->
            val hash = s.str("hash")?.takeIf { HASH.matches(it) } ?: return null
            ArchiveManifest.Song(s.str("path") ?: return null, s.str("id") ?: return null, hash, s.num("size") ?: return null)
        } ?: return null
        fun files(k: String): List<ArchiveManifest.File> = root.objects(k)?.mapNotNull { f ->
            val path = f.str("path") ?: return@mapNotNull null
            ArchiveManifest.File(path, f.str("sha256") ?: return@mapNotNull null, f.num("size") ?: 0)
        }.orEmpty()
        val version = root.str("version")?.takeIf { it.length <= VERSION_MAX_CHARS } ?: return null
        val previous = root["previous"] as? JsonObject
        return ArchiveManifest(
            archiveVersion = root.str("archive_version")?.takeIf { FORMAT_VERSION.matches(it) } ?: return null,
            library = root.str("library") ?: return null,
            formatVersion = root.str("format_version") ?: return null,
            version = version,
            revision = root.str("revision") ?: return null,
            created = root.str("created") ?: return null,
            sourceRepository = source.str("repository") ?: return null,
            sourceRef = source.str("ref") ?: return null,
            songs = songs,
            skipped = root.objects("skipped")?.mapNotNull { s -> s.str("path")?.let { ArchiveManifest.Skipped(it, s.str("code") ?: "S000", s.str("message")) } }.orEmpty(),
            notation = files("notation"),
            setlists = files("setlists"),
            builder = root.str("builder"),
            previousVersion = previous?.str("version"),
            previousRevision = previous?.str("revision"),
        )
    }

    private fun isUtcTime(s: String): Boolean =
        UTC.matches(s) && try { OffsetDateTime.parse(s); true } catch (e: DateTimeParseException) { false }

    private fun sha256Hex(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).toHex()
    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun nl.pe1mew.zangbundel.core.model.ContentHash.toHex() = bytes.toHex()

    const val MANIFEST = "zangbundel-archive.json"
    const val FORMAT = "zangbundel-archive"
    const val NOTATION = "notation/"
    const val SUPPORTED_ARCHIVE_MAJOR = 1
    const val VERSION_MAX_CHARS = 32
    /** The manifest of 10 000 songs is about 1.5 MB; JSON and licence entries above this are refused. */
    const val JSON_CAP = 4L * 1024 * 1024
    const val NOTATION_CAP = 16L * 1024 * 1024
    private val FORMAT_VERSION = Regex("^[0-9]+\\.[0-9]+$")
    private val REVISION = Regex("^([0-9a-f]{40}|[0-9a-f]{64})$")
    private val HASH = Regex("^[0-9a-f]{32}$")
    private val UTC = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]+)?Z$")
}
