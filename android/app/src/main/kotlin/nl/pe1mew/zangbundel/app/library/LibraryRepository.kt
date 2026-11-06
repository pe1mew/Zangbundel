// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.library

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.pe1mew.zangbundel.core.songformat.ArchiveImport
import nl.pe1mew.zangbundel.core.songformat.Library
import nl.pe1mew.zangbundel.core.songformat.LibraryOpen
import nl.pe1mew.zangbundel.core.songformat.SignatureVerifier
import nl.pe1mew.zangbundel.core.songformat.Limits
import nl.pe1mew.zangbundel.core.songformat.ReleaseIndex
import nl.pe1mew.zangbundel.core.songformat.LibraryUrl
import nl.pe1mew.zangbundel.core.songformat.ReportEntry
import nl.pe1mew.zangbundel.core.songformat.ReportLevel
import nl.pe1mew.zangbundel.platform.storage.LibraryIndex
import nl.pe1mew.zangbundel.platform.storage.LibraryInstaller
import nl.pe1mew.zangbundel.platform.storage.LibraryRow

/** The network as the library side needs it: one HTTPS GET with a size cap (architecture A-14). */
interface Downloads {
    class Fetched(val code: Int, val etag: String?, val bytes: ByteArray)

    /** Throws when the connection fails or the body exceeds [maxBytes]. */
    suspend fun fetch(url: String, maxBytes: Long, ifNoneMatch: String? = null): Fetched
}

/**
 * Adding and updating libraries (guiDesign D-50, song-format 12.6 and 12.7, FR-LIB-10 to FR-LIB-17).
 * Two routes, both in steps the screen walks the leader through:
 *
 * - **From a file:** [checkFile], then the licence (FR-LIB-21), then [install].
 * - **Online:** [find] the release a URL points at, with its size (FR-NET-04); on the leader's
 *   confirmation [download] it, which verifies the index's SHA-256 (A013) and checks the archive; then
 *   the licence, then [install].
 *
 * Nothing here starts on its own (FR-LIB-12, FR-NET-02): every call is a user's action. The gathering
 * engine never calls this class (FR-NET-01).
 */
