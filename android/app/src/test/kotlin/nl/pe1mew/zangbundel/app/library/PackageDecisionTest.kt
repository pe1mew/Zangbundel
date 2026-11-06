// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.library

import kotlinx.coroutines.test.runTest
import nl.pe1mew.zangbundel.core.songformat.SignatureVerifier
import nl.pe1mew.zangbundel.platform.storage.InstallPlan
import nl.pe1mew.zangbundel.platform.storage.InstalledSong
import nl.pe1mew.zangbundel.platform.storage.LibraryFiles
import nl.pe1mew.zangbundel.platform.storage.LibraryIndex
import nl.pe1mew.zangbundel.platform.storage.LibraryInstaller
import nl.pe1mew.zangbundel.platform.storage.LibraryMemory
import nl.pe1mew.zangbundel.platform.storage.LibraryRow
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The library manager's decision table (FDS §8.3) and its cross-format rule (APP-06; song-format 4.3;
 * assessment SP-39) as [LibraryRepository] asks them (guiDesign D-88), with the shared test libraries.
 */
class PackageDecisionTest {
    private fun pkg(name: String) = javaClass.classLoader.getResourceAsStream("conformance/packages/$name.zbl")!!.use { it.readBytes() }

    private val jdk = SignatureVerifier { publicKey, message, signature ->
        val spki = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00) + publicKey
        val key = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(spki))
        Signature.getInstance("Ed25519").run { initVerify(key); update(message); verify(signature) }
    }

    private class Index : LibraryIndex {
        val rows = HashMap<String, LibraryRow>()
        val memories = HashMap<String, LibraryMemory>()
        override suspend fun replace(plan: InstallPlan) { rows[plan.libraryId] = plan.library }
        override suspend fun installed(libraryId: String) = rows[libraryId]
        override suspend fun all() = rows.values.toList()
        override suspend fun remove(libraryId: String) { rows.remove(libraryId) }
        override suspend fun songs(libraryId: String) = emptyList<InstalledSong>()
        override suspend fun setEnabled(libraryId: String, enabled: Boolean) {}
        override suspend fun memory(libraryId: String) = memories[libraryId]
        override suspend fun remember(memory: LibraryMemory) { memories[memory.libraryId] = memory }
    }

    private fun repository(index: Index) = LibraryRepository(
        object : Downloads { override suspend fun fetch(url: String, maxBytes: Long, ifNoneMatch: String?) = error("no network") },
        LibraryInstaller(LibraryFiles(Files.createTempDirectory("libraries").toFile()), index), index, "1.0.0", jdk, clock = { 42 },
    )

    /** The package without manifest.json and manifest.sig: what is left is its 1.1 view, a snapshot with the package's id. */
    private fun viewOnly(zip: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            ZipInputStream(ByteArrayInputStream(zip)).use { input ->
                generateSequence { input.nextEntry }.filterNot { it.name.endsWith("/manifest.json") || it.name.endsWith("/manifest.sig") }.forEach { e ->
                    z.putNextEntry(ZipEntry(e.name))
                    z.write(input.readBytes())
                    z.closeEntry()
                }
            }
        }
        return out.toByteArray()
    }

    @Test
    fun `a new package installs without a question and is remembered with its key and version`() = runTest {
        val index = Index()
        val repo = repository(index)
        val checked = repo.checkFile(pkg("valid-signed"), "valid-signed.zbl")
        assertFalse(checked.refused, "${checked.report}")
        assertNull(checked.question)
        assertIs<LibraryInstaller.Outcome.Installed>(repo.install(checked))
        val row = assertNotNull(index.rows.values.single())
        assertTrue(row.isPackage)
        assertEquals("21FE-31DF-A154-A261", row.fingerprint)
        assertEquals(row.version, row.revision)
        val memory = assertNotNull(index.memories[row.id])
        assertNotNull(memory.publicKey)
        assertEquals(row.version?.toInt(), memory.version)
    }

    @Test
    fun `the same version again asks M-72, the unsigned one M-71 with the version sentence`() = runTest {
        val index = Index()
        val repo = repository(index)
        repo.install(repo.checkFile(pkg("valid-signed"), "a.zbl"))
        assertIs<LibraryRepository.Question.SameVersion>(repo.checkFile(pkg("valid-signed"), "a.zbl").question)
        val unsigned = assertIs<LibraryRepository.Question.NotSigned>(repo.checkFile(pkg("valid-unsigned"), "b.zbl").question)
        assertEquals("21FE-31DF-A154-A261", unsigned.currentFingerprint)
        assertNotNull(unsigned.newVersion, "not higher, so the version sentence is part of the question (FDS M-71)")
    }

    @Test
    fun `an unsigned copy of a signed package asks M-71, and on accept the memory says unsigned`() = runTest {
        val index = Index()
        val repo = repository(index)
        repo.install(repo.checkFile(pkg("valid-signed"), "a.zbl"))
        val id = index.rows.keys.single()
        val copy = repo.checkFile(viewOnly(pkg("valid-signed")), "copy.zip")
        assertFalse(copy.refused, "${copy.report}")
        assertEquals(id, copy.library?.id, "the view carries the package's id")
        val q = assertIs<LibraryRepository.Question.NotSigned>(copy.question)
        assertNull(q.newVersion, "a zangbundel.json library has no comparable version (APP-06)")
        assertTrue(copy.report.any { it.code == "W013" })
        repo.install(copy)
        assertNull(index.memories.getValue(id).publicKey, "APP-06: the memory records unsigned")
        assertFalse(index.rows.getValue(id).isPackage)
        // Now unsigned in memory: the next copy asks M-72.
        assertIs<LibraryRepository.Question.SameVersion>(repo.checkFile(viewOnly(pkg("valid-signed")), "copy.zip").question)
    }

    @Test
    fun `a different library with the same title asks M-73, and Vervangen removes the other`() = runTest {
        val index = Index()
        val repo = repository(index)
        val first = repo.checkFile(pkg("valid-signed"), "a.zbl")
        repo.install(first)
        val row = index.rows.values.single()
        index.rows.clear()
        index.memories.clear()
        index.rows["other-library"] = row.copy(id = "other-library")
        val again = repo.checkFile(pkg("valid-signed"), "a.zbl")
        val q = assertIs<LibraryRepository.Question.SameTitle>(again.question)
        assertEquals("other-library", q.installedId)
        repo.install(again, replace = q.installedId)
        assertEquals(setOf(row.id), index.rows.keys)
    }

    @Test
    fun `a refused package asks nothing`() = runTest {
        val checked = repository(Index()).checkFile(pkg("signature-invalid"), "bad.zbl")
        assertTrue(checked.refused)
        assertNull(checked.question)
        assertEquals(listOf("SIG-03"), checked.report.map { it.code })
    }
}
