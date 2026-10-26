// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.library

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.pe1mew.zangbundel.core.songformat.ArchiveImport
import nl.pe1mew.zangbundel.core.songformat.Library
import nl.pe1mew.zangbundel.core.songformat.Limits
import nl.pe1mew.zangbundel.core.songformat.ReleaseIndex
import nl.pe1mew.zangbundel.core.songformat.LibraryUrl
import nl.pe1mew.zangbundel.core.songformat.ReportEntry
import nl.pe1mew.zangbundel.core.songformat.ReportLevel
import nl.pe1mew.zangbundel.platform.storage.LibraryIndex
import nl.pe1mew.zangbundel.platform.storage.LibraryInstaller

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
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** An archive that was opened and checked, waiting for the licence to be shown and the install confirmed. */
    class Checked(val result: ArchiveImport.Result, val source: String) {
        val library: Library? get() = result.library?.library
        /** The licence file's text, shown in full before the import completes (FR-LIB-21). */
        val licenseText: String? get() = library?.let { l -> result.files[l.license.file]?.toString(Charsets.UTF_8) }
        val refused: Boolean get() = result.refused
        val report: List<ReportEntry> get() = result.report
    }

    sealed interface Found {
        /** A release to download: [size] for the confirmation on mobile data (FR-NET-04), null when the URL named the archive itself. */
        data class Release(val url: String, val size: Long?, val sha256: String?, val version: String?, val indexUrl: String?) : Found
        data class Failed(val reason: Reason, val detail: String? = null) : Found
    }

    enum class Reason { UNSUPPORTED_URL, NETWORK, NOT_AN_INDEX, NO_RELEASE, TOO_LARGE }

    suspend fun checkFile(bytes: ByteArray, fileName: String): Checked = withContext(Dispatchers.Default) {
        Checked(withClash(ArchiveImport.open(bytes, appVersion)), fileName)
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
        return withContext(Dispatchers.Default) { Checked(withClash(ArchiveImport.open(fetched.bytes, appVersion)), release.url) }
    }

    /** Installs an archive the leader confirmed after reading its licence (song-format 12.7 steps 6 and 7). */
    suspend fun install(checked: Checked): LibraryInstaller.Outcome = installer.install(checked.result, checked.source, clock())

    suspend fun remove(libraryId: String) = installer.remove(libraryId)

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
}
