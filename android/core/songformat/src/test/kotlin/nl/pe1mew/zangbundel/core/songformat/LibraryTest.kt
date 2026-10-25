// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.songformat

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** The library file (song-format 4) and the import checks across files (3, 11). */
class LibraryTest {
    private val app = "1.0.0"

    /** The example of section 4.4. */
    private val example = """
        {
          "format": "zangbundel-library",
          "format_version": "1.0",
          "id": "zangbundel-voorbeeld",
          "name": "Zangbundel voorbeeldbundel",
          "description": "Kampvuurliederen voor scouting. Vrij te gebruiken en te verspreiden; zie de licentie per lied.",
          "maintainer": {
            "name": "Zangbundel-project",
            "email": "bundel@example.org",
            "url": "https://example.org/zangbundel-voorbeeld"
          },
          "license": {
            "id": "CC-BY-SA-4.0",
            "file": "LICENSE",
            "summary": "Creative Commons Naamsvermelding-GelijkDelen 4.0, tenzij een lied anders aangeeft."
          },
          "min_app_version": "1.0.0",
          "homepage": "https://example.org/zangbundel-voorbeeld",
          "languages": ["nl", "en"],
          "tags": {
            "kampvuur": { "nl": "Kampvuur", "en": "Campfire" },
            "avond":    { "nl": "Avond",    "en": "Evening" },
            "kerst":    { "nl": "Kerst",    "en": "Christmas" }
          }
        }
    """.trimIndent()

    private fun parse(json: String, url: String? = null, installed: Map<String, String> = emptyMap()) =
        LibraryFileParser.parse(json.toByteArray(), app, url, installed)

    private fun code(json: String) = parse(json).report.single().code

    private fun with(key: String, value: String) = example.replace(Regex("\"$key\": \"[^\"]*\""), "\"$key\": $value")

    @Test
    fun `the example library file reads as written`() {
        val l = assertNotNull(parse(example).library)
        assertEquals("zangbundel-voorbeeld", l.id)
        assertEquals("CC-BY-SA-4.0", l.license.id)
        assertEquals("bundel@example.org", l.maintainer.email)
        assertEquals(listOf("nl", "en"), l.languages)
        assertEquals("Campfire", l.tags.getValue("kampvuur").getValue("en"))
    }

    @Test
    fun `each library refusal has its code`() {
        assertEquals("L002", code("{ not json"))
        assertEquals("L002", code("[1, 2]"))
        assertEquals("L002", code(example.replace("\"description\"", "\"omschrijving\"")))
        assertEquals("L002", code(with("name", "42")))
        assertEquals("L002", code(example.replace("\"email\": \"bundel@example.org\",", "").replace("\"url\": \"https://example.org/zangbundel-voorbeeld\"", "\"site\": \"x\"")))
        assertEquals("L002", code(with("min_app_version", "\"1.0\"")))
        assertEquals("L002", code("[".repeat(10_000) + "]".repeat(10_000)))
        assertEquals("L002", LibraryFileParser.parse(ByteArray(64 * 1024 + 1) { ' '.code.toByte() }, app).report.single().code)
        assertEquals("L003", code(with("format", "\"iets-anders\"")))
        assertEquals("L004", code(with("format_version", "\"2.0\"")))
        assertEquals("L005", code(with("min_app_version", "\"1.2.0\"")))
        assertEquals("L006", code(with("id", "\"Mijn Bundel\"")))
        assertEquals("L006", parse(example, url = "https://b", installed = mapOf("zangbundel-voorbeeld" to "https://a")).report.single().code)
        assertNotNull(parse(example, url = "https://a", installed = mapOf("zangbundel-voorbeeld" to "https://a")).library, "same URL: an update, not a clash")
    }

    @Test
    fun `a newer minor format and unknown fields are fine`() {
        assertNotNull(parse(with("format_version", "\"1.7\"").replace("\"homepage\"", "\"x_extra\": [1, {\"a\": 2}], \"homepage\"")).library)
    }

    @Test
    fun `app versions compare numerically`() {
        assertTrue(LibraryFileParser.compareVersions("1.10.0", "1.9.9") > 0)
        assertEquals(0, LibraryFileParser.compareVersions("1.0.0", "1.0.0"))
        assertTrue(LibraryFileParser.compareVersions("0.9.12", "1.0.0") < 0)
    }

    // ---- import across files ----

    private fun vector(name: String) = javaClass.classLoader.getResourceAsStream("conformance/songs/$name.cho")!!.readBytes()

