// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.model

/**
 * Song reference as it travels over BLE (ble-protocol section 5, `ref`): library id, song id,
 * content hash, and the informational library revision. Equality of the hash alone decides a match.
 */
data class SongRef(
    val library: LibraryId,
    val song: SongId,
    val hash: ContentHash,
    val revision: String? = null,
)

/** One line of a setlist (song-format section 13): a resolved song or a title-only entry. */
sealed interface SetlistEntry {
    data class Song(val library: LibraryId, val song: SongId) : SetlistEntry
    data class TitleOnly(val title: String) : SetlistEntry
}

/** Status of a setlist item as broadcast in the setlist document (ble-protocol section 9.5). */
enum class SetlistStatus { UPCOMING, CURRENT, SUNG, SKIPPED }

/** Status of a song request as broadcast in `reqstat` (ble-protocol section 9.5). */
enum class RequestStatus { PENDING, ACCEPTED, DECLINED, SUNG, WITHDRAWN }