class LibraryRepository(
    private val downloads: Downloads,
    private val installer: LibraryInstaller,
    private val index: LibraryIndex,
    private val appVersion: String,
    /** Ed25519 for package signatures (architecture A-26): libsodium's, from `platform/crypto`. */
    private val verifier: SignatureVerifier,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /**
     * An archive or package that was opened and checked, waiting for the leader: first [question] when the
     * library manager's decision table asks one (guiDesign D-88), then the licence, then the install.
     */
    class Checked(val result: ArchiveImport.Result, val source: String, val question: Question? = null) {
        val library: Library? get() = result.library?.library
        /** The licence file's text, shown in full before the import completes (FR-LIB-21). */
        val licenseText: String? get() = library?.let { l -> result.files[l.license.file]?.toString(Charsets.UTF_8) }
        val refused: Boolean get() = result.refused
        val report: List<ReportEntry> get() = result.report
    }

    /**
     * The one question an import may ask (FDS §8.3, APP-05, APP-06; guiDesign D-88). [newVersion] and
     * [currentVersion] give the version sentence of M-70 and M-71, null when the new version is higher or
     * not comparable.
     */
    sealed interface Question {
        /** M-70: signed by another key than the version on this device. */
        data class OtherSigner(val currentName: String, val currentFingerprint: String, val newName: String, val newFingerprint: String, val newVersion: Int?, val currentVersion: Int?) : Question
        /** M-71: the version on this device was signed, this one is not (also an unsigned copy of a package, APP-06). */
        data class NotSigned(val currentName: String, val currentFingerprint: String, val newVersion: Int?, val currentVersion: Int?) : Question
        /** M-72: the same version again. */
        data class SameVersion(val current: Int) : Question
        /** M-73: another library with this title is installed; [installedId] is replaced on *Vervangen*. */
        data class SameTitle(val title: String, val installedId: String) : Question
        /** M-74: an older version than the one on this device. */
        data class Older(val new: Int, val current: Int) : Question
    }

    sealed interface Found {
        /** A release to download: [size] for the confirmation on mobile data (FR-NET-04), null when the URL named the archive itself. */
        data class Release(val url: String, val size: Long?, val sha256: String?, val version: String?, val indexUrl: String?) : Found
        data class Failed(val reason: Reason, val detail: String? = null) : Found
    }

    enum class Reason { UNSUPPORTED_URL, NETWORK, NOT_AN_INDEX, NO_RELEASE, TOO_LARGE }

    suspend fun checkFile(bytes: ByteArray, fileName: String): Checked = checked(withContext(Dispatchers.Default) { withClash(LibraryOpen.open(bytes, appVersion, verifier)) }, fileName)

    /** Adds the decision table's question to a checked library (FDS §8.3, APP-06). */
    private suspend fun checked(result: ArchiveImport.Result, source: String): Checked =
        Checked(result, source, if (result.refused) null else question(result))

    /**
     * The library manager's decision table (FDS §8.3) and its cross-format rule (APP-06, song-format 4.3,
     * assessment SP-39); the first row that matches decides, and at most one question is asked (APP-05).
     */
    private suspend fun question(result: ArchiveImport.Result): Question? {
        val id = result.library?.library?.id ?: return null
        val memory = index.memory(id)
        val pkg = result.pkg
        if (pkg == null) {
            // APP-06: a zangbundel.json library with a package's id is an unsigned copy of it.
            memory ?: return null
            return if (memory.publicKey != null) Question.NotSigned(memory.signer.orEmpty(), memory.fingerprint.orEmpty(), null, null)
            else Question.SameVersion(memory.version)
        }
        if (memory == null) {
            if (index.installed(id) != null) return null   // the package's own 1.1 view, imported earlier: the same library (12.11)
            val clash = index.all().firstOrNull { it.id != id && it.name.equals(pkg.title, ignoreCase = true) } ?: return null
            return Question.SameTitle(clash.name, clash.id)
        }
        val newKey = pkg.author?.publicKey?.let { java.util.Base64.getEncoder().encodeToString(it) }
        val higher = pkg.version > memory.version
        fun note(): Pair<Int?, Int?> = if (higher) null to null else pkg.version to memory.version
        if (memory.publicKey != null && newKey != memory.publicKey) {
            val (n, c) = note()
            return if (newKey == null) Question.NotSigned(memory.signer.orEmpty(), memory.fingerprint.orEmpty(), n, c)
            else Question.OtherSigner(memory.signer.orEmpty(), memory.fingerprint.orEmpty(), pkg.author!!.name, pkg.author!!.fingerprint, n, c)
        }
        return when {
            higher -> null
            pkg.version == memory.version -> Question.SameVersion(memory.version)
            else -> Question.Older(pkg.version, memory.version)
        }
    }

    suspend fun find(input: String): Found = when (val u = LibraryUrl.of(input)) {
        is LibraryUrl.Unsupported -> Found.Failed(Reason.UNSUPPORTED_URL, u.reason)
        is LibraryUrl.Archive -> Found.Release(u.url, null, null, null, null)
        is LibraryUrl.Index -> {
            val fetched = try { downloads.fetch(u.url, ReleaseIndex.MAX_BYTES.toLong()) } catch (e: Exception) { null }
            when {
                fetched == null || fetched.code != 200 -> Found.Failed(Reason.NETWORK, fetched?.code?.toString())
                else -> {
                    val index = ReleaseIndex.parse(fetched.bytes)
                    val newest = index?.newest
                    when {
                        index == null -> Found.Failed(Reason.NOT_AN_INDEX)
                        newest == null -> Found.Failed(Reason.NO_RELEASE)
                        newest.size > Limits.ARCHIVE_MAX_BYTES -> Found.Failed(Reason.TOO_LARGE, "${newest.size} bytes")
                        else -> Found.Release(newest.url, newest.size, newest.sha256, newest.version, u.url)
                    }
                }
            }
        }
    }

    /** Downloads the release the leader confirmed, verifies it against the index (A013) and checks it. */
    suspend fun download(release: Found.Release): Checked {
        val fetched = try { downloads.fetch(release.url, Limits.ARCHIVE_MAX_BYTES.toLong()) } catch (e: Exception) {
            return Checked(failure("A001", "download failed: ${e.message}"), release.url)
        }
        if (fetched.code != 200) return Checked(failure("A001", "HTTP ${fetched.code}"), release.url)
        release.sha256?.let { sha -> ArchiveImport.verifyDownload(fetched.bytes, sha)?.let { return Checked(failure(it), release.url) } }
        return checked(withContext(Dispatchers.Default) { withClash(LibraryOpen.open(fetched.bytes, appVersion, verifier)) }, release.url)
    }

    /**
     * Installs a library the leader confirmed after reading its licence (song-format 12.7 steps 6 and 7).
     * [replace] is the library M-73's *Vervangen* removes first (FDS §8.3); null to add or update.
     */
    suspend fun install(checked: Checked, replace: String? = null): LibraryInstaller.Outcome {
        if (replace != null && replace != checked.library?.id) installer.remove(replace)
        return installer.install(checked.result, checked.source, clock())
    }

    suspend fun remove(libraryId: String) = installer.remove(libraryId)

    suspend fun setEnabled(libraryId: String, enabled: Boolean) = installer.setEnabled(libraryId, enabled)

    /**
     * *Bijwerken* (FR-LIB-11): where to look for a newer release of an installed library, in order its
     * `release_index`, the repository its manifest names, or the URL it came from. Null for a library
     * added from a file without either: the leader picks the new file.
     */
    fun updateSource(library: LibraryRow): String? =
        library.releaseIndex ?: library.repository ?: library.source?.takeIf { it.startsWith("https://") }

    /** The outcome of one automatic check (FR-LIB-13, song-format 12.7): it looks, and never downloads an archive. */
    sealed interface UpdateCheck {
        /** A newer release is in the index; [version] when the index names one. */
        data class Available(val version: String?, val etag: String?) : UpdateCheck
        data class Current(val etag: String?) : UpdateCheck
        /** HTTP 304: the index has not changed since [etag] was kept, so neither has the answer. */
        data object Unchanged : UpdateCheck
        /** A library without a release index (an archive address, a file) cannot be checked without downloading it. */
        data object NotCheckable : UpdateCheck
        data object Failed : UpdateCheck
    }

    /**
     * The opt-in check on Wi-Fi (FR-LIB-13, architecture A-14, assessment SP-29): the release index of
     * [library] with a conditional request on [etag], its newest release compared with the installed
     * archive by SHA-256, as *Bijwerken* compares them.
     */
    suspend fun checkUpdate(library: LibraryRow, etag: String?): UpdateCheck {
        val source = updateSource(library) ?: return UpdateCheck.NotCheckable
        val url = (LibraryUrl.of(source) as? LibraryUrl.Index)?.url ?: return UpdateCheck.NotCheckable
        val fetched = try { downloads.fetch(url, ReleaseIndex.MAX_BYTES.toLong(), etag) } catch (e: Exception) { return UpdateCheck.Failed }
        if (fetched.code == HTTP_NOT_MODIFIED) return UpdateCheck.Unchanged
        if (fetched.code != HTTP_OK) return UpdateCheck.Failed
        val newest = ReleaseIndex.parse(fetched.bytes)?.newest ?: return UpdateCheck.Failed
        return if (newest.sha256.equals(library.archiveSha256, ignoreCase = true)) UpdateCheck.Current(fetched.etag)
        else UpdateCheck.Available(newest.version, fetched.etag)
    }

    /**
     * 4.3, L006: a library id already installed from another repository is refused, and the message
     * names the owner. A snapshot carries no repository and cannot clash; the same repository is an update.
     */
    private suspend fun withClash(result: ArchiveImport.Result): ArchiveImport.Result {
        if (result.refused) return result
        val id = result.library?.library?.id ?: return result
        val repository = result.manifest?.sourceRepository ?: return result
        val owner = index.all().firstOrNull { it.id == id }?.repository ?: return result
        if (owner == repository) return result
        return ArchiveImport.Result(null, result.manifest, null, result.archiveSha256, emptyMap(),
            result.report + ReportEntry(ReportLevel.LIBRARY_ERROR, "L006", "zangbundel.json", detail = "$id belongs to $owner"))
    }

    private fun failure(code: String, detail: String) = failure(ReportEntry(ReportLevel.LIBRARY_ERROR, code, "", detail = detail))
    private fun failure(entry: ReportEntry) = ArchiveImport.Result(null, null, null, "", emptyMap(), listOf(entry))

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_NOT_MODIFIED = 304
    }
}
