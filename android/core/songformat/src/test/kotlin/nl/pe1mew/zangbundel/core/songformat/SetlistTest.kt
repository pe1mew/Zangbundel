// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.songformat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** Setlists (song-format 13) against the setlist vectors, the export round trip, and setlists in a library import. */
class SetlistTest {
    private fun resource(name: String): ByteArray =
        assertNotNull(javaClass.classLoader.getResourceAsStream("conformance/$name"), "missing $name").use { it.readBytes() }

    /** The library state of the vectors: library id → song metadata. */
    private val state: Map<String, List<SongMeta>> by lazy {
        (Json.parseToJsonElement(String(resource("setlists/library-state.json"), Charsets.UTF_8)) as JsonObject).mapValues { (_, songs) ->
            songs.jsonArray.map { s ->
                val o = s.jsonObject
                SongMeta(id = o.getValue("id").jsonPrimitive.content, title = o.getValue("title").jsonPrimitive.content, language = "nl",
                    sortTitle = o["sorttitle"]?.jsonPrimitive?.content)
            }
        }
    }

    private fun describe(r: SetlistResolver.Resolution): String = when (r) {
        is SetlistResolver.Resolution.Resolved -> "resolved ${r.library}/${r.song.id}"
        is SetlistResolver.Resolution.Unresolved ->
            "unresolved ${r.reason}" + if (r.reason == SetlistResolver.Reason.NO_SINGLE_TITLE_MATCH) " ${r.candidates.size}" else ""
    }

    @Test
    fun `every setlist vector gives the expected codes and resolution`() {
        val names = String(resource("setlists/index.txt"), Charsets.UTF_8).lines().filter { it.isNotBlank() }
        assertTrue(names.size >= 12, "vectors: $names")
        for (name in names) {
            val bytes = resource("setlists/$name.zbs")
            val expected = Json.parseToJsonElement(String(resource("setlists/$name.expected.json"), Charsets.UTF_8)) as JsonObject
            assertEquals(expected.getValue("sha256").jsonPrimitive.content, MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, "$name: vector bytes changed")
            val library = expected.getValue("library").takeIf { it != JsonNull }?.jsonPrimitive?.content
            val r = SetlistParser.parse(bytes, "setlists/$name.zbs", "$name.zbs", library, library?.let { lib -> state[lib]?.map { it.id }?.toSet() })
            assertEquals(expected.getValue("codes").jsonArray.map { it.jsonPrimitive.content }, r.report.map { it.code }.distinct().sorted(), "$name: ${r.report}")
            assertEquals(expected.getValue("accepted").jsonPrimitive.boolean, r.setlist != null, "$name: ${r.report}")
            expected["resolution"]?.let { res ->
                val got = SetlistResolver.resolve(assertNotNull(r.setlist), state).map(::describe)
                assertEquals(res.jsonArray.map { it.jsonPrimitive.content }, got, name)
            }
        }
    }

    @Test
    fun `the example keeps its entries in order with line numbers, comments dropped`() {
        val s = assertNotNull(SetlistParser.parse(resource("setlists/zomerkamp.zbs")).setlist)
        assertEquals("Zomerkamp", s.title)
        assertEquals("Eerste avond, rond het grote vuur", s.description)
        assertEquals(5, s.entries.size)
        assertEquals(Setlist.Entry.Song("zangbundel-bibliotheek", "advocaatje", 7), s.entries[0])
        assertEquals(Setlist.Entry.Title("Vader Jacob", 11), s.entries[4])
    }

    @Test
    fun `an export reads back to the same meaning, titles as comments, unresolved titles kept`() {
        val s = assertNotNull(SetlistParser.parse(resource("setlists/zomerkamp.zbs")).setlist)
        val text = SetlistWriter.write(s, SetlistResolver.resolve(s, state))
        assertTrue("zangbundel-voorbeeld/vuur-in-de-nacht  # Vuur in de nacht\n" in text, text)
        assertTrue("? Vader Jacob\n" in text, "an ambiguous title stays a title entry: $text")
        assertTrue(!Canonical.canonicalise(text).changed, "the export is canonical")
        val again = assertNotNull(SetlistParser.parse(text.toByteArray(), fileName = "zomerkamp.zbs").setlist)
        assertEquals(s.entries.map { (it as? Setlist.Entry.Song)?.let { e -> e.library + "/" + e.song } ?: (it as Setlist.Entry.Title).title },
            again.entries.map { (it as? Setlist.Entry.Song)?.let { e -> e.library + "/" + e.song } ?: (it as Setlist.Entry.Title).title })
    }

    @Test
    fun `titles fold case, accents and spaces`() {
        assertEquals("kanon en vuur", SetlistResolver.fold("  Kânon   en VUUR "))
    }

    @Test
    fun `a library import reads its setlists, skipping one with an error and a duplicate id`() {
        val json = """{"format":"zangbundel-library","format_version":"1.1","id":"zangbundel-voorbeeld","name":"V","description":"D",
            "maintainer":{"name":"M","url":"https://example.org"},"license":{"id":"CC0-1.0","file":"LICENSE","summary":"S"},"min_app_version":"1.0.0"}"""
        val song = resource("songs/vader-jacob.cho")
        val good = "{title: Avond}\n{meta: id avond}\nzangbundel-voorbeeld/vader-jacob\nzangbundel-voorbeeld/onbekend\n".toByteArray()
        val files = mapOf(
            "zangbundel.json" to json.toByteArray(), "LICENSE" to "l".toByteArray(), "songs/vader-jacob.cho" to song,
            "setlists/avond.zbs" to good, "setlists/kopie/avond.zbs" to good, "setlists/kapot.zbs" to "{meta: id kapot}\n".toByteArray(),
        )
        val r = LibraryImport.check(files, "1.0.0")
        assertEquals(listOf("setlists/avond.zbs"), r.setlists.map { it.path })
        assertTrue(r.report.any { it.code == "SL101" && it.path == "setlists/avond.zbs" })
        assertTrue(r.report.any { it.code == "SL003" && it.path == "setlists/kopie/avond.zbs" })
        assertTrue(r.report.any { it.code == "SL002" && it.path == "setlists/kapot.zbs" })
    }

    @Test
    fun `random setlist files never make it throw`() {
        val random = Random(20261025)
        val pieces = listOf("{", "}", "title", ": ", "meta", " id x", "\n", "? ", "a/b", "#", " ", "/", "é", "\r")
        repeat(20_000) {
            val text = (0 until random.nextInt(0, 50)).joinToString("") { pieces[random.nextInt(pieces.size)] }
            try {
                val r = SetlistParser.parse(text.toByteArray())
                r.setlist?.let { SetlistWriter.write(it, SetlistResolver.resolve(it, state)) }
            } catch (e: Throwable) {
                fail("${e::class.simpleName} on ${text.replace("\n", "\\n")}: ${e.message}")
            }
        }
        assertNull(SetlistParser.parse(ByteArray(64 * 1024 + 1)).setlist)
    }
}
