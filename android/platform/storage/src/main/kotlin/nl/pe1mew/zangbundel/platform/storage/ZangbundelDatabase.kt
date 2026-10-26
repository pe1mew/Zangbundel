// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.platform.storage

import android.content.Context
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

    /** [match] is an FTS4 MATCH expression, built by the caller from what the user typed. */
    @Query("SELECT song.* FROM song JOIN song_fts ON song.libraryId = song_fts.libraryId AND song.songId = song_fts.songId WHERE song_fts MATCH :match")
    abstract suspend fun search(match: String): List<SongEntity>

    @Query("SELECT * FROM setlist WHERE libraryId = :libraryId ORDER BY title")
    abstract suspend fun setlists(libraryId: String): List<SetlistEntity>

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
    entities = [LibraryEntity::class, SongEntity::class, SongFtsEntity::class, SetlistEntity::class, ReportEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ZangbundelDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

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
            LibraryEntity(l.id, l.name, l.description, l.revision, l.directory, l.version, l.created, l.source, l.repository, l.ref, l.archiveSha256, l.formatVersion, l.licenseId, l.licenseSummary, l.installedAt, l.colour, l.songCount),
            plan.songs.map { SongEntity(it.libraryId, it.songId, it.fileName, it.sourcePath, it.title, it.sortTitle, it.language, it.hash, it.tags.joinToString("\n"), it.variantGroup, it.redistribute, it.licenseSummary) },
            plan.songs.map { SongFtsEntity(it.libraryId, it.songId, it.title, it.searchText) },
            plan.setlists.map { SetlistEntity(it.libraryId, it.setlistId, it.fileName, it.title, it.description, it.entries) },
            plan.report.map { ReportEntity(libraryId = l.id, level = it.level, code = it.code, path = it.path, line = it.line, detail = it.detail) },
        )
    }

    override suspend fun installed(libraryId: String): LibraryRow? = dao.library(libraryId)?.let(::row)

    override suspend fun all(): List<LibraryRow> = dao.all().map(::row)

    private fun row(it: LibraryEntity) =
        LibraryRow(it.id, it.name, it.description, it.revision, it.directory, it.version, it.created, it.source, it.repository, it.ref, it.archiveSha256, it.formatVersion, it.licenseId, it.licenseSummary, it.installedAt, it.colour, it.songCount)

    override suspend fun remove(libraryId: String) = dao.remove(libraryId)
}
