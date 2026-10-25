// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.songformat

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** Rules of song-format sections 5 to 7, 9 and 11 that the conformance vectors do not reach. */
class SongParserTest {
    private val head = "{title: Test}\n{meta: id test}\n{meta: language nl}\n{lyricist: Zangbundel-project}\n" +
        "{copyright: 2026 Zangbundel-project}\n{meta: license CC0-1.0}\n{meta: source Geschreven voor de test}\n{meta: tag test}\n"

    private fun parse(body: String, fileName: String? = null) = SongParser.parse((head + body).toByteArray(), "songs/test.cho", fileName)
    private fun codes(body: String, fileName: String? = null) = parse(body, fileName).report.map { it.code }
    private fun song(body: String) = assertNotNull(parse(body).song, parse(body).report.toString())

    @Test
    fun `the Dutch example has the sections the spec describes`() {
        val bytes = javaClass.classLoader.getResourceAsStream("conformance/songs/vuur-in-de-nacht.cho")!!.readBytes()
        val s = assertNotNull(SongParser.parse(bytes).song)
        val sections = s.body.filterIsInstance<Part.Section>()
        assertEquals(listOf(Kind.VERSE, Kind.CHORUS, Kind.VERSE, Kind.BRIDGE, Kind.CHORUS), sections.map { it.kind })
        assertEquals(listOf(1, null, 2, null, null), sections.map { it.verseNumber })
        assertTrue(sections.all { it.label == null }, "no labels written: the UI generates them")
        assertEquals(listOf(false, false, false, false, true), sections.map { it.repeat })
        assertEquals(listOf(15, 16, 17, 18), sections.last().items.filterIsInstance<Item.Line>().map { it.number })
        with(s.meta) {
            assertEquals("vuur-in-de-nacht", id); assertEquals("Vuur in de nacht", title); assertEquals("nl", language)
            assertEquals(listOf("Zangbundel-project"), lyricists); assertEquals(listOf("Percy Montrose"), composers)
            assertEquals("Oh My Darling, Clementine", melody); assertEquals(listOf("kampvuur", "avond"), tags)
            assertEquals("vuur-in-de-nacht", variantGroup); assertTrue(redistribute)
        }
    }

    @Test
    fun `implicit verses are runs of lines between blank lines, numbered with verse environments`() {
        val s = song("Een\nTwee\n\nDrie\n{start_of_verse}\nVier\n{end_of_verse}\nVijf\n")
        val sections = s.body.filterIsInstance<Part.Section>()
        assertEquals(listOf(1, 2, 3, 4), sections.map { it.verseNumber })
        assertEquals(listOf("Een", "Twee", "Drie", "Vier", "Vijf"), s.lines)
    }

    @Test
    fun `chords, annotations and escapes leave the display text`() {
        assertEquals("De zon is weg,", SongParser.displayText("De [G]zon is [*zacht]weg,"))
        assertEquals("[haakjes] en {accolades} en \\", SongParser.displayText("\\[haakjes\\] en \\{accolades\\} en \\\\"))
        assertEquals("een \\n blijft", SongParser.displayText("een \\n blijft"))
        assertEquals("open [ blijft", SongParser.displayText("open [ blijft"))
        assertEquals("tab  weg", SongParser.displayText("tab\t weg\t"))
        val s = song("[G] [D]\nDe [G]regel\n")
        assertEquals(listOf("De regel"), s.lines, "a chord-only line is not a canonical line")
    }

    @Test
    fun `chorus repeats take the labelled chorus or the latest, shown with the given label`() {
        val s = song("{soc: A}\nEerste\n{eoc}\n{soc: B}\nTweede\n{eoc}\n{chorus: A}\n{chorus}\n{chorus: Refrein (2x)}\n")
        val repeats = s.body.filterIsInstance<Part.Section>().filter { it.repeat }
        assertEquals(listOf("A", "B", "Refrein (2x)"), repeats.map { it.label })   // {chorus} takes the latest, with its label
        assertEquals(listOf("Eerste", "Tweede", "Eerste", "Tweede", "Tweede"), s.lines)
    }

    @Test
    fun `remarks are shown in place but are not lines`() {
        val s = song("{c: gesproken}\n{start_of_verse}\nEen\n{ci: langzamer}\nTwee\n{end_of_verse}\n")
        assertIs<Part.Remark>(s.body.first())
        val verse = s.body.filterIsInstance<Part.Section>().single()
        assertEquals(Item.Remark("langzamer", RemarkStyle.ITALIC), verse.items[1])
        assertEquals(listOf("Een", "Twee"), s.lines)
    }

    @Test
    fun `unknown environments are skipped and reported once, P3 ones silently`() {
        val r = parse("{start_of_foo}\nweg\n{end_of_foo}\n{start_of_foo}\nweg\n{end_of_foo}\n{start_of_tab}\ntab\n{end_of_tab}\nBlijft\n")
        assertEquals(listOf("Blijft"), assertNotNull(r.song).lines)
        assertEquals(1, r.report.count { it.code == "I002" })
    }

    @Test
    fun `unknown directives are info once per name, upper case is a warning`() {
        val c = codes("{x_iets: a}\n{x_iets: b}\n{Title_Extra: c}\n{START_OF_VERSE}\nEen\n{end_of_verse}\n")
        assertEquals(2, c.count { it == "I001" })
        assertTrue("W006" in c)
    }

