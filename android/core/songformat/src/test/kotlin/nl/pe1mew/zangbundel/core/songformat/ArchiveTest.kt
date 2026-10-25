// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.songformat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** Release archives and snapshots (song-format 12), against the archive vectors of the conformance corpus. */
class ArchiveTest {
    private val app = "1.0.0"

    private fun resource(name: String): ByteArray =
        assertNotNull(javaClass.classLoader.getResourceAsStream("conformance/$name"), "missing $name").use { it.readBytes() }

    private fun archive(name: String) = resource("archives/$name.zbl")

    @Test
    fun `every archive vector gives the expected verdict and codes`() {
        val names = String(resource("archives/index.txt"), Charsets.UTF_8).lines().filter { it.isNotBlank() }
        assertTrue(names.size >= 20, "vectors: $names")
        for (name in names) {
            val bytes = archive(name)
            val expected = Json.parseToJsonElement(String(resource("archives/$name.expected.json"), Charsets.UTF_8)) as JsonObject
            assertEquals(expected.getValue("sha256").jsonPrimitive.content, MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, "$name: vector bytes changed")
            val r = ArchiveImport.open(bytes, app)
            assertEquals(expected.getValue("refused").jsonPrimitive.boolean, r.refused, "$name: ${r.report}")
            assertEquals(expected.getValue("codes").jsonArray.map { it.jsonPrimitive.content }, r.report.map { it.code }.distinct().sorted(), "$name: ${r.report}")
        }
    }

    @Test
    fun `a release archive installs its revision and the canonical songs`() {
        val r = ArchiveImport.open(archive("release"), app)
        assertFalse(r.refused)
        assertEquals("9f2c1e7d4b0a6c3e8f5d2a1b7c4e9f0d3a6b8c1e", r.revision)
        assertEquals("2026.10.0", r.manifest?.version)
        assertEquals(setOf("zangbundel.json", "LICENSE", "songs/en/fire-in-the-night.cho", "songs/nl/vuur-in-de-nacht.cho"), r.files.keys)
        assertContentEquals(resource("songs/vuur-in-de-nacht.cho"), r.files.getValue("songs/nl/vuur-in-de-nacht.cho"))
        assertEquals(2, r.library?.songs?.size)
    }

    @Test
    fun `a snapshot in one top directory is read from inside it, with a snapshot revision`() {
        val bytes = archive("snapshot-top-directory")
        val r = ArchiveImport.open(bytes, app)
        assertFalse(r.refused)
        assertNull(r.manifest)
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals("snapshot-" + sha.substring(0, 16), r.revision)
        assertTrue("songs/nl/vuur-in-de-nacht.cho" in r.files, "paths without the top directory: ${r.files.keys}")
    }

    @Test
    fun `a notation file whose hash differs is dropped, the songs stay`() {
        val r = ArchiveImport.open(archive("notation-bad-sha"), app)
        assertFalse(r.refused)
        assertFalse("notation/x.musicxml" in r.files)
        assertEquals(2, r.library?.songs?.size)
    }

    @Test
    fun `downloaded bytes are checked against the release index`() {
        val bytes = archive("release")
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertNull(ArchiveImport.verifyDownload(bytes, sha))
        assertEquals("A013", ArchiveImport.verifyDownload(bytes, "0".repeat(64))?.code)
    }

    @Test
    fun `an installed library from another URL with the same id is refused`() {
        val r = ArchiveImport.open(archive("release"), app, url = "https://b", installed = mapOf("zangbundel-voorbeeld" to "https://a"))
        assertTrue(r.refused)
        assertEquals("L006", r.report.single().code)
    }

    @Test
    fun `damaged archives never make it throw`() {
        val random = Random(20261025)
        val good = archive("release")
        repeat(5_000) {
            val bytes = good.copyOf()
            repeat(random.nextInt(1, 8)) { bytes[random.nextInt(bytes.size)] = random.nextInt(256).toByte() }
            val cut = if (random.nextInt(5) == 0) bytes.copyOf(random.nextInt(bytes.size)) else bytes
            try {
                val r = ArchiveImport.open(cut, app)
                if (r.refused) assertTrue(r.report.any { it.level == ReportLevel.LIBRARY_ERROR })
            } catch (e: Throwable) {
                fail("${e::class.simpleName}: ${e.message}")
            }
        }
    }
}
