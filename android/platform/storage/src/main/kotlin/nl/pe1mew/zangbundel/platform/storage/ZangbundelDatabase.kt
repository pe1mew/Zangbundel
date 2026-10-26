// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.platform.storage

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction

/** One installed library revision (architecture section 6; [LibraryRow]). */
@Entity(tableName = "library")
data class LibraryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val revision: String,
    val directory: String,
    val version: String?,
    val created: String?,
    val source: String?,
    val repository: String?,
    val ref: String?,
    val archiveSha256: String,
    val formatVersion: String,
    val licenseId: String,
    val licenseSummary: String,
    val installedAt: Long,
    val colour: Int,
    val songCount: Int,
    /** Added in version 2; libraries installed before are on. */
    @ColumnInfo(defaultValue = "1") val enabled: Boolean,
    val releaseIndex: String?,
)

/** A song of an installed library ([SongRow]); `hash` is how the engine finds a local copy (FR-BLE-12). */
@Entity(tableName = "song", primaryKeys = ["libraryId", "songId"], indices = [androidx.room.Index("hash")])
data class SongEntity(
    val libraryId: String,
    val songId: String,
    val fileName: String,
    val sourcePath: String,
    val title: String,
    val sortTitle: String?,
    val language: String,
    val hash: String,
    /** Tags joined by a line feed; tags have id syntax and carry none. */
    val tags: String,
    val variantGroup: String?,
    val redistribute: Boolean,
    val licenseSummary: String,
)

/**
 * Lyric and title search [FR-LIB-31]: FTS4 with the unicode61 tokenizer and diacritics removed
 * (architecture section 6). `remove_diacritics=1` is what SQLite has from API 26 on.
 */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61, tokenizerArgs = ["remove_diacritics=1"], notIndexed = ["libraryId", "songId"])
@Entity(tableName = "song_fts")
data class SongFtsEntity(val libraryId: String, val songId: String, val title: String, val text: String)

@Entity(tableName = "setlist", primaryKeys = ["libraryId", "setlistId"])
data class SetlistEntity(val libraryId: String, val setlistId: String, val fileName: String, val title: String, val description: String?, val entries: Int)

/** The import report, kept until the next import (song-format 11.2). */
@Entity(tableName = "report")
data class ReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val libraryId: String,
    val level: String,
    val code: String,
    val path: String,
    val line: Int?,
    val detail: String?,
)

/** A row of [LibraryDao.songbook]. */
data class SongbookRow(
    val libraryId: String,
    val songId: String,
    val title: String,
    val sortTitle: String?,
    val language: String,
    val tags: String,
    val fileName: String,
    val hash: String,
    val libraryName: String,
    val colour: Int,
    val directory: String,
)

data class SongKey(val libraryId: String, val songId: String)

/** A setlist shipped in an installed library (guiDesign D-59): read-only, opened from its file with [Songbook.read]. */
data class LibrarySetlist(
    val libraryId: String,
    val setlistId: String,
    val fileName: String,
    val title: String,
    val entries: Int,
    val libraryName: String,
    val colour: Int,
    val directory: String,
)

@Dao
abstract class LibraryDao {
    @Query("SELECT * FROM library ORDER BY name")
    abstract suspend fun all(): List<LibraryEntity>

    @Query("SELECT * FROM library WHERE id = :id")
    abstract suspend fun library(id: String): LibraryEntity?

    @Query("SELECT * FROM song WHERE libraryId = :libraryId ORDER BY COALESCE(sortTitle, title)")
    abstract suspend fun songs(libraryId: String): List<SongEntity>

    /** A local copy of a song pushed by a leader, by its content hash in hex (ble-protocol 10.3). */
    @Query("SELECT * FROM song WHERE hash = :hash LIMIT 1")
    abstract suspend fun songByHash(hash: String): SongEntity?

    /** [match] is an FTS4 MATCH expression, built by the caller from what the user typed; disabled libraries are left out (D-50). */
    @Query("SELECT song.* FROM song JOIN song_fts ON song.libraryId = song_fts.libraryId AND song.songId = song_fts.songId JOIN library ON library.id = song.libraryId WHERE library.enabled = 1 AND song_fts MATCH :match")
    abstract suspend fun search(match: String): List<SongEntity>

    @Query("SELECT * FROM setlist WHERE libraryId = :libraryId ORDER BY title")
    abstract suspend fun setlists(libraryId: String): List<SetlistEntity>

    /** The combined songbook [FR-LIB-30]: every song of an enabled library, with what its row shows (D-52). */
    @Query(
        "SELECT song.libraryId, song.songId, song.title, song.sortTitle, song.language, song.tags, song.fileName, song.hash, " +
            "library.name AS libraryName, library.colour AS colour, library.directory AS directory " +
            "FROM song JOIN library ON library.id = song.libraryId WHERE library.enabled = 1",
    )
    abstract suspend fun songbook(): List<SongbookRow>

    /** Setlists shipped in enabled libraries (guiDesign D-59), with what their row shows. */
    @Query(
        "SELECT setlist.libraryId, setlist.setlistId, setlist.fileName, setlist.title, setlist.entries, " +
            "library.name AS libraryName, library.colour AS colour, library.directory AS directory " +
            "FROM setlist JOIN library ON library.id = setlist.libraryId WHERE library.enabled = 1 ORDER BY library.name, setlist.title",
    )
    abstract suspend fun librarySetlists(): List<LibrarySetlist>

