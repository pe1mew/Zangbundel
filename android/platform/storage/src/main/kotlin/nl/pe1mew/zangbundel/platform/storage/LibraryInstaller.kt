// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.platform.storage

import nl.pe1mew.zangbundel.core.songformat.ArchiveImport

/** The library index the installer swaps in one transaction; [RoomLibraryIndex] on the phone, a fake in tests. */
interface LibraryIndex {
    /** Replaces everything of the plan's library in one transaction (FR-LIB-15). */
    suspend fun replace(plan: InstallPlan)
    suspend fun installed(libraryId: String): LibraryRow?
    suspend fun all(): List<LibraryRow>
    suspend fun remove(libraryId: String)
}

/**
 * Installs a checked archive (song-format 12.7 steps 6 and 7, FR-LIB-15): write the files to staging,
 * rename staging to a new directory, swap the index in one transaction, then delete the previous
 * directory. Until the swap the previous revision stays in use; if any step fails, the new files are
 * removed and the previous revision is untouched.
 */
class LibraryInstaller(private val files: LibraryFiles, private val index: LibraryIndex) {

    sealed interface Outcome {
        /** [previous] is the revision that was installed before, for the "what changed" view [FR-LIB-11]. */
        data class Installed(val library: LibraryRow, val previous: LibraryRow?, val songs: Int, val setlists: Int) : Outcome
        data class Refused(val result: ArchiveImport.Result) : Outcome
    }

    suspend fun install(result: ArchiveImport.Result, source: String?, now: Long): Outcome {
        if (result.refused) return Outcome.Refused(result)
        val id = result.library!!.library!!.id
        val previous = index.installed(id)
        val colour = previous?.colour ?: ((index.all().maxOfOrNull { it.colour } ?: -1) + 1)
        val plan = InstallPlan.from(result, source, now, colour)
        val staging = files.stage(plan)
        val dir = try {
            files.commit(plan.libraryId, staging, plan.library.directory)
        } catch (e: Exception) {
            staging.deleteRecursively()
            throw e
        }
        try {
            index.replace(plan)
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
        files.prune(plan.libraryId, keep = plan.library.directory)
        return Outcome.Installed(plan.library, previous, plan.songs.size, plan.setlists.size)
    }

    /** Removes a library: index first, so nothing points at files that are gone. */
    suspend fun remove(libraryId: String) {
        index.remove(libraryId)
        files.remove(libraryId)
    }
}
