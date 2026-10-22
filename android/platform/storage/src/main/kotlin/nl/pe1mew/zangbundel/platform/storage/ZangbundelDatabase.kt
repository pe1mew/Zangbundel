// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.platform.storage

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

/**
 * One installed library revision (architecture section 6). Song metadata, the FTS4 search table,
 * setlists, gathering memories, usage and statistics tables follow with the import pipeline.
 */
@Entity(tableName = "library")
data class LibraryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val revision: String,
    val version: String,
    val createdAt: String,
    val sourceUrl: String?,
    val archiveSha256: String?,
)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM library ORDER BY name")
    suspend fun all(): List<LibraryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(library: LibraryEntity)

    @Query("DELETE FROM library WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(entities = [LibraryEntity::class], version = 1, exportSchema = true)
abstract class ZangbundelDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    companion object {
        const val NAME = "zangbundel.db"

        /** Opens the one database of the app; Room stays inside this module (architecture section 4). */
        fun open(context: Context): ZangbundelDatabase =
            Room.databaseBuilder(context.applicationContext, ZangbundelDatabase::class.java, NAME).build()
    }
}
