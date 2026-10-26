// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.library

import nl.pe1mew.zangbundel.platform.storage.SongbookEntry
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/**
 * What the leader has set on the *Liedboek* screen (guiDesign D-52). The default is everything, A–Z;
 * [isDefault] decides whether *Wis filters* is shown.
 */
data class SongbookFilter(
    val query: String = "",
    /** *Ook in de tekst*: match the lyrics too, through the full-text index [FR-LIB-31]. */
    val inText: Boolean = false,
    val descending: Boolean = false,
    val libraries: Set<String> = emptySet(),
    val languages: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
) {
    val isDefault: Boolean get() = this == SongbookFilter()

    /** A row of the list; [showLibrary] when another visible row has the same title (D-52, amended in v0.7). */
    data class Row(val entry: SongbookEntry, val showLibrary: Boolean) {
        val key: String get() = entry.libraryId + "/" + entry.songId
    }

    /**
     * Applies the filter. [textHits] are the full-text matches as `libraryId/songId`, used when
     * [inText] is on and something is typed; otherwise the title must hold every typed word, compared
     * without case or accents. Within a filter group any chosen value matches; the groups all apply.
     */
    fun apply(entries: List<SongbookEntry>, textHits: Set<String>?, locale: Locale = Locale.getDefault()): List<Row> {
        val words = query.split(' ').map(::fold).filter(String::isNotEmpty)
        val visible = entries.filter { e ->
            (libraries.isEmpty() || e.libraryId in libraries) &&
                (languages.isEmpty() || e.language in languages) &&
                (tags.isEmpty() || e.tags.any { it in tags }) &&
                when {
                    words.isEmpty() -> true
                    inText -> textHits?.contains(e.libraryId + "/" + e.songId) == true
                    else -> fold(e.title).let { t -> words.all { it in t } }
                }
        }
        val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
        val order = compareBy<SongbookEntry, String>(collator) { it.sortTitle ?: it.title }.thenBy { it.libraryId }
        val sorted = visible.sortedWith(if (descending) order.reversed() else order)
        val repeated = sorted.groupingBy { fold(it.title) }.eachCount().filterValues { it > 1 }.keys
        return sorted.map { Row(it, fold(it.title) in repeated) }
    }

    companion object {
        private val MARKS = Regex("\\p{M}+")

        /** Lower case without accents: "Één" and "een" are the same word to a leader typing on a phone. */
        fun fold(s: String): String = MARKS.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase(Locale.ROOT).trim()
    }
}
