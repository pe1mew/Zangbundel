// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.library

import kotlinx.coroutines.test.runTest
import nl.pe1mew.zangbundel.platform.storage.InstallPlan
import nl.pe1mew.zangbundel.platform.storage.InstalledSong
import nl.pe1mew.zangbundel.platform.storage.LibraryFiles
import nl.pe1mew.zangbundel.platform.storage.LibraryIndex
import nl.pe1mew.zangbundel.platform.storage.LibraryInstaller
import nl.pe1mew.zangbundel.platform.storage.LibraryRow
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The two routes of guiDesign D-50 against a fake network serving the archive vectors. */
class LibraryRepositoryTest {
    private fun archive(name: String) = javaClass.classLoader.getResourceAsStream("conformance/archives/$name.zbl")!!.use { it.readBytes() }
    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private class FakeDownloads(val served: Map<String, ByteArray>) : Downloads {
        val asked = ArrayList<String>()
        override suspend fun fetch(url: String, maxBytes: Long, ifNoneMatch: String?): Downloads.Fetched {
            asked += url
            val b = served[url] ?: return Downloads.Fetched(404, null, ByteArray(0))
            if (b.size > maxBytes) throw IOException("too large")
            return Downloads.Fetched(200, "\"e1\"", b)
        }
    }

    private class FakeIndex : LibraryIndex {
        val rows = HashMap<String, LibraryRow>()
        val songs = HashMap<String, List<InstalledSong>>()
        override suspend fun replace(plan: InstallPlan) {
            rows[plan.libraryId] = plan.library
            songs[plan.libraryId] = plan.songs.map { InstalledSong(it.songId, it.title, it.hash) }
        }
        override suspend fun installed(libraryId: String) = rows[libraryId]
        override suspend fun all() = rows.values.toList()
        override suspend fun remove(libraryId: String) { rows.remove(libraryId) }
        override suspend fun songs(libraryId: String) = songs[libraryId].orEmpty()
        override suspend fun setEnabled(libraryId: String, enabled: Boolean) { rows[libraryId] = rows.getValue(libraryId).copy(enabled = enabled) }
    }

    private fun repository(served: Map<String, ByteArray>, index: FakeIndex = FakeIndex()): Pair<LibraryRepository, FakeDownloads> {
        val downloads = FakeDownloads(served)
        val installer = LibraryInstaller(LibraryFiles(Files.createTempDirectory("libraries").toFile()), index)
        return LibraryRepository(downloads, installer, index, "1.0.0", clock = { 42 }) to downloads
    }

    private fun indexJson(url: String, archive: ByteArray, sha256: String = sha(archive)) =
        """{"format":"zangbundel-releases","library":"zangbundel-voorbeeld","releases":[
           {"version":"2026.10.0","revision":"9f2c1e7d4b0a6c3e8f5d2a1b7c4e9f0d3a6b8c1e","created":"2026-10-18T14:05:00Z","url":"$url","size":${archive.size},"sha256":"$sha256"}]}""".toByteArray()

    @Test
    fun `online from a repository url, then index, size, download, licence and install`() = runTest {
        val zbl = archive("release")
        val raw = "https://raw.githubusercontent.com/example/zangbundel-voorbeeld/HEAD/releases.json"
        val url = "https://example.org/zangbundel-voorbeeld-2026.10.0.zbl"
        val (repo, net) = repository(mapOf(raw to indexJson(url, zbl), url to zbl))
        val found = assertIs<LibraryRepository.Found.Release>(repo.find("https://github.com/example/zangbundel-voorbeeld"))
        assertEquals(zbl.size.toLong(), found.size, "the size is known before the download (FR-NET-04)")
        assertEquals(listOf(raw), net.asked, "nothing but the index is fetched before the leader confirms")
        val checked = repo.download(found)
        assertFalse(checked.refused, checked.report.toString())
        assertEquals("CC0 1.0\n", checked.licenseText)
        val installed = assertIs<LibraryInstaller.Outcome.Installed>(repo.install(checked))
        assertEquals("https://example.org/zangbundel-voorbeeld", installed.library.repository)
        assertEquals(url, installed.library.source)
    }

    @Test
    fun `a download that does not match the index is refused with A013`() = runTest {
        val zbl = archive("release")
        val url = "https://example.org/a.zbl"
        val (repo, _) = repository(mapOf("https://example.org/releases.json" to indexJson(url, zbl, "0".repeat(64)), url to zbl))
        val found = assertIs<LibraryRepository.Found.Release>(repo.find("https://example.org/releases.json"))
        val checked = repo.download(found)
        assertTrue(checked.refused)
        assertEquals("A013", checked.report.single().code)
    }

    @Test
    fun `failures on the way are named`() = runTest {
        val (repo, _) = repository(mapOf("https://example.org/leeg.json" to """{"format":"zangbundel-releases","library":"x","releases":[]}""".toByteArray(),
            "https://example.org/rommel.json" to "geen index".toByteArray()))
        assertEquals(LibraryRepository.Reason.UNSUPPORTED_URL, assertIs<LibraryRepository.Found.Failed>(repo.find("http://example.org/a.zbl")).reason)
        assertEquals(LibraryRepository.Reason.NETWORK, assertIs<LibraryRepository.Found.Failed>(repo.find("https://example.org/weg.json")).reason)
        assertEquals(LibraryRepository.Reason.NO_RELEASE, assertIs<LibraryRepository.Found.Failed>(repo.find("https://example.org/leeg.json")).reason)
        assertEquals(LibraryRepository.Reason.NOT_AN_INDEX, assertIs<LibraryRepository.Found.Failed>(repo.find("https://example.org/rommel.json")).reason)
    }

    @Test
    fun `from a file, and a library id owned by another repository is refused`() = runTest {
        val index = FakeIndex()
        val (repo, _) = repository(emptyMap(), index)
        val checked = repo.checkFile(archive("release"), "zangbundel-voorbeeld-2026.10.0.zbl")
        assertIs<LibraryInstaller.Outcome.Installed>(repo.install(checked))
        // The same id from another repository: L006, the owner named.
        index.rows["zangbundel-voorbeeld"] = index.rows.getValue("zangbundel-voorbeeld").copy(repository = "https://example.org/andere")
        val clash = repo.checkFile(archive("release"), "kopie.zbl")
        assertTrue(clash.refused)
        assertEquals("L006", clash.report.last().code)
        // A snapshot has no repository, so it cannot clash.
        assertFalse(repo.checkFile(archive("snapshot-root"), "snapshot.zip").refused)
    }

    @Test
    fun `an update looks at the release index, then the repository, then an https source`() = runTest {
        val index = FakeIndex()
        val (repo, _) = repository(emptyMap(), index)
        repo.install(repo.checkFile(archive("release"), "a.zbl"))
        val row = index.rows.getValue("zangbundel-voorbeeld")
        assertEquals("https://example.org/zangbundel-voorbeeld", repo.updateSource(row), "the manifest's repository")
        assertEquals("https://example.org/r.json", repo.updateSource(row.copy(releaseIndex = "https://example.org/r.json")))
        assertEquals("https://example.org/a.zbl", repo.updateSource(row.copy(repository = null, source = "https://example.org/a.zbl")))
        assertEquals(null, repo.updateSource(row.copy(repository = null, source = "a.zbl")), "from a file: the leader picks the new file")
        repo.setEnabled(row.id, false)
        assertFalse(index.rows.getValue(row.id).enabled)
    }
}
