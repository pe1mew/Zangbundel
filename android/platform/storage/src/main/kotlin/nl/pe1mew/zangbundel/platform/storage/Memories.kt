// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.platform.storage

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction

/**
 * A gathering memory (FR-DATA-03, guiDesign D-49): the gathering's name, when it ran, on which side
 * this phone was, and on the leader's side how many singers took part. No names of people and no
 * identifiers of other devices; kept until the user deletes it (FR-DATA-04).
 */
data class GatheringMemory(
    val uid: Long,
    val name: String,
    /** Wall-clock start and end, epoch milliseconds. */
    val startedAt: Long,
    val endedAt: Long,
    val led: Boolean,
    /** Leader side only: the most singers counted at once. */
    val singers: Int?,
    val songs: Int,
)

/** A song of a memory, in the order it was sung, with its identifiers so it can be found once its library is installed. */
data class MemorySong(val libraryId: String, val songId: String, val title: String) {
    val songKey: String get() = "$libraryId/$songId"
}

/** Gathering memories; [RoomMemories] on the phone. */
interface Memories {
    suspend fun all(): List<GatheringMemory>
    suspend fun songs(uid: Long): List<MemorySong>
    suspend fun add(name: String, startedAt: Long, endedAt: Long, led: Boolean, singers: Int?, songs: List<MemorySong>): Long
    suspend fun delete(uid: Long)
    suspend fun deleteAll()
}

@Entity(tableName = "memory")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val uid: Long = 0,
    val name: String,
    val startedAt: Long,
    val endedAt: Long,
    val led: Boolean,
    val singers: Int?,
)

@Entity(tableName = "memory_song", primaryKeys = ["memoryUid", "position"], indices = [Index("memoryUid")])
data class MemorySongEntity(val memoryUid: Long, val position: Int, val libraryId: String, val songId: String, val title: String)

/** A row of [MemoryDao.all]. */
data class MemoryRow(val uid: Long, val name: String, val startedAt: Long, val endedAt: Long, val led: Boolean, val singers: Int?, val songs: Int)

@Dao
abstract class MemoryDao {
    @Query(
        "SELECT m.uid, m.name, m.startedAt, m.endedAt, m.led, m.singers, (SELECT COUNT(*) FROM memory_song s WHERE s.memoryUid = m.uid) AS songs " +
            "FROM memory m ORDER BY m.startedAt DESC",
    )
    abstract suspend fun all(): List<MemoryRow>

    @Query("SELECT * FROM memory_song WHERE memoryUid = :uid ORDER BY position")
    abstract suspend fun songs(uid: Long): List<MemorySongEntity>

    @Insert protected abstract suspend fun insert(memory: MemoryEntity): Long
    @Insert protected abstract suspend fun insertSongs(songs: List<MemorySongEntity>)

    @Transaction
    open suspend fun add(memory: MemoryEntity, songs: List<MemorySong>): Long {
        val uid = insert(memory)
        insertSongs(songs.mapIndexed { i, s -> MemorySongEntity(uid, i, s.libraryId, s.songId, s.title) })
        return uid
    }

    @Query("DELETE FROM memory WHERE uid = :uid") protected abstract suspend fun deleteMemory(uid: Long)
    @Query("DELETE FROM memory_song WHERE memoryUid = :uid") protected abstract suspend fun deleteSongs(uid: Long)
    @Query("DELETE FROM memory") protected abstract suspend fun deleteAllMemories()
    @Query("DELETE FROM memory_song") protected abstract suspend fun deleteAllSongs()

    @Transaction
    open suspend fun delete(uid: Long) {
        deleteSongs(uid)
        deleteMemory(uid)
    }

    @Transaction
    open suspend fun deleteAll() {
        deleteAllSongs()
        deleteAllMemories()
    }
}

class RoomMemories(private val dao: MemoryDao) : Memories {
    override suspend fun all(): List<GatheringMemory> = dao.all().map { GatheringMemory(it.uid, it.name, it.startedAt, it.endedAt, it.led, it.singers, it.songs) }
    override suspend fun songs(uid: Long): List<MemorySong> = dao.songs(uid).map { MemorySong(it.libraryId, it.songId, it.title) }
    override suspend fun add(name: String, startedAt: Long, endedAt: Long, led: Boolean, singers: Int?, songs: List<MemorySong>): Long =
        dao.add(MemoryEntity(name = name, startedAt = startedAt, endedAt = endedAt, led = led, singers = singers), songs)
    override suspend fun delete(uid: Long) = dao.delete(uid)
    override suspend fun deleteAll() = dao.deleteAll()
}
