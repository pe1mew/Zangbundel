// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.platform.storage

import kotlinx.coroutines.test.runTest
import nl.pe1mew.zangbundel.core.songformat.ArchiveImport
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The install pipeline of architecture section 6 on the JVM: plan, file store, and the order of the swap. */
class LibraryInstallerTest {
    private val app = "1.0.0"
    private fun archive(name: String) = javaClass.classLoader.getResourceAsStream("conformance/archives/$name.zbl")!!.use { it.readBytes() }
    private fun opened(name: String) = ArchiveImport.open(archive(name), app)

    private class FakeIndex : LibraryIndex {
        val rows = HashMap<String, InstallPlan>()
        var failNext = false
        override suspend fun replace(plan: InstallPlan) {
            if (failNext) { failNext = false; throw IOException("disk full") }
            rows[plan.libraryId] = plan
        }
        override suspend fun installed(libraryId: String) = rows[libraryId]?.library
        override suspend fun all() = rows.values.map { it.library }
        override suspend fun remove(libraryId: String) { rows.remove(libraryId) }
    }

    private fun tempRoot(): File = Files.createTempDirectory("libraries").toFile().also { it.deleteOnExit() }

    @Test
    fun `a release archive plans its files under app names and its index rows`() {
        val plan = InstallPlan.from(opened("release"), "https://example.org/a.zbl", 1000)
        assertEquals(
            setOf("library.json", "license.txt", "manifest.json", "songs/fire-in-the-night.cho", "songs/vuur-in-de-nacht.cho"),
            plan.files.keys,
        )
        assertEquals("9f2c1e7d4b0a6c3e8f5d2a1b7c4e9f0d3a6b8c1e", plan.library.revision)
        assertTrue(plan.library.directory.startsWith(plan.library.revision + "-"))
        assertEquals("2026.10.0", plan.library.version)
        assertEquals("https://example.org/zangbundel-voorbeeld", plan.library.repository)
        assertEquals("v2026.10.0", plan.library.ref)
        val nl = plan.songs.single { it.songId == "vuur-in-de-nacht" }
        assertEquals("songs/nl/vuur-in-de-nacht.cho", nl.sourcePath)
        assertEquals(32, nl.hash.length)
        assertEquals(18, nl.searchText.lines().size)
        assertEquals(listOf("kampvuur", "avond"), nl.tags)
        assertTrue(nl.licenseSummary.startsWith("© 2026 Zangbundel-project"))
    }

    @Test
    fun `a snapshot has no manifest file and a snapshot revision`() {
        val plan = InstallPlan.from(opened("snapshot-root"), "snapshot.zip", 1000)
        assertFalse("manifest.json" in plan.files)
        assertTrue(plan.library.revision.startsWith("snapshot-"))
        assertNull(plan.library.version)
    }

    @Test
    fun `install writes the files, swaps the index, and an update removes the previous directory`() = runTest {
        val root = tempRoot()
        val files = LibraryFiles(root)
        val index = FakeIndex()
        val installer = LibraryInstaller(files, index)
        val first = assertIs<LibraryInstaller.Outcome.Installed>(installer.install(opened("release"), "a", 1))
        assertNull(first.previous)
        assertEquals(2, first.songs)
        val dir1 = first.library.directory
        assertEquals(listOf(dir1), files.directories("zangbundel-voorbeeld"))
        assertContentEquals(
            javaClass.classLoader.getResourceAsStream("conformance/songs/vuur-in-de-nacht.cho")!!.readBytes(),
            files.read("zangbundel-voorbeeld", dir1, "songs/vuur-in-de-nacht.cho"),
        )
        // The same revision again gets a new directory; the old one goes only after the swap.
        val second = assertIs<LibraryInstaller.Outcome.Installed>(installer.install(opened("release"), "a", 2))
        assertEquals(dir1, second.previous?.directory)
        assertEquals(listOf(second.library.directory), files.directories("zangbundel-voorbeeld"))
    }

    @Test
    fun `when the index swap fails the previous revision stays and the new files go`() = runTest {
        val root = tempRoot()
        val files = LibraryFiles(root)
        val index = FakeIndex()
        val installer = LibraryInstaller(files, index)
        val first = assertIs<LibraryInstaller.Outcome.Installed>(installer.install(opened("release"), "a", 1))
        index.failNext = true
        assertFailsWith<IOException> { installer.install(opened("snapshot-root"), "b", 2) }
        assertEquals(listOf(first.library.directory), files.directories("zangbundel-voorbeeld"))
        assertEquals(first.library.directory, index.installed("zangbundel-voorbeeld")?.directory)
    }

    @Test
    fun `a refused archive writes nothing`() = runTest {
        val root = tempRoot()
        val installer = LibraryInstaller(LibraryFiles(root), FakeIndex())
        assertIs<LibraryInstaller.Outcome.Refused>(installer.install(opened("traversal"), "x", 1))
        assertTrue(root.listFiles().isNullOrEmpty())
    }

    @Test
    fun `removing a library clears its index and files`() = runTest {
        val root = tempRoot()
        val files = LibraryFiles(root)
        val index = FakeIndex()
        val installer = LibraryInstaller(files, index)
        installer.install(opened("release"), "a", 1)
        installer.remove("zangbundel-voorbeeld")
        assertNull(index.installed("zangbundel-voorbeeld"))
        assertTrue(files.directories("zangbundel-voorbeeld").isEmpty())
    }

    @Test
    fun `the file store accepts only names the app chose`() {
        val files = LibraryFiles(tempRoot())
        assertFailsWith<IllegalArgumentException> { files.read("../etc", "x", "library.json") }
        assertFailsWith<IllegalArgumentException> { files.read("zangbundel-voorbeeld", "../x", "library.json") }
        assertFailsWith<IllegalArgumentException> { files.read("zangbundel-voorbeeld", "${"a".repeat(40)}-00000000", "../library.json") }
        assertNotNull(files)
    }
}