    private fun library(vararg songs: Pair<String, ByteArray>, extra: Map<String, ByteArray> = emptyMap()): Map<String, ByteArray> =
        mapOf("zangbundel.json" to example.toByteArray(), "LICENSE" to "licentietekst".toByteArray()) + songs.toMap() + extra

    @Test
    fun `the two example songs import cleanly as one library`() {
        val r = LibraryImport.check(library("songs/nl/vuur-in-de-nacht.cho" to vector("vuur-in-de-nacht"), "songs/en/fire-in-the-night.cho" to vector("fire-in-the-night")), app)
        assertFalse(r.refused)
        assertEquals(listOf("songs/en/fire-in-the-night.cho", "songs/nl/vuur-in-de-nacht.cho"), r.songs.map { it.path })
        assertEquals(listOf("I001"), r.report.map { it.code }, "only the English song's timing data")
        assertEquals(0, r.skipped)
    }

    @Test
    fun `the checks across files`() {
        // W011: the Dutch song alone has nobody in its variant group.
        val alone = LibraryImport.check(library("songs/nl/vuur-in-de-nacht.cho" to vector("vuur-in-de-nacht")), app)
        assertEquals(listOf("W011"), alone.report.map { it.code })
        // S005: the same id in two directories; both reported, the first in path order imported.
        val twice = vector("vader-jacob")
        val dup = LibraryImport.check(library("songs/a/vader-jacob.cho" to twice, "songs/b/vader-jacob.cho" to twice), app)
        assertEquals(2, dup.report.count { it.code == "S005" })
        assertEquals(listOf("songs/a/vader-jacob.cho"), dup.songs.map { it.path })
        // I003: other files under songs/ are counted, not listed.
        val notes = LibraryImport.check(library("songs/vader-jacob.cho" to twice, extra = mapOf("songs/notes.txt" to ByteArray(1), "songs/x.CHO" to twice)), app)
        assertEquals("2 files", notes.report.single { it.code == "I003" }.detail)
        // W012: a notation path that is not in the repository.
        val notation = "{title: N}\n{meta: id n}\n{meta: language nl}\n{meta: notation notation/n.musicxml}\nEen\n".toByteArray()
        assertTrue(LibraryImport.check(library("songs/n.cho" to notation), app).report.any { it.code == "W012" })
        assertTrue(LibraryImport.check(library("songs/n.cho" to notation, extra = mapOf("notation/n.musicxml" to ByteArray(1))), app).report.none { it.code == "W012" })
        // A song error skips only that song.
        val mixed = LibraryImport.check(library("songs/vader-jacob.cho" to twice, "songs/missing-language.cho" to vector("missing-language")), app)
        assertEquals(1, mixed.skipped)
        assertEquals(1, mixed.songs.size)
    }

    @Test
    fun `missing library file, licence file or songs refuse the import`() {
        assertEquals("L001", LibraryImport.check(mapOf("songs/a.cho" to ByteArray(1)), app).report.single().code)
        assertEquals("L007", LibraryImport.check(library("songs/vader-jacob.cho" to vector("vader-jacob")) - "LICENSE", app).report.single().code)
        assertTrue(LibraryImport.check(library(), app).refused)
        assertEquals("L008", LibraryImport.check(library(), app).report.single().code)
        val confirmed = LibraryImport.check(library(), app, allowEmpty = true)
        assertFalse(confirmed.refused)
        assertEquals(ReportLevel.WARNING, confirmed.report.single().level)
    }

    @Test
    fun `random library files never make it throw`() {
        val random = Random(20261025)
        val pieces = listOf("{", "}", "[", "]", "\"", ":", ",", "\\", "format", "zangbundel-library", "1.0", "id", "license", "null", "true", "1", " ")
        repeat(20_000) {
            val text = if (random.nextInt(4) == 0) example.substring(0, random.nextInt(example.length))
            else (0 until random.nextInt(0, 80)).joinToString("") { pieces[random.nextInt(pieces.size)] }
            try {
                val r = LibraryFileParser.parse(text.toByteArray(), app)
                if (r.library == null) assertTrue(r.report.isNotEmpty())
            } catch (e: Throwable) {
                fail("${e::class.simpleName} on $text: ${e.message}")
            }
        }
        assertNull(LibraryFileParser.parse(byteArrayOf(0xFF.toByte(), 0xFE.toByte()), app).library)
    }
}
