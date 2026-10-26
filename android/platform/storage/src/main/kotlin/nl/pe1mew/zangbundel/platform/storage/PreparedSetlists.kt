// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.platform.storage

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import nl.pe1mew.zangbundel.core.model.isValidId
import java.text.Normalizer

/** A setlist the leader prepared [FR-LEAD-02], kept until removed (architecture section 6, FR-DATA-06). */
data class PreparedSetlist(
    /** The row's own key; [id] is the setlist id a `.zbs` export carries (song-format 13.2). */
    val uid: Long,
    val id: String,
    val title: String,
    val songs: Int,
    val changedAt: Long,
    /** Set when a gathering starts from it (guiDesign D-55); null until then. */
    val lastUsed: Long?,
)

/**
 * One line of a prepared setlist: a song by library and song id, with its title as last seen so a
 * song whose library is gone still reads as something (D-59, *niet gevonden*); or a title-only entry.
 */
data class PreparedEntry(val libraryId: String?, val songId: String?, val title: String) {
    /** `libraryId/songId`, as the songbook keys songs; null for a title-only entry. */
    val songKey: String? get() = if (libraryId != null && songId != null) "$libraryId/$songId" else null
}

/** Prepared setlists (guiDesign D-54); [RoomSetlists] on the phone, a fake in tests. */
interface Setlists {
    suspend fun all(): List<PreparedSetlist>
    suspend fun setlist(uid: Long): PreparedSetlist?
    suspend fun entries(uid: Long): List<PreparedEntry>
    suspend fun create(id: String, title: String, now: Long): Long
    suspend fun rename(uid: Long, title: String, now: Long)
    suspend fun delete(uid: Long)
    /** Replaces the whole list in one transaction: adding, removing and reordering all come here. */
    suspend fun replaceEntries(uid: Long, entries: List<PreparedEntry>, now: Long)
}

/** Setlist ids in song-id syntax (song-format 3.2, 13.2), made from the title and unique on this phone. */
object SetlistIds {
    private val MARKS = Regex("\\p{M}+")
    private val OTHER = Regex("[^a-z0-9]+")

    fun of(title: String, taken: Set<String>): String {
        val base = OTHER.replace(MARKS.replace(Normalizer.normalize(title, Normalizer.Form.NFD), "").lowercase(), "-")
            .trim('-').take(56).trim('-').ifEmpty { "setlist" }
        var id = base
        var n = 2
        while (id in taken) id = "$base-${n++}"
        check(isValidId(id)) { id }
        return id
    }
}

@Entity(tableName = "prepared_setlist")
data class PreparedSetlistEntity(
    @PrimaryKey(autoGenerate = true) val uid: Long = 0,
    val id: String,
    val title: String,
    val createdAt: Long,
    val changedAt: Long,
    val lastUsed: Long?,
)

@Entity(tableName = "prepared_entry", primaryKeys = ["setlistUid", "position"], indices = [Index("setlistUid")])
data class PreparedEntryEntity(val setlistUid: Long, val position: Int, val libraryId: String?, val songId: String?, val title: String)

/** A row of [PreparedSetlistDao.all]. */
data class PreparedSetlistRow(val uid: Long, val id: String, val title: String, val changedAt: Long, val lastUsed: Long?, val songs: Int)

@Dao
abstract class PreparedSetlistDao {
    @Query(
        "SELECT s.uid, s.id, s.title, s.changedAt, s.lastUsed, (SELECT COUNT(*) FROM prepared_entry e WHERE e.setlistUid = s.uid) AS songs " +
            "FROM prepared_setlist s ORDER BY s.title COLLATE NOCASE",
    )
    abstract suspend fun all(): List<PreparedSetlistRow>

    @Query("SELECT * FROM prepared_entry WHERE setlistUid = :uid ORDER BY position")
    abstract suspend fun entries(uid: Long): List<PreparedEntryEntity>

    @Insert abstract suspend fun insert(setlist: PreparedSetlistEntity): Long
    @Insert protected abstract suspend fun insertEntries(entries: List<PreparedEntryEntity>)

    @Query("UPDATE prepared_setlist SET title = :title, changedAt = :now WHERE uid = :uid")
    abstract suspend fun rename(uid: Long, title: String, now: Long)

    @Query("UPDATE prepared_setlist SET changedAt = :now WHERE uid = :uid")
    protected abstract suspend fun touch(uid: Long, now: Long)

    @Query("DELETE FROM prepared_setlist WHERE uid = :uid") protected abstract suspend fun deleteSetlist(uid: Long)
    @Query("DELETE FROM prepared_entry WHERE setlistUid = :uid") protected abstract suspend fun deleteEntries(uid: Long)

    @Transaction
    open suspend fun delete(uid: Long) {
        deleteEntries(uid)
        deleteSetlist(uid)
    }

    @Transaction
    open suspend fun replaceEntries(uid: Long, entries: List<PreparedEntryEntity>, now: Long) {
        deleteEntries(uid)
        insertEntries(entries)
        touch(uid, now)
    }
}

class RoomSetlists(private val dao: PreparedSetlistDao) : Setlists {
    override suspend fun all(): List<PreparedSetlist> = dao.all().map { PreparedSetlist(it.uid, it.id, it.title, it.songs, it.changedAt, it.lastUsed) }
    override suspend fun setlist(uid: Long): PreparedSetlist? = all().firstOrNull { it.uid == uid }
    override suspend fun entries(uid: Long): List<PreparedEntry> = dao.entries(uid).map { PreparedEntry(it.libraryId, it.songId, it.title) }
    override suspend fun create(id: String, title: String, now: Long): Long = dao.insert(PreparedSetlistEntity(id = id, title = title, createdAt = now, changedAt = now, lastUsed = null))
    override suspend fun rename(uid: Long, title: String, now: Long) = dao.rename(uid, title, now)
    override suspend fun delete(uid: Long) = dao.delete(uid)
    override suspend fun replaceEntries(uid: Long, entries: List<PreparedEntry>, now: Long) =
        dao.replaceEntries(uid, entries.mapIndexed { i, e -> PreparedEntryEntity(uid, i, e.libraryId, e.songId, e.title) }, now)
}
