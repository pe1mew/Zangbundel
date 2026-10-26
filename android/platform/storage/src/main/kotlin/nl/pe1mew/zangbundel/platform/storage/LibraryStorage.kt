// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.platform.storage

import android.content.Context
import java.io.File

/**
 * The library side of storage as the app sees it: the index and the file store, with Room kept inside
 * this module (architecture section 4). Opened once by the app's container.
 */
class LibraryStorage private constructor(val index: LibraryIndex, val files: LibraryFiles, val songbook: Songbook, val setlists: Setlists, val memories: Memories, val settings: AppSettings) {
    companion object {
        fun open(context: Context): LibraryStorage {
            val database = ZangbundelDatabase.open(context)
            val dao = database.libraryDao()
            val files = LibraryFiles(File(context.filesDir, "libraries"))
            return LibraryStorage(RoomLibraryIndex(dao), files, RoomSongbook(dao, files), RoomSetlists(database.preparedSetlistDao()), RoomMemories(database.memoryDao()), AppSettings.open(context))
        }
    }
}
