// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.songformat

import nl.pe1mew.zangbundel.core.model.ContentHash

/**
 * A parsed song file (song-format sections 6, 7 and 9): its canonical text and hash, the metadata,
 * and the body as the lyric view shows it, in sung order with chorus repeats expanded.
 */
class Song(
    val canonical: String,
    val hash: ContentHash,
    val meta: SongMeta,
    val body: List<Part>,
) {
    /** The canonical line list (section 9.1): display texts, line `n` at index `n − 1`. */
    val lines: List<String> = body.flatMap { part ->
        (part as? Part.Section)?.items?.filterIsInstance<Item.Line>()?.map { it.text }.orEmpty()
    }
}

/**
 * Song metadata (section 6.1), values exactly as written [FR-LIB-23]. Multi-valued fields keep their
 * file order; [other] keeps unknown meta names as opaque text (section 10.1 rule 4).
 */
data class SongMeta(
    val id: String,
    val title: String,
    val language: String,
    val subtitle: String? = null,
    val sortTitle: String? = null,
    val lyricists: List<String> = emptyList(),
    val composers: List<String> = emptyList(),
    val artists: List<String> = emptyList(),
    val year: String? = null,
    val copyright: String? = null,
    val license: String? = null,
    val source: String? = null,
    val tags: List<String> = emptyList(),
    val melody: String? = null,
    val variantGroup: String? = null,
    /** `{meta: redistribute no}` marks a display-only song [FR-LIB-22]; default `yes`. */
    val redistribute: Boolean = true,
    val key: String? = null,
    val capo: String? = null,
    val tempo: String? = null,
    val time: String? = null,
    val duration: String? = null,
    val other: Map<String, List<String>> = emptyMap(),
) {
    /**
     * The licence summary a leader sends with a broadcast song (6.2, BLE 9.5): copyright with `©`,
     * the licence id or "licentie van bundel <library>", lyricists and composers, and the source,
     * joined by ` · ` and cut at a character boundary to 200 characters.
     */
    fun licenceSummary(libraryName: String): String {
        val parts = listOfNotNull(copyright?.let { if ('©' in it) it else "© $it" }, license ?: "licentie van bundel $libraryName") +
            lyricists + composers + listOfNotNull(source)
        val text = parts.joinToString(" · ")
        if (text.codePointCount(0, text.length) <= LICENCE_SUMMARY_MAX_CHARS) return text
        return text.substring(0, text.offsetByCodePoints(0, LICENCE_SUMMARY_MAX_CHARS))
    }

    companion object {
        const val LICENCE_SUMMARY_MAX_CHARS = 200
    }
}

/** A top-level part of the body: a section, or a remark outside any section. */
sealed interface Part {
    /**
     * A verse, chorus or bridge (7.1, 7.3). [label] is as written, or null, in which case the UI
     * generates one in its own language ("Couplet n", "Refrein", "Brug"); [verseNumber] counts verse
     * environments and implicit verses in file order. [repeat] marks a `{chorus}` repetition (7.2).
     */
    data class Section(
        val kind: Kind,
        val label: String?,
        val verseNumber: Int?,
        val repeat: Boolean,
        val items: List<Item>,
    ) : Part

    data class Remark(val text: String, val style: RemarkStyle) : Part
}

enum class Kind { VERSE, CHORUS, BRIDGE }

/** `{comment}`, `{comment_italic}`, `{comment_box}` (7.4): shown as a remark, not a lyric line. */
enum class RemarkStyle { PLAIN, ITALIC, BOX }

sealed interface Item {
    /** A canonical line: [number] is its 1-based position in the line list (section 9). */
    data class Line(val number: Int, val text: String) : Item
    data class Remark(val text: String, val style: RemarkStyle) : Item
    /** A blank line inside an environment: a visual gap (7.3). */
    data object Gap : Item
}
