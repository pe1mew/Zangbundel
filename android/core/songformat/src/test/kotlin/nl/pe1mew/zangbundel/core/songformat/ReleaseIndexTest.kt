// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.songformat

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

/** The release index and the URLs a leader pastes (song-format 12.6, guiDesign D-50). */
class ReleaseIndexTest {
    private val sha = "a".repeat(64)
    private val rev = "9f2c1e7d4b0a6c3e8f5d2a1b7c4e9f0d3a6b8c1e"

    private fun index(vararg releases: String) =
        """{"format":"zangbundel-releases","library":"voorbeeld","releases":[${releases.joinToString(",")}]}""".toByteArray()

    private fun release(version: String, created: String, url: String = "https://example.org/$version.zbl", s: String = sha, r: String = rev, size: Int = 1000) =
        """{"version":"$version","revision":"$r","created":"$created","url":"$url","size":$size,"sha256":"$s"}"""

    @Test
    fun `the newest release is chosen by created, never by version`() {
        val i = assertNotNull(ReleaseIndex.parse(index(release("9.0.0", "2026-01-01T00:00:00Z"), release("10.0.0", "2026-10-18T14:05:00Z"), release("2026.12.0", "2025-12-01T00:00:00Z"))))
        assertEquals("10.0.0", i.newest?.version)
        assertEquals(3, i.releases.size)
    }

    @Test
    fun `releases with a bad field are left out, a non-index is refused`() {
        val i = assertNotNull(ReleaseIndex.parse(index(
            release("a", "2026-01-01T00:00:00Z", url = "http://example.org/a.zbl"),
            release("b", "2026-01-01T00:00:00Z", s = "xyz"),
            release("c", "gisteren"),
            release("d", "2026-01-01T00:00:00Z", r = "abc"),
            release("e", "2026-01-01T00:00:00Z", size = 0),
        )))
        assertEquals(emptyList(), i.releases)
        assertNull(i.newest)
        assertNull(ReleaseIndex.parse("""{"format":"zangbundel-library"}""".toByteArray()))
        assertNull(ReleaseIndex.parse("niet eens json".toByteArray()))
        assertNull(ReleaseIndex.parse(ByteArray(ReleaseIndex.MAX_BYTES + 1) { ' '.code.toByte() }))
    }

    @Test
    fun `urls a leader pastes`() {
        assertEquals(LibraryUrl.Archive("https://example.org/b-2026.10.0.zbl"), LibraryUrl.of(" https://example.org/b-2026.10.0.zbl "))
        assertEquals(LibraryUrl.Archive("https://example.org/snapshot.ZIP"), LibraryUrl.of("https://example.org/snapshot.ZIP"))
        assertEquals(LibraryUrl.Index("https://example.org/releases.json"), LibraryUrl.of("https://example.org/releases.json"))
        assertEquals(LibraryUrl.Index("https://raw.githubusercontent.com/pe1mew/Zangbundel-bibliotheek/HEAD/releases.json"), LibraryUrl.of("https://github.com/pe1mew/Zangbundel-bibliotheek"))
        assertEquals(LibraryUrl.Index("https://raw.githubusercontent.com/pe1mew/Zangbundel-bibliotheek/HEAD/releases.json"), LibraryUrl.of("https://github.com/pe1mew/Zangbundel-bibliotheek.git/"))
        assertEquals(LibraryUrl.Index("https://gitlab.com/groep/bundel/-/raw/HEAD/releases.json"), LibraryUrl.of("https://gitlab.com/groep/bundel"))
        assertIs<LibraryUrl.Unsupported>(LibraryUrl.of("http://example.org/a.zbl"))
        assertIs<LibraryUrl.Unsupported>(LibraryUrl.of("https://codeberg.org/groep/bundel"))
        assertIs<LibraryUrl.Unsupported>(LibraryUrl.of("https://github.com/pe1mew"))
    }

    @Test
    fun `random indexes never make it throw`() {
        val random = Random(20261026)
        val good = index(release("1", "2026-01-01T00:00:00Z")).toString(Charsets.UTF_8)
        repeat(10_000) {
            val text = good.substring(0, random.nextInt(good.length)) + (if (random.nextBoolean()) "\"" else "}")
            try { ReleaseIndex.parse(text.toByteArray())?.newest } catch (e: Throwable) { fail("${e::class.simpleName}: $text") }
        }
    }
}
