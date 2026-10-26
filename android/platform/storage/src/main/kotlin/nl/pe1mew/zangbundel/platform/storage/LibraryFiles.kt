// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.platform.storage

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.SecureRandom

/**
 * The canonical file store of installed libraries (architecture section 6):
 * `<root>/<library id>/<directory>/` per installed revision, written first to
 * `<root>/<library id>/.staging-<random>/` and renamed when complete (FR-LIB-15). Every name is one
 * the app chose ([InstallPlan]); names are checked again here, so nothing from an archive becomes a
 * path. [StartupSweep] removes staging directories a crash left behind.
 */
class LibraryFiles(private val root: File, private val random: SecureRandom = SecureRandom()) {

    /** Writes every file of [plan] into a new staging directory, flushed to disk, and returns it. */
    fun stage(plan: InstallPlan): File {
        val dir = File(libraryDir(plan.libraryId), STAGING_PREFIX + ByteArray(8).also(random::nextBytes).joinToString("") { "%02x".format(it) })
        if (!dir.mkdirs()) throw IOException("cannot create $dir")
        try {
            for ((name, bytes) in plan.files) {
                require(FILE_NAME.matches(name)) { "not a name the app chose: $name" }
                val f = File(dir, name)
                f.parentFile?.mkdirs()
                FileOutputStream(f).use { out -> out.write(bytes); out.fd.sync() }
            }
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
        return dir
    }

    /** Renames a complete staging directory to the revision's [directory]; the previous one stays untouched. */
    fun commit(libraryId: String, staging: File, directory: String): File {
        val target = File(libraryDir(libraryId), checkDirectory(directory))
        if (target.exists()) throw IOException("$target exists")
        if (!staging.renameTo(target)) throw IOException("cannot rename $staging to $target")
        return target
    }

    /** Deletes every installed directory of [libraryId] except [keep]; staging directories are the sweep's. */
    fun prune(libraryId: String, keep: String) {
        libraryDir(libraryId).listFiles()?.filter { it.isDirectory && it.name != keep && !it.name.startsWith(STAGING_PREFIX) }?.forEach { it.deleteRecursively() }
    }

    /** Removes everything of a library (the user removes it, or "remove all data"). */
    fun remove(libraryId: String) {
        libraryDir(libraryId).deleteRecursively()
    }

    fun read(libraryId: String, directory: String, fileName: String): ByteArray {
        require(FILE_NAME.matches(fileName)) { "not a name the app chose: $fileName" }
        return File(File(libraryDir(libraryId), checkDirectory(directory)), fileName).readBytes()
    }

    fun directories(libraryId: String): List<String> = libraryDir(libraryId).list()?.sorted().orEmpty()

    private fun libraryDir(id: String): File {
        require(ID.matches(id) && id.length <= 64) { "not a library id: $id" }
        return File(root, id)
    }

    private fun checkDirectory(d: String): String {
        require(DIRECTORY.matches(d)) { "not an install directory: $d" }
        return d
    }

    companion object {
        const val STAGING_PREFIX = ".staging-"
        private val ID = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")
        /** A revision (40 or 64 hex, or `snapshot-` and 16 hex) and the install's random suffix. */
        private val DIRECTORY = Regex("^(snapshot-[0-9a-f]{16}|[0-9a-f]{40}|[0-9a-f]{64})-[0-9a-f]{8}$")
        /** `library.json`, `songs/<id>.cho` and the like: at most one directory, id characters only. */
        private val FILE_NAME = Regex("^([a-z]+/)?[a-z0-9]+(-[a-z0-9]+)*\\.[a-z]{3,4}$")
    }
}
