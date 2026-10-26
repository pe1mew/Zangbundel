// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.platform.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer

/** One song in the combined songbook (guiDesign D-52): enough to list, filter and open it. */
data class SongbookEntry(
    val libraryId: String,
    val songId: String,
    val title: String,
    val sortTitle: String?,
    val language: String,
    val tags: List<String>,
    val libraryName: String,
    /** The library's D-51 colour index, as in [LibraryRow.colour]. */
    val colour: Int,
    /** The installed revision's directory and the song's file in it, for [Songbook.read]. */
    val directory: String,
    val fileName: String,
    /** The content hash in hex (song-format 8.2): how a pushed song is matched to a local copy (ble-protocol 10.3, FR-BLE-12). */
    val hash: String = "",
)

/**
 * The combined songbook of every enabled library [FR-LIB-30, FR-LIB-31]; a disabled library is left
 * out (D-50). [RoomSongbook] on the phone.
 */
interface Songbook {
    suspend fun entries(): List<SongbookEntry>

    /** Songs whose title or lyrics hold every word typed, as `libraryId/songId`; empty for nothing searchable. */
    suspend fun searchText(input: String): Set<String>

    /** The setlists shipped in enabled libraries (D-59). */
    suspend fun librarySetlists(): List<LibrarySetlist>

    /** A file of an installed revision: a song by its [SongbookEntry.fileName], a setlist by [LibrarySetlist.fileName], or [InstallPlan.LIBRARY_FILE]. */
    suspend fun read(libraryId: String, directory: String, fileName: String): ByteArray
}

/**
 * Turns what the user typed into an FTS4 MATCH expression: every run of letters and digits becomes a
 * prefix term, all of them required. Nothing else of the input reaches the query, so quotes, `-`,
 * `OR`, `NEAR` and column filters typed by the user are plain words or dropped. Case and diacritics are
 * folded by the table's unicode61 tokenizer. Null when nothing searchable is left.
 */
object FtsQuery {
    private const val MAX_TERMS = 8
    /** Letters and decimal digits, after NFC so a typed accent stays with its letter. */
    private val WORD = Regex("[\\p{L}\\p{Nd}]+")

    fun of(input: String): String? {
        val nfc = Normalizer.normalize(input, Normalizer.Form.NFC)
        val terms = WORD.findAll(nfc).map { it.value }.filter { it.uppercase() !in OPERATORS }.take(MAX_TERMS).toList()
        return if (terms.isEmpty()) null else terms.joinToString(" ") { "$it*" }
    }

    /** FTS4 operators count only in upper case, but the user meant words either way; dropped in any case. */
    private val OPERATORS = setOf("AND", "OR", "NOT", "NEAR")
}

class RoomSongbook(private val dao: LibraryDao, private val files: LibraryFiles) : Songbook {
    override suspend fun entries(): List<SongbookEntry> = dao.songbook().map {
        SongbookEntry(it.libraryId, it.songId, it.title, it.sortTitle, it.language, it.tags.split('\n').filter(String::isNotEmpty),
            it.libraryName, it.colour, it.directory, it.fileName, it.hash)
    }

    override suspend fun searchText(input: String): Set<String> {
        val match = FtsQuery.of(input) ?: return emptySet()
        return dao.searchKeys(match).mapTo(HashSet()) { it.libraryId + "/" + it.songId }
    }

    override suspend fun librarySetlists(): List<LibrarySetlist> = dao.librarySetlists()

    override suspend fun read(libraryId: String, directory: String, fileName: String): ByteArray =
        withContext(Dispatchers.IO) { files.read(libraryId, directory, fileName) }
}
