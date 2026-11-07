// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.songformat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import nl.pe1mew.zangbundel.core.model.isValidId
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.Base64

/** Checks an Ed25519 signature over [message]; the app passes libsodium's (architecture A-26), tests the JDK's. */
fun interface SignatureVerifier {
    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean
}

/** What a library package says about itself in its manifest (the library manager's FDS §4.1), as checked. */
data class PackageInfo(
    val libraryId: String,
    val version: Int,
    val title: String,
    val description: String?,
    val created: String,
    val modified: String,
    val changes: String?,
    val licenseId: String,
    val licenseSummary: String,
    val licenseText: String?,
    val maintainer: Library.Maintainer,
    val sets: List<Set>,
    val author: Author?,
    val files: List<File>,
) {
    data class Set(val name: String, val songs: List<String>)
    data class File(val path: String, val size: Long, val sha256: String)

    /** The signer as the manifest names it (not verified, FDS limitation 1), its raw key (SG-05) and fingerprint (SG-20). */
    class Author(val name: String, val publicKey: ByteArray, val fingerprint: String)

    val signed: Boolean get() = author != null
}

/**
 * Opens a library package (FRD FR-LIB-08; song-format 12.11; the library manager's FDS draft 0.5,
 * §3, §4, §5 and the rules of §6 that the app applies). A package is accepted whole or not at all
 * (APP-01): the first Fatal or Error rule refuses it, with that rule's code in the report.
 *
 * The package is read from its manifest. Its format 1.1 view (§3.7) is what an older app imports; this
 * reader checks it against the manifest (VIEW-01 to VIEW-03) and then runs the library and song checks
 * of song-format section 11 over the view and the songs, so the songs are parsed exactly as for an
 * archive. The signature is checked with [SignatureVerifier], so this module holds no crypto code.
 *
 * Untrusted input (assessment SP-09, SP-37, SP-39): the container is bounded by [ZipReader], JSON by size
 * and depth, every string by the FDS length before it is kept.
 */
object PackageImport {

    const val MANIFEST = "manifest.json"
    const val SIGNATURE = "manifest.sig"
    const val LICENSE = "LICENSE"
    const val VIEW_FILE = "zangbundel.json"
    const val SUPPORTED_FORMAT = 1
    /** FDS PF-41: what the view's `zangbundel.json` holds, and nothing else. */
    const val VIEW_FORMAT_VERSION = "1.1"
    const val VIEW_MIN_APP_VERSION = "0.1.0"

    /** FDS §3.6: written by operating systems, never part of a library (PF-31); lower case, matched regardless of case. */
    private val IGNORED_FILES = setOf(".ds_store", "thumbs.db", "desktop.ini")
    private const val IGNORED_FOLDER = "__macosx/"

    private val LIBRARY_ID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
    private val UTC = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$")
    private val SHA256 = Regex("^[0-9a-f]{64}$")
    private val SET_FILE = Regex("^setlists/set-([1-9][0-9]*)\\.zbs$")
    private val RESERVED = Regex("^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?$", RegexOption.IGNORE_CASE)
    private const val FORBIDDEN = "<>:\"/\\|?*"

    /** True when [zip] is a package: `manifest.json` in its single top-level folder (FDS APP-02). False for anything else, a broken ZIP included. */
    fun isPackage(zip: ByteArray): Boolean {
        val reader = try { ZipReader.open(zip) } catch (e: ZipReader.Refused) { return false }
        val names = reader.entries.filterNot { it.isDirectory || ignored(it.name) }.map { it.name }
        val tops = names.map { it.substringBefore('/') }.toSet()
        val top = tops.singleOrNull() ?: return false
        return names.all { '/' in it } && "$top/$MANIFEST" in names
    }

