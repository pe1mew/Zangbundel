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
import kotlin.test.assertNull
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

    @Test
    fun `every package vector gives the app's verdict and codes`() {
        assertTrue(names.size >= 6, "vectors: $names")
        for (name in names) {
            val bytes = resource("packages/$name.zbl")
            val e = expected(name)
            assertEquals(e.getValue("sha256").jsonPrimitive.content, sha(bytes), "$name: vector bytes changed")
            assertTrue(PackageImport.isPackage(bytes), "$name is a package")
            val want = e.getValue("app").jsonObject
            val r = LibraryOpen.open(bytes, app, jdk)
            assertEquals(want.getValue("verdict").jsonPrimitive.content == "refused", r.refused, "$name: ${r.report}")
            val fdsCodes = r.report.map { it.code }.filter { '-' in it }.distinct().sorted()
            assertEquals(want.getValue("codes").jsonArray.map { it.jsonPrimitive.content }.sorted(), fdsCodes, "$name: ${r.report}")
            want["song_format_warnings"]?.let { w ->
                assertEquals(w.jsonArray.map { it.jsonPrimitive.content }.sorted(), r.report.filter { it.level == ReportLevel.WARNING }.map { it.code }.distinct().sorted(), "$name: ${r.report}")
            }
            want["signed"]?.let { assertEquals(it.jsonPrimitive.boolean, r.pkg?.signed, "$name signed") }
            want["fingerprint"]?.let { assertEquals(it.jsonPrimitive.content, r.pkg?.author?.fingerprint, "$name fingerprint") }
        }
    }

    @Test
    fun `an app before packages imports every vector's view as a snapshot`() {
        for (name in names) {
            val want = expected(name).getValue("format_1_1").jsonObject
            val r = ArchiveImport.open(resource("packages/$name.zbl"), app)
            assertEquals(want.getValue("verdict").jsonPrimitive.content.startsWith("refused"), r.refused, "$name: ${r.report}")
            want.getValue("codes").jsonArray.map { it.jsonPrimitive.content }.forEach { code ->
                assertTrue(r.report.any { it.code == code }, "$name: $code expected in ${r.report}")
            }
            assertNull(r.pkg, "$name: the 1.1 path never reads the manifest")
        }
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
