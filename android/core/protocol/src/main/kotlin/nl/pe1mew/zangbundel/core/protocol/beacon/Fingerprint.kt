// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.beacon

import nl.pe1mew.zangbundel.core.model.GatheringId

/**
 * The gathering fingerprint (ble-protocol 6.6, FR-GATH-18): two words chosen by the first two bytes of `gid`,
 * so a leader can say them aloud. Nothing of it goes over the air: each phone looks the words up in the lists
 * of its own app language, which are index-aligned translations of the Dutch lists of ble-protocol appendix A
 * (the app's `fingerprint_words_1` and `fingerprint_words_2`, guiDesign D-36).
 */
object Fingerprint {
    /** Words per list; `gid[0]` and `gid[1]` index them. */
    const val WORDS_PER_LIST = 256

    /** Where a gathering's two words stand in the lists: `gid[0]` and `gid[1]`, read unsigned. */
    data class Key(val first: Int, val second: Int)

    fun keyOf(gid: GatheringId): Key = Key(gid.bytes[0].toInt() and 0xFF, gid.bytes[1].toInt() and 0xFF)

    /** The pair in one language: word [Key.first] of [first] and word [Key.second] of [second], joined by a hyphen. */
    fun words(key: Key, first: List<String>, second: List<String>): String {
        require(first.size == WORDS_PER_LIST && second.size == WORDS_PER_LIST) { "two lists of $WORDS_PER_LIST words" }
        return first[key.first] + "-" + second[key.second]
    }
}