    fun open(zip: ByteArray, appVersion: String, verifier: SignatureVerifier): ArchiveImport.Result {
        val sha = sha256Hex(zip)
        val report = ArrayList<ReportEntry>()
        fun refused(code: String, detail: String?, path: String = ""): ArchiveImport.Result {
            report += ReportEntry(ReportLevel.LIBRARY_ERROR, code, path, detail = detail)
            return ArchiveImport.Result(null, null, null, sha, emptyMap(), report)
        }
        // PKG-09 (the sharing threshold) is the Editor's alone, so no A101 here (APP-01: warnings are not shown).
        val reader = try { ZipReader.open(zip) } catch (e: ZipReader.Refused) { return refused(pkgCode(e), e.message) }

        // PKG-05: one top-level folder, the library folder; its name is not significant (PF-02).
        val all = reader.entries.filterNot { it.isDirectory || ignored(it.name) }
        val top = all.map { it.name.substringBefore('/') }.toSet().singleOrNull()
        if (top == null || all.any { '/' !in it.name }) return refused("PKG-05", "the ZIP must hold exactly one library folder")
        // PKG-06: PF-20 to PF-22 for every name on the way; PKG-07 (case) is the reader's A006.
        for (e in all) {
            if (e.name.length > 255) return refused("PKG-06", "path longer than 255 characters", e.name)
            e.name.split('/').firstOrNull { !validName(it) }?.let { return refused("PKG-06", "name \"$it\" is not allowed", e.name) }
        }
        val entries = all.associateBy { it.name.removePrefix("$top/") }

        return try {
            val manifestEntry = entries[MANIFEST] ?: return refused("MAN-01", "no manifest.json")
            if (manifestEntry.size > ArchiveImport.JSON_CAP) return refused("MAN-02", "${manifestEntry.size} bytes", MANIFEST)
            val manifestBytes = reader.read(manifestEntry)
            val info = when (val m = parseManifest(manifestBytes)) {
                is Manifest.Refused -> return refused(m.code, m.detail, MANIFEST)
                is Manifest.Read -> m.info
            }

            // STR-01, STR-05 to STR-08, STR-10, STR-11: the library folder's layout (FDS §3.2, draft 0.5).
            structure(entries.keys)?.let { (code, detail, path) -> return refused(code, detail, path) }

            // SIG-01 to SIG-03 (FDS §5): signed is author.publicKey and manifest.sig, both or neither.
            val signatureEntry = entries[SIGNATURE]
            if ((info.author != null) != (signatureEntry != null)) return refused("SIG-01", "author.publicKey and manifest.sig must both be present or both absent")
            if (info.author != null) {
                if (signatureEntry!!.size > ArchiveImport.JSON_CAP) return refused("SIG-02", "${signatureEntry.size} bytes", SIGNATURE)
                val signature = parseSignature(reader.read(signatureEntry)) ?: return refused("SIG-02", "not a valid Ed25519 signature file", SIGNATURE)
                if (!verifier.verify(info.author.publicKey, manifestBytes, signature)) return refused("SIG-03", "the signature does not match the manifest", SIGNATURE)
            }

            // MAN-04 to MAN-06 (MF-01, MF-02): every file listed, every listed file present, sizes and hashes equal.
            val listed = info.files.associateBy { it.path }
            for (path in entries.keys - MANIFEST - SIGNATURE) if (path !in listed) return refused("MAN-05", "not in the manifest", path)
            val files = LinkedHashMap<String, ByteArray>()
            for (f in info.files) {
                val e = entries[f.path] ?: return refused("MAN-04", "listed but missing", f.path)
                if (e.size != f.size) return refused("MAN-06", "size ${e.size}, manifest ${f.size}", f.path)
                val cap = if (f.path.startsWith(LibraryImport.SONGS)) Limits.SONG_FILE_MAX_BYTES.toLong() else ArchiveImport.JSON_CAP
                if (e.size > cap) return refused(if (f.path.startsWith(LibraryImport.SONGS)) "SNG-18" else "MAN-06", "${e.size} bytes", f.path)
                val bytes = reader.read(e)
                if (sha256Hex(bytes) != f.sha256) return refused("MAN-06", "SHA-256 differs from the manifest", f.path)
                files[f.path] = bytes
            }

            // SET-01: every song of a set is a song of the library.
            val songIds = files.keys.filter { it.startsWith(LibraryImport.SONGS) }.map { it.split('/')[1] }.toSet()
            for (set in info.sets) set.songs.firstOrNull { it !in songIds }?.let { return refused("SET-01", "set \"${set.name}\": $it is not a song of the library") }

            // VIEW-01 to VIEW-03 (FDS §3.7, §6.8): the 1.1 view is complete, agrees with the manifest and passes the 1.1 library checks.
            view(info, files, appVersion)?.let { (code, detail, path) -> return refused(code, detail, path) }

            // The songs and the view's setlists through the checks of song-format section 11, as for an archive; any error refuses (APP-01).
            // An empty library (STR-02) and an empty set (SET-03) are warnings the Editor saves, so neither refuses here; an empty set
            // is not offered as a setlist (FDS APP-23), as the 1.1 path skips it (proposal 2026-11-06-shared-test-libraries, 3.5, 3.6).
            val checked = LibraryImport.check(files, appVersion, allowEmpty = true)
            val emptySets = info.sets.withIndex().filter { it.value.songs.isEmpty() }.map { "setlists/set-${it.index + 1}.zbs" }.toSet()
            val errors = checked.report.filter { it.level == ReportLevel.LIBRARY_ERROR || it.level == ReportLevel.SONG_ERROR }
                .filterNot { it.code == "SL005" && it.path in emptySets }
            // The first refusing rule, with a song error under its FDS code (proposal 3.3, 3.4).
            errors.firstOrNull()?.let {
                report += it.copy(code = sngCode(it))
                return ArchiveImport.Result(null, null, null, sha, emptyMap(), report)
            }
            if (checked.setlists.size != info.sets.size - emptySets.size) return refused("VIEW-02", "the setlists do not all read as the manifest's sets", LibraryImport.SETLISTS)

            // FDS APP-01: Warnings and Info are not shown; W013 is the one warning a package adds (APP-22, song-format 11).
            if (!info.signed) report += ReportEntry(ReportLevel.WARNING, LibraryOpen.NOT_SIGNED, "")
            ArchiveImport.Result(checked, null, info.version.toString(), sha, ArchiveImport.toInstall(files, checked, checked.library!!), report, manifestBytes, info)
        } catch (e: ZipReader.Refused) {
            refused(pkgCode(e), e.message)
        }
    }

