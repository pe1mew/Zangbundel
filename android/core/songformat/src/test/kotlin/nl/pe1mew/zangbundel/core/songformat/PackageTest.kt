// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.songformat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Library packages (FRD FR-LIB-08; song-format 12.11; the library manager's FDS draft 0.5 and later) against
 * the shared test libraries of FDS O-04 (corpus `packages/`, from `Zangbundel-bibliotheek-manager/test-libraries/`):
 * each must give the app's verdict and codes through [LibraryOpen], and the 1.1-only verdict through
 * [ArchiveImport], which is what an app before packages runs on the view.
 */
class PackageTest {
    private val app = "1.0.0"

    /** Ed25519 through the JDK, as libsodium does it in the app: the raw key wrapped in the fixed SubjectPublicKeyInfo header. */
    private val jdk = SignatureVerifier { publicKey, message, signature ->
        val spki = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00) + publicKey
        val key = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(spki))
        Signature.getInstance("Ed25519").run { initVerify(key); update(message); verify(signature) }
    }

    private fun resource(name: String): ByteArray =
        assertNotNull(javaClass.classLoader.getResourceAsStream("conformance/$name"), "missing $name").use { it.readBytes() }

    private fun expected(name: String) = Json.parseToJsonElement(String(resource("packages/$name.expected.json"), Charsets.UTF_8)) as JsonObject

    private val names: List<String> get() = String(resource("packages/index.txt"), Charsets.UTF_8).lines().filter { it.isNotBlank() }

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    /**
     * Vectors where the app and the shared test libraries (the manager's 0.1.0, FDS draft 0.7) still disagree.
     * `design/proposals/2026-11-06-shared-test-libraries.md`, approved by the owner 2026-11-06, keeps the app's
     * behaviour for each of these, and the manager changes the vector's `expected.json`. A vector leaves this list
     * when its new `expected.json` is copied here, and the test fails until it does, so the list is always the true
     * set of differences.
     */
    private val knownAppDifferences = setOf(
        // Proposal section 3.1: the ZIP reader refuses before the format is known, with its A-code.
        "zip-truncated", "zip-ratio", "path-traversal", "symlink", "entry-encrypted", "names-case",
        // 3.2: two top-level folders are not a package (APP-02), so A001, not PKG-05.
        "two-top-folders",
        // 3.4: the app reports the first refusing rule only.
        "license-missing", "song-folder-invalid",
    )

    /** As [knownAppDifferences], for the format 1.1 path ([ArchiveImport] on the view). Empty since `song-two-files` follows song format v0.23 (proposal 3.7). */
    private val knownFormat11Differences = setOf<String>()

    private fun codes(a: JsonObject, key: String) = a[key]?.jsonArray?.map { it.jsonPrimitive.content }?.sorted()

    /** Null when the app gives [name] the expected verdict and codes, else what differs. */
    private fun appDifference(name: String): String? {
        val want = expected(name).getValue("app").jsonObject
        val r = LibraryOpen.open(resource("packages/$name.zbl"), app, jdk)
        val verdict = want.getValue("verdict").jsonPrimitive.content
        val got = when { r.refused -> "refused"; r.pkg == null -> "accepted as snapshot"; else -> "accepted" }
        val fds = r.report.map { it.code }.filter { '-' in it }.distinct().sorted()
        val warnings = r.report.filter { it.level == ReportLevel.WARNING }.map { it.code }.distinct().sorted()
        val differs = listOfNotNull(
            "verdict $got, expected $verdict".takeIf { got != verdict },
            "FDS codes $fds, expected ${codes(want, "codes")}".takeIf { fds != codes(want, "codes") },
            codes(want, "song_format_warnings")?.let { w -> "warnings $warnings, expected $w".takeIf { warnings != w } },
            // A snapshot or a refused library has no package info, and is not signed.
            want["signed"]?.let { s -> "signed ${r.pkg?.signed ?: false}".takeIf { s.jsonPrimitive.boolean != (r.pkg?.signed ?: false) } },
            want["fingerprint"]?.let { f -> "fingerprint ${r.pkg?.author?.fingerprint}".takeIf { f.jsonPrimitive.content != r.pkg?.author?.fingerprint } },
        )
        if (differs.isEmpty()) return null
        val errors = r.report.filter { it.level == ReportLevel.LIBRARY_ERROR || it.level == ReportLevel.SONG_ERROR }
            .joinToString("; ") { listOfNotNull(it.code, it.path.ifEmpty { null }, it.detail).joinToString(" ") }
        return differs.joinToString("; ") + " | package ${PackageImport.isPackage(resource("packages/$name.zbl"))} | errors: $errors"
    }

    /** Null when the format 1.1 path gives [name] its expected verdict and every listed code. */
    private fun format11Difference(name: String): String? {
        val want = expected(name).getValue("format_1_1").jsonObject
        val r = ArchiveImport.open(resource("packages/$name.zbl"), app)
        val differs = listOfNotNull(
            "refused ${r.refused}".takeIf { want.getValue("verdict").jsonPrimitive.content.startsWith("refused") != r.refused },
            codes(want, "codes")!!.filter { c -> r.report.none { it.code == c } }.takeIf { it.isNotEmpty() }?.let { "missing $it" },
            "read the manifest".takeIf { r.pkg != null },
        )
        return if (differs.isEmpty()) null else differs.joinToString("; ") + " | report: ${r.report.map { it.code }}"
    }

    /** Writes the differences to `build/reports/package-vectors.md`, the table behind the proposal. */
    private fun record(title: String, found: Map<String, String>) {
        val file = java.io.File("build/reports/package-vectors-$title.md")
        file.parentFile.mkdirs()
        file.writeText(found.entries.joinToString("\n", "# $title: ${found.size} of ${names.size}\n\n", "\n") { (n, d) -> "- `$n`: $d" })
    }

    @Test
    fun `every package vector is intact and listed`() {
        assertTrue(names.size >= 56, "vectors: $names")
        for (name in names) assertEquals(expected(name).getValue("sha256").jsonPrimitive.content, sha(resource("packages/$name.zbl")), "$name: vector bytes changed")
    }

    @Test
    fun `every package vector gives the app's verdict and codes, apart from the known differences`() {
        val found = names.associateWith { appDifference(it) }.filterValues { it != null }.mapValues { it.value!! }
        record("app", found)
        assertEquals(knownAppDifferences, found.keys, found.entries.joinToString("\n") { "${it.key}: ${it.value}" })
    }

    @Test
    fun `an app before packages imports every vector's view as expected, apart from the known differences`() {
        val found = names.associateWith { format11Difference(it) }.filterValues { it != null }.mapValues { it.value!! }
        record("format-1-1", found)
        assertEquals(knownFormat11Differences, found.keys, found.entries.joinToString("\n") { "${it.key}: ${it.value}" })
    }

    @Test
    fun `the valid package installs its songs and sets, with its version as revision`() {
        val r = LibraryOpen.open(resource("packages/valid-signed.zbl"), app, jdk)
        assertFalse(r.refused, "${r.report}")
        val info = assertNotNull(r.pkg)
        assertEquals(info.version.toString(), r.revision)
        assertEquals(info.libraryId, r.library?.library?.id, "the view and the package are one library (FR-LIB-09)")
        assertEquals(info.sets.size, r.library?.setlists?.size)
        assertEquals(info.sets.indices.map { "set-${it + 1}" }, r.library?.setlists?.map { it.setlist.id })
        assertTrue(r.report.none { it.code == LibraryOpen.NOT_SIGNED }, "signed: no W013")
        assertTrue(r.files.keys.any { it.startsWith("songs/") })
    }

    @Test
    fun `an empty set is accepted and not offered, and an empty library is accepted`() {
        val set = LibraryOpen.open(resource("packages/set-empty.zbl"), app, jdk)
        assertFalse(set.refused, "${set.report}")
        assertEquals(listOf("set-1"), set.library?.setlists?.map { it.setlist.id }, "the empty set-2 is not a setlist")
        assertTrue(set.files.keys.none { it == "setlists/set-2.zbs" }, "nor installed")
        val none = LibraryOpen.open(resource("packages/no-songs.zbl"), app, jdk)
        assertFalse(none.refused, "${none.report}")
        assertEquals(0, none.library?.songs?.size)
    }

    /** [zip] with extra entries, each named relative to its library folder. */
    private fun withEntries(zip: ByteArray, vararg extra: Pair<String, String>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { z ->
            var top = ""
            java.util.zip.ZipInputStream(zip.inputStream()).use { input ->
                generateSequence { input.nextEntry }.forEach { e ->
                    top = e.name.substringBefore('/')
                    z.putNextEntry(java.util.zip.ZipEntry(e.name)); input.copyTo(z); z.closeEntry()
                }
            }
            for ((name, text) in extra) { z.putNextEntry(java.util.zip.ZipEntry("$top/$name")); z.write(text.toByteArray()); z.closeEntry() }
        }
        return out.toByteArray()
    }

    @Test
    fun `system files are ignored regardless of case`() {
        val base = resource("packages/valid-unsigned.zbl")
        val bytes = withEntries(base, "Desktop.ini" to "[x]", "songs/vader-jacob/THUMBS.DB" to "x", ".ds_store" to "x")
        val r = LibraryOpen.open(bytes, app, jdk)
        assertFalse(r.refused, "${r.report}")
        assertTrue(LibraryOpen.open(withEntries(base, "Desktop.txt" to "x"), app, jdk).report.any { it.code == "STR-01" }, "only those names are ignored")
    }

    @Test
    fun `a song error in a package carries its FDS code`() {
        fun entry(code: String, detail: String? = null) = ReportEntry(ReportLevel.SONG_ERROR, code, "songs/a/a.cho", detail = detail)
        assertEquals("SNG-02", PackageImport.sngCode(entry("S003", "title, language")))
        assertEquals("SNG-11", PackageImport.sngCode(entry("S003", "language")))
        assertEquals("SNG-12", PackageImport.sngCode(entry("S003", "id")))
        assertEquals("SNG-12", PackageImport.sngCode(entry("S004")))
        assertEquals("S005", PackageImport.sngCode(entry("S005")), "no FDS code: unchanged")
    }

    @Test
    fun `old formats get W013 once, and nothing else changes`() {
        for (name in listOf("release", "snapshot-top-directory")) {
            val bytes = resource("archives/$name.zbl")
            val plain = ArchiveImport.open(bytes, app)
            val opened = LibraryOpen.open(bytes, app, jdk)
            assertFalse(opened.refused)
            assertEquals(plain.report + ReportEntry(ReportLevel.WARNING, "W013", ""), opened.report, name)
            assertEquals(plain.revision, opened.revision)
        }
        val refused = LibraryOpen.open(resource("archives/encrypted.zbl"), app, jdk)
        assertTrue(refused.refused)
        assertTrue(refused.report.none { it.code == "W013" }, "a refused archive gets no W013")
    }

    @Test
    fun `the fingerprint is SHA-256 over the raw key, and the wrapped key gives another`() {
        val raw = Base64.getDecoder().decode("11qYAYKxCrfVS/7TyWQHOg7hcvPapiMlrwIaaPcHURo=")
        assertEquals("21FE-31DF-A154-A261", PackageImport.fingerprint(raw))
        val wrapped = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00) + raw
        assertEquals("06E3-FD8F-DA29-BB60", PackageImport.fingerprint(wrapped))
    }

    @Test
    fun `a manifest that breaks section 4 point 1 is refused with its rule`() {
        fun manifest(edit: (String) -> String): PackageImport.Manifest {
            val base = """{"formatVersion":1,"libraryId":"3f6c2a1e-8b4d-4c1a-9e2f-7d5b0a6c9e11","version":4,"title":"T",
                "created":"2026-03-14T18:22:05Z","modified":"2026-11-04T09:41:30Z",
                "license":{"id":"CC0-1.0","summary":"Free."},"maintainer":{"name":"M","email":"m@example.org"},
                "sets":[],"files":[]}"""
            return PackageImport.parseManifest(edit(base).toByteArray())
        }
        assertTrue(manifest { it } is PackageImport.Manifest.Read)
        fun code(edit: (String) -> String) = (manifest(edit) as? PackageImport.Manifest.Refused)?.code
        assertEquals("MAN-03", code { it.replace("\"formatVersion\":1", "\"formatVersion\":2") })
        assertEquals("MAN-02", code { it.replace("3f6c2a1e", "3F6C2A1E") })
        assertEquals("MAN-02", code { it.replace("-4c1a-", "-1c1a-") })
        assertEquals("MAN-02", code { it.replace("\"version\":4", "\"version\":0") })
        assertEquals("MAN-02", code { it.replace("\"title\":\"T\"", "\"title\":\"${"x".repeat(81)}\"") })
        assertEquals("MAN-07", code { it.replace(""""license":{"id":"CC0-1.0","summary":"Free."},""", "") })
        assertEquals("MAN-08", code { it.replace(""","email":"m@example.org"""", "") })
        assertEquals("MAN-08", code { it.replace(""""email":"m@example.org"""", """"url":"http://example.org"""") })
        assertEquals("SET-02", code { it.replace("\"sets\":[]", """"sets":[{"name":"A","songs":[]},{"name":"a","songs":[]}]""") })
        assertEquals("MAN-02", code { it.replace("\"files\":[]", """"files":[{"path":"b","size":1,"sha256":"${"0".repeat(64)}"},{"path":"a","size":1,"sha256":"${"0".repeat(64)}"}]""") })
    }
}