    @Test
    fun `the single-file song errors`() {
        assertTrue("S004" in codes("Een\n", fileName = "ander.cho"))
        assertTrue("S009" in codes("{new_song}\nEen\n"))
        assertTrue("S010" in codes("{c: alleen een opmerking}\n"))
        assertTrue("S012" in codes("{meta: redistribute misschien}\nEen\n"))
        assertTrue("S012" in codes("{year: 26}\nEen\n"))
        assertTrue("S012" in codes("{meta: tag Kampvuur}\nEen\n"))
        assertTrue("S007" in codes("{start_of_verse}\n{start_of_chorus}\nEen\n{end_of_chorus}\n{end_of_verse}\n"))
        assertTrue("S007" in codes("Een\n{end_of_verse}\n"))
        assertTrue("S011" in codes("{title: a {b} c}\nEen\n"))
        assertNull(parse("{start_of_verse}\nEen\n").song)
    }

    @Test
    fun `the warnings`() {
        assertTrue("W005" in codes("x".repeat(121) + "\n"))
        assertTrue("W007" in codes("Een\tTwee\n"))
        val w010 = SongParser.parse((head.replace("CC0-1.0", "MIT license") + "Een\n").toByteArray()).report.map { it.code }
        assertTrue("W010" in w010)
        val title = SongParser.parse("{title: ${"t".repeat(61)}}\n{meta: id t}\n{meta: language nl}\nEen\n".toByteArray()).report.map { it.code }
        assertTrue("W003" in title)
        val long = SongParser.parse(("{title: T}\n{meta: id t}\n{meta: language nl}\n" + (1..255).joinToString("") { "Regel $it\n" }).toByteArray()).report.map { it.code }
        assertTrue("W004" in long)
        assertTrue("W008" in codes((1..200).joinToString("") { "Een tamelijk lange regel tekst nummer $it\n" }))
    }

    @Test
    fun `timing data is checked for shape and reported once as unknown`() {
        val ok = codes("Een\nTwee\n{x_zb_time: 1 2.0}\n{x_zb_time: 2 5.5 7}\n")
        assertEquals(listOf("I001"), ok)
        assertTrue("W009" in codes("Een\n{x_zb_time: 2 1.0}\n"))                // beyond the line list
        assertTrue("W009" in codes("Een\nTwee\n{x_zb_time: 2 3.0}\n{x_zb_time: 1 4.0}\n"))
        assertTrue("W009" in codes("Een\n{x_zb_time: 1 x}\n"))
    }

    @Test
    fun `language tags are checked for syntax and normalised`() {
        assertEquals("nl-BE", SongParser.normaliseLanguage("nl-be"))
        assertEquals("sr-Latn-RS", SongParser.normaliseLanguage("SR-latn-rs"))
        assertEquals("es-419", SongParser.normaliseLanguage("es-419"))
        val r = SongParser.parse("{title: T}\n{meta: id t}\n{meta: language n_l}\nEen\n".toByteArray())
        assertTrue("S003" in r.report.map { it.code })
    }

    @Test
    fun `a song over 64 KiB is refused before decoding`() {
        val r = SongParser.parse(ByteArray(64 * 1024 + 1) { 'a'.code.toByte() })
        assertEquals(listOf("S002"), r.report.map { it.code })
    }

    @Test
    fun `random bytes never make it throw`() {
        val random = Random(20261025)
        val pieces = listOf("{", "}", "[", "]", "\\", "{start_of_chorus}", "{eoc}", "{chorus}", "{meta: ", "#", "\n", "\r", "\t", "é", "x", " ", ":")
        repeat(20_000) {
            val text = (0 until random.nextInt(0, 60)).joinToString("") { pieces[random.nextInt(pieces.size)] }
            val bytes = if (random.nextInt(10) == 0) random.nextBytes(random.nextInt(0, 200)) else (head + text).toByteArray()
            try {
                val r = SongParser.parse(bytes)
                r.song?.let { s -> assertEquals(s.lines.size, s.body.sumOf { p -> (p as? Part.Section)?.items?.count { it is Item.Line } ?: 0 }) }
            } catch (e: Throwable) {
                fail("${e::class.simpleName} on ${text.replace("\n", "\\n")}: ${e.message}")
            }
        }
    }

    @Test
    fun `a file without problems reports nothing`() {
        val r = parse("{start_of_verse}\nEen\n{end_of_verse}\n")
        assertTrue(r.report.isEmpty(), r.report.toString())
        assertFalse(assertNotNull(r.song).canonical.isEmpty())
    }

    @Test
    fun `the licence summary for BLE is built in the order of 6_2 and capped at 200 characters`() {
        val m = assertNotNull(SongParser.parse(javaClass.classLoader.getResourceAsStream("conformance/songs/vuur-in-de-nacht.cho")!!.readBytes()).song).meta
        assertEquals(
            "© 2026 Zangbundel-project · CC0-1.0 · Zangbundel-project · Percy Montrose · Geschreven als voorbeeld bij de formaatspecificatie",
            m.licenceSummary("Voorbeeldbundel"),
        )
        val bare = SongMeta(id = "a", title = "A", language = "nl", copyright = "© Iemand", source = "x".repeat(300))
        assertEquals("© Iemand · licentie van bundel Bundel · " + "x".repeat(200 - 40), bare.licenceSummary("Bundel"))
    }
}