    /** SG-20: the first 8 bytes of SHA-256 over the raw 32-byte key, as `AB12-CD34-EF56-7890`. */
    fun fingerprint(publicKey: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(publicKey).copyOf(8)
            .joinToString("") { "%02X".format(it) }.chunked(4).joinToString("-")

    // --- the manifest (FDS §4.1) ---

    sealed interface Manifest {
        class Read(val info: PackageInfo) : Manifest
        class Refused(val code: String, val detail: String) : Manifest
    }

    private class Bad(val code: String, message: String) : Exception(message)

    /** FDS §4.1 with MAN-02, MAN-03, MAN-07, MAN-08, SET-02 and SG-05; unknown fields are ignored. */
    fun parseManifest(bytes: ByteArray): Manifest {
        val text = try { Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString() } catch (e: CharacterCodingException) {
            return Manifest.Refused("MAN-02", "not valid UTF-8")
        }
        if (text.startsWith('﻿')) return Manifest.Refused("MAN-02", "starts with a byte order mark")
        if (LibraryFileParser.depth(text) > LibraryFileParser.MAX_DEPTH) return Manifest.Refused("MAN-02", "nested too deep")
        val root = try { Json.parseToJsonElement(text) as? JsonObject } catch (e: Exception) { null } ?: return Manifest.Refused("MAN-02", "not a JSON object")
        return try {
            Manifest.Read(read(root))
        } catch (e: Bad) {
            Manifest.Refused(e.code, e.message ?: e.code)
        }
    }

    private fun read(root: JsonObject): PackageInfo {
        fun bad(what: String, code: String = "MAN-02"): Nothing = throw Bad(code, what)
        fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
        fun JsonObject.int(k: String): Long? = (this[k] as? JsonPrimitive)?.takeIf { !it.isString && '.' !in it.content && 'e' !in it.content.lowercase() }?.longOrNull
        fun String.characters() = codePointCount(0, length)
        fun JsonObject.text(k: String, min: Int, max: Int, code: String = "MAN-02"): String {
            val s = str(k) ?: bad("$k missing or not text", code)
            if (s.characters() !in min..max) bad("$k must be $min to $max characters", code)
            return s
        }
        fun JsonObject.optionalText(k: String, max: Int): String? {
            if (k !in this) return null
            val s = str(k) ?: bad("$k is not text")
            if (s.characters() > max) bad("$k is longer than $max characters")
            return s
        }
        fun JsonObject.time(k: String): String {
            val s = str(k) ?: bad("$k missing")
            if (!UTC.matches(s) || try { OffsetDateTime.parse(s); false } catch (e: DateTimeParseException) { true }) bad("$k is not a UTC time")
            return s
        }

        val formatVersion = root.int("formatVersion") ?: bad("formatVersion missing")
        if (formatVersion > SUPPORTED_FORMAT) bad("formatVersion $formatVersion", "MAN-03")
        if (formatVersion != SUPPORTED_FORMAT.toLong()) bad("formatVersion $formatVersion")
        val libraryId = root.str("libraryId")?.takeIf { LIBRARY_ID.matches(it) } ?: bad("libraryId is not a lower-case version 4 UUID")
        val version = root.int("version")?.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt() ?: bad("version must be a whole number from 1")
        val title = root.text("title", 1, 80)
        val description = root.optionalText("description", 1000)
        val created = root.time("created")
        val modified = root.time("modified")
        val changes = root.optionalText("changes", 500)

        val license = root["license"] as? JsonObject ?: bad("license missing", "MAN-07")
        val licenseId = license.text("id", 1, 100, "MAN-07")
        val licenseSummary = license.text("summary", 1, 200, "MAN-07")
        val licenseText = license.optionalText("text", 32_000)

        val m = root["maintainer"] as? JsonObject ?: bad("maintainer missing", "MAN-08")
        val maintainerName = m.text("name", 1, 100, "MAN-08")
        val email = m.str("email")?.takeIf { it.isNotBlank() && it.characters() <= 254 }
        val url = m.str("url")?.takeIf { it.startsWith("https://") && it.characters() <= 2000 }
        if (email == null && url == null) bad("maintainer needs an e-mail address or an https: URL", "MAN-08")

        val sets = (root["sets"] as? JsonArray ?: bad("sets missing")).map { e ->
            val o = e as? JsonObject ?: bad("a set is not an object")
            val songs = (o["songs"] as? JsonArray ?: bad("a set has no songs list")).map { s ->
                ((s as? JsonPrimitive)?.takeIf { it.isString }?.content)?.takeIf { isValidId(it) } ?: bad("a set lists an invalid song ID")
            }
            PackageInfo.Set(o.text("name", 1, 60), songs)
        }
        sets.groupBy { it.name.lowercase() }.values.firstOrNull { it.size > 1 }?.let { bad("two sets are named \"${it.first().name}\"", "SET-02") }

        val author = (root["author"] as? JsonObject)?.let { a ->
            val key = a.str("publicKey") ?: bad("author.publicKey missing")
            val raw = try { Base64.getDecoder().decode(key) } catch (e: IllegalArgumentException) { null }
            if (key.length != 44 || raw == null || raw.size != 32) bad("author.publicKey is not a raw 32-byte Ed25519 key in base64 (SG-05)")
            PackageInfo.Author(a.text("name", 1, 100), raw, fingerprint(raw))
        } ?: if ("author" in root) bad("author is not an object") else null

        val files = (root["files"] as? JsonArray ?: bad("files missing")).map { e ->
            val o = e as? JsonObject ?: bad("a files entry is not an object")
            val path = o.str("path")?.takeIf { it.isNotEmpty() && it.length <= 255 } ?: bad("a files entry has no path")
            val size = o.int("size")?.takeIf { it >= 0 } ?: bad("$path: size missing")
            val sha = o.str("sha256")?.takeIf { SHA256.matches(it) } ?: bad("$path: sha256 is not lower-case hex")
            PackageInfo.File(path, size, sha)
        }
        val paths = files.map { it.path }
        if (paths.toSet().size != paths.size) bad("a path is listed twice")
        if (paths != paths.sortedWith { a, b -> compareBytes(a, b) }) bad("files are not sorted by path")
        if (MANIFEST in paths || SIGNATURE in paths) bad("files lists manifest.json or manifest.sig")

        return PackageInfo(libraryId, version, title, description, created, modified, changes, licenseId, licenseSummary, licenseText,
            Library.Maintainer(maintainerName, email, url), sets, author, files)
    }

    /** `manifest.sig` (FDS §5.2): `{"algorithm": "Ed25519", "signature": "<base64 of 64 bytes>"}`; null when it is not that. */
    private fun parseSignature(bytes: ByteArray): ByteArray? {
        val text = try { Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString() } catch (e: CharacterCodingException) { return null }
        if (LibraryFileParser.depth(text) > 2) return null
        val o = try { Json.parseToJsonElement(text) as? JsonObject } catch (e: Exception) { null } ?: return null
        if ((o["algorithm"] as? JsonPrimitive)?.content != "Ed25519") return null
        val sig = (o["signature"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        return try { Base64.getDecoder().decode(sig).takeIf { it.size == 64 } } catch (e: IllegalArgumentException) { null }
    }

    // --- layout and view ---

    /** FDS STR rules over the paths of the library folder; null when they hold, else (code, detail, path). */
    private fun structure(paths: Set<String>): Triple<String, String, String>? {
        val songFolders = HashMap<String, MutableList<String>>()
        for (path in paths) {
            when {
                '/' !in path -> if (path !in setOf(MANIFEST, SIGNATURE, VIEW_FILE, LICENSE)) return Triple("STR-01", "does not belong in the library folder", path)
                path.startsWith(LibraryImport.SETLISTS) -> if (!SET_FILE.matches(path)) return Triple("VIEW-01", path, path)
                path.startsWith(LibraryImport.SONGS) -> {
                    val parts = path.split('/')
                    if (parts.size == 2) return Triple("STR-11", "songs/ holds only song folders", path)
                    if (parts.size > 3) return Triple("STR-07", "a song folder contains a subfolder", path)
                    if (!isValidId(parts[1])) return Triple("STR-10", "\"${parts[1]}\" is not a valid song ID", path)
                    songFolders.getOrPut(parts[1]) { ArrayList() } += parts[2]
                }
                else -> return Triple("STR-01", "does not belong in the library folder", path)
            }
        }
        for ((id, names) in songFolders) {
            val cho = names.filter { it.endsWith(LibraryImport.EXTENSION) }
            if (cho.size > 1) return Triple("STR-06", "more than one song file", "songs/$id/")
            if (names.any { !it.endsWith(LibraryImport.EXTENSION) }) return Triple("STR-08", "a file other than the song", "songs/$id/")
            if (cho.singleOrNull() != "$id${LibraryImport.EXTENSION}") return Triple("STR-05", "no song file $id.cho", "songs/$id/")
        }
        return null
    }

    /** VIEW-01 to VIEW-03 (FDS §3.7, PF-41 to PF-43); null when the view holds. */
    private fun view(info: PackageInfo, files: Map<String, ByteArray>, appVersion: String): Triple<String, String, String>? {
        val expectedSets = info.sets.indices.map { "setlists/set-${it + 1}.zbs" }.toSet()
        val presentSets = files.keys.filter { it.startsWith(LibraryImport.SETLISTS) }.toSet()
        val missing = listOf(VIEW_FILE, LICENSE).filter { it !in files } + (expectedSets - presentSets).sorted()
        if (missing.isNotEmpty()) return Triple("VIEW-01", missing.joinToString(", "), "")
        (presentSets - expectedSets).firstOrNull()?.let { return Triple("VIEW-01", it, it) }

        // VIEW-03 first: a view an older app would refuse is reported as such, whatever else differs.
        val parsed = LibraryFileParser.parse(files.getValue(VIEW_FILE), appVersion.takeIf { LibraryFileParser.compareVersions(it, VIEW_MIN_APP_VERSION) >= 0 } ?: VIEW_MIN_APP_VERSION)
        val library = parsed.library ?: return Triple("VIEW-03", parsed.report.joinToString { "${it.code} ${it.detail.orEmpty()}".trim() }, VIEW_FILE)

        // VIEW-02: zangbundel.json (PF-41), field by field and no other fields.
        val root = Json.parseToJsonElement(files.getValue(VIEW_FILE).toString(Charsets.UTF_8)) as JsonObject
        val keys = root.keys - setOf("format", "format_version", "id", "name", "description", "maintainer", "license", "min_app_version")
        val maintainerKeys = (root["maintainer"] as JsonObject).keys - setOf("name", "email", "url")
        val licenseKeys = (root["license"] as JsonObject).keys - setOf("id", "file", "summary")
        val differs = listOfNotNull(
            "fields ${keys + maintainerKeys + licenseKeys}".takeIf { keys.isNotEmpty() || maintainerKeys.isNotEmpty() || licenseKeys.isNotEmpty() },
            "format_version".takeIf { library.formatVersion != VIEW_FORMAT_VERSION },
            "id".takeIf { library.id != info.libraryId },
            "name".takeIf { library.name != info.title },
            "description".takeIf { library.description != (info.description ?: info.title) },
            "maintainer".takeIf { library.maintainer != info.maintainer },
            "license".takeIf { library.license != Library.LibraryLicense(info.licenseId, LICENSE, info.licenseSummary) },
            "min_app_version".takeIf { library.minAppVersion != VIEW_MIN_APP_VERSION },
        )
        if (differs.isNotEmpty()) return Triple("VIEW-02", differs.joinToString(", "), VIEW_FILE)

        // LICENSE (PF-42 as of FDS draft 0.6): the text, or the summary, with trailing LFs removed and exactly one LF added; compared byte for byte.
        val licence = ((info.licenseText ?: info.licenseSummary).trimEnd('\n') + "\n").toByteArray(Charsets.UTF_8)
        if (!files.getValue(LICENSE).contentEquals(licence)) return Triple("VIEW-02", "does not hold the manifest's licence", LICENSE)

        // setlists/set-<n>.zbs (PF-43): the set's name, id set-<n>, and its songs in order, nothing else.
        for ((i, set) in info.sets.withIndex()) {
            val path = "setlists/set-${i + 1}.zbs"
            val read = SetlistParser.parse(files.getValue(path), path, "set-${i + 1}.zbs", allowEmpty = set.songs.isEmpty()).setlist ?: return Triple("VIEW-02", "not a readable setlist", path)
            val entries = read.entries.map { (it as? Setlist.Entry.Song)?.let { s -> "${s.library}/${s.song}" } ?: "?" }
            if (read.title != set.name || read.id != "set-${i + 1}" || entries != set.songs.map { "${info.libraryId}/$it" }) {
                return Triple("VIEW-02", "does not match set \"${set.name}\"", path)
            }
        }
        return null
    }

    /** FDS §6.2 (draft 0.6), the Archive column: a package reports the ZIP reader's A-codes as PKG rules. */
    private fun pkgCode(e: ZipReader.Refused): String = when (e.code) {
        "A001" -> "PKG-01"
        "A002", "A003" -> "PKG-02"
        "A004" -> "PKG-04"
        "A005" -> "PKG-03"
        "A006" -> if (e.message.orEmpty().endsWith("occurs twice")) "PKG-07" else "PKG-03"
        else -> e.code
    }

    /**
     * FDS 6.6, the Profile column: a package reports the profile's song errors as SNG rules, as [pkgCode] does for
     * the ZIP reader (proposal 2026-11-06-shared-test-libraries, 3.3). S003 names its fields in the order id, title, language.
     */
    internal fun sngCode(e: ReportEntry): String = when (e.code) {
        "S001" -> "SNG-01"
        "S002" -> "SNG-18"
        "S003" -> when (e.detail?.substringBefore(',')?.trim()) { "title" -> "SNG-02"; "language" -> "SNG-11"; else -> "SNG-12" }
        "S004" -> "SNG-12"
        "S006" -> "SNG-03"
        "S007" -> "SNG-06"
        "S008" -> "SNG-13"
        "S009" -> "SNG-14"
        "S010" -> "SNG-09"
        "S011" -> "SNG-04"
        "S012" -> "SNG-15"
        else -> e.code
    }

    /** FDS §3.6, regardless of case (proposal 2026-11-06-shared-test-libraries, O-11 i): on Windows `Desktop.ini` is `desktop.ini`. */
    private fun ignored(name: String): Boolean {
        val n = name.lowercase()
        return n.startsWith(IGNORED_FOLDER) || "/$IGNORED_FOLDER" in n || n.substringAfterLast('/') in IGNORED_FILES
    }

    /** PF-20 to PF-22 for one name. */
    private fun validName(name: String): Boolean =
        name.isNotEmpty() && name.length <= 100 && name.none { it in FORBIDDEN || it.code < 0x20 || it.code == 0x7F } &&
            !name.endsWith(' ') && !name.endsWith('.') && !RESERVED.matches(name)

    /** Ordinal byte order of the UTF-8 encodings (FDS §4.1, `files`). */
    private fun compareBytes(a: String, b: String): Int {
        val x = a.toByteArray(Charsets.UTF_8)
        val y = b.toByteArray(Charsets.UTF_8)
        for (i in 0 until minOf(x.size, y.size)) {
            val c = (x[i].toInt() and 0xFF).compareTo(y[i].toInt() and 0xFF)
            if (c != 0) return c
        }
        return x.size.compareTo(y.size)
    }

    private fun sha256Hex(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
}

/**
 * What the app opens when the user adds a library from a file, a download or another app (FRD
 * FR-LIB-08; FDS APP-02): a package by its manifest, otherwise a release archive or snapshot as before.
 * Every library without a signature imports with W013 (song-format 11; FDS APP-22), which never refuses.
 *
 * [ArchiveImport.open] alone stays the format 1.1 path, which is what an app before packages runs and
 * what the shared `archives/` vectors check.
 */
object LibraryOpen {
    /** W013: library not signed (song-format 11.3). */
    const val NOT_SIGNED = "W013"

    fun open(zip: ByteArray, appVersion: String, verifier: SignatureVerifier, url: String? = null, installed: Map<String, String> = emptyMap()): ArchiveImport.Result {
        if (PackageImport.isPackage(zip)) return PackageImport.open(zip, appVersion, verifier)
        val r = ArchiveImport.open(zip, appVersion, url, installed)
        if (r.refused) return r
        return ArchiveImport.Result(r.library, r.manifest, r.revision, r.archiveSha256, r.files,
            r.report + ReportEntry(ReportLevel.WARNING, NOT_SIGNED, ""), r.manifestBytes, r.pkg)
    }
}
