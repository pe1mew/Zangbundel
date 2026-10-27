// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.platform.storage

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * One campfire's statistics record (guiDesign D-60, assessment SP-16), kept on the leader's phone only
 * while the opt-in is on: the date, the figures the season view lists, and [record], the whole record as
 * the export writes it (JSON). Nothing in it names or identifies a person, a phone or an installation;
 * it leaves the phone only through the share sheet, by the leader's hand.
 */
data class CampfireStats(val uid: Long, val date: String, val durationMs: Long, val songs: Int, val maxSingers: Int, val record: String)

/** The opt-in statistics; [RoomStatistics] on the phone. Deleted by *Wissen* and by "remove all data" (FR-DATA-07). */
interface Statistics {
    suspend fun all(): List<CampfireStats>
    suspend fun add(date: String, durationMs: Long, songs: Int, maxSingers: Int, record: String): Long
    suspend fun deleteAll()
}

@Entity(tableName = "campfire_statistics")
data class CampfireStatsEntity(
    @PrimaryKey(autoGenerate = true) val uid: Long = 0,
    val date: String,
    val durationMs: Long,
    val songs: Int,
    val maxSingers: Int,
    val record: String,
)

@Dao
abstract class StatisticsDao {
    @Query("SELECT * FROM campfire_statistics ORDER BY uid")
    abstract suspend fun all(): List<CampfireStatsEntity>

    @Insert abstract suspend fun insert(stats: CampfireStatsEntity): Long

    @Query("DELETE FROM campfire_statistics") abstract suspend fun deleteAll()
}

class RoomStatistics(private val dao: StatisticsDao) : Statistics {
    override suspend fun all(): List<CampfireStats> = dao.all().map { CampfireStats(it.uid, it.date, it.durationMs, it.songs, it.maxSingers, it.record) }
    override suspend fun add(date: String, durationMs: Long, songs: Int, maxSingers: Int, record: String): Long =
        dao.insert(CampfireStatsEntity(date = date, durationMs = durationMs, songs = songs, maxSingers = maxSingers, record = record))
    override suspend fun deleteAll() = dao.deleteAll()
}