    /** Lyric and title search [FR-LIB-31]; [match] comes from [FtsQuery]. */
    @Query("SELECT song_fts.libraryId, song_fts.songId FROM song_fts JOIN library ON library.id = song_fts.libraryId WHERE library.enabled = 1 AND song_fts MATCH :match")
    abstract suspend fun searchKeys(match: String): List<SongKey>

    @Query("UPDATE library SET enabled = :enabled WHERE id = :id")
    abstract suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("SELECT * FROM report WHERE libraryId = :libraryId ORDER BY id")
    abstract suspend fun report(libraryId: String): List<ReportEntity>

    @Insert protected abstract suspend fun insertLibrary(library: LibraryEntity)
    @Insert protected abstract suspend fun insertSongs(songs: List<SongEntity>)
    @Insert protected abstract suspend fun insertFts(fts: List<SongFtsEntity>)
    @Insert protected abstract suspend fun insertSetlists(setlists: List<SetlistEntity>)
    @Insert protected abstract suspend fun insertReport(report: List<ReportEntity>)

    @Query("DELETE FROM library WHERE id = :id") protected abstract suspend fun deleteLibrary(id: String)
    @Query("DELETE FROM song WHERE libraryId = :id") protected abstract suspend fun deleteSongs(id: String)
    @Query("DELETE FROM song_fts WHERE libraryId = :id") protected abstract suspend fun deleteFts(id: String)
    @Query("DELETE FROM setlist WHERE libraryId = :id") protected abstract suspend fun deleteSetlists(id: String)
    @Query("DELETE FROM report WHERE libraryId = :id") protected abstract suspend fun deleteReport(id: String)

    /** Everything of one library, replaced in one transaction (FR-LIB-15). */
    @Transaction
    open suspend fun replace(library: LibraryEntity, songs: List<SongEntity>, fts: List<SongFtsEntity>, setlists: List<SetlistEntity>, report: List<ReportEntity>) {
        remove(library.id)
        insertLibrary(library)
        insertSongs(songs)
        insertFts(fts)
        insertSetlists(setlists)
        insertReport(report)
    }

    @Transaction
    open suspend fun remove(id: String) {
        deleteLibrary(id)
        deleteSongs(id)
        deleteFts(id)
        deleteSetlists(id)
        deleteReport(id)
    }
}

@Database(
    entities = [
        LibraryEntity::class, SongEntity::class, SongFtsEntity::class, SetlistEntity::class, ReportEntity::class,
        PreparedSetlistEntity::class, PreparedEntryEntity::class, MemoryEntity::class, MemorySongEntity::class,
    ],
    version = 4,
    exportSchema = true,
    // 2: library.enabled and library.releaseIndex. 3: prepared setlists (guiDesign D-54). 4: gathering memories (D-49). Every later version adds its migration here.
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
abstract class ZangbundelDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
    abstract fun preparedSetlistDao(): PreparedSetlistDao
    abstract fun memoryDao(): MemoryDao

    companion object {
        const val NAME = "zangbundel.db"

        /** Opens the one database of the app; Room stays inside this module (architecture section 4). */
        fun open(context: Context): ZangbundelDatabase =
            Room.databaseBuilder(context.applicationContext, ZangbundelDatabase::class.java, NAME).build()
    }
}

/** [LibraryIndex] on the app's Room database. */
class RoomLibraryIndex(private val dao: LibraryDao) : LibraryIndex {
    override suspend fun replace(plan: InstallPlan) {
        val l = plan.library
        dao.replace(
            LibraryEntity(l.id, l.name, l.description, l.revision, l.directory, l.version, l.created, l.source, l.repository, l.ref, l.archiveSha256, l.formatVersion, l.licenseId, l.licenseSummary, l.installedAt, l.colour, l.songCount, l.enabled, l.releaseIndex),
            plan.songs.map { SongEntity(it.libraryId, it.songId, it.fileName, it.sourcePath, it.title, it.sortTitle, it.language, it.hash, it.tags.joinToString("\n"), it.variantGroup, it.redistribute, it.licenseSummary) },
            plan.songs.map { SongFtsEntity(it.libraryId, it.songId, it.title, it.searchText) },
            plan.setlists.map { SetlistEntity(it.libraryId, it.setlistId, it.fileName, it.title, it.description, it.entries) },
            plan.report.map { ReportEntity(libraryId = l.id, level = it.level, code = it.code, path = it.path, line = it.line, detail = it.detail) },
        )
    }

    override suspend fun installed(libraryId: String): LibraryRow? = dao.library(libraryId)?.let(::row)

    override suspend fun all(): List<LibraryRow> = dao.all().map(::row)

    private fun row(it: LibraryEntity) =
        LibraryRow(it.id, it.name, it.description, it.revision, it.directory, it.version, it.created, it.source, it.repository, it.ref, it.archiveSha256, it.formatVersion, it.licenseId, it.licenseSummary, it.installedAt, it.colour, it.songCount, it.enabled, it.releaseIndex)

    override suspend fun remove(libraryId: String) = dao.remove(libraryId)

    override suspend fun songs(libraryId: String): List<InstalledSong> = dao.songs(libraryId).map { InstalledSong(it.songId, it.title, it.hash) }

    override suspend fun setEnabled(libraryId: String, enabled: Boolean) = dao.setEnabled(libraryId, enabled)
}
