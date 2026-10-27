// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import java.util.TreeMap

/**
 * Where the lines of the songs on a lyric page are, by song key and line code, as laid out last: for the
 * verse jumps of FR-SING-13 and for keeping the top line through a rotation (guiDesign D-42, FR-DISP-04).
 * A line code is `part * 1000` for a section's label and `part * 1000 + n` for its n-th item (see
 * [LyricBody]). Positions are only known for songs laid out since the page appeared.
 */
class LineKeeper {
    private val itemTops = HashMap<String, Float>()
    private val lineTops = HashMap<String, TreeMap<Int, Float>>()

    fun item(key: String, c: LayoutCoordinates) {
        itemTops[key] = c.positionInRoot().y
    }

    fun line(key: String, code: Int, c: LayoutCoordinates) {
        lineTops.getOrPut(key) { TreeMap() }[code] = c.positionInRoot().y
    }

    /** Pixels from the top of song [key]'s item to line [code]; null when not laid out. */
    fun offsetOf(key: String, code: Int): Int? {
        val top = itemTops[key] ?: return null
        return lineTops[key]?.get(code)?.let { (it - top).toInt() }
    }

    /** The section labels of song [key], in pixels from the top of its item, in order. */
    fun sectionStarts(key: String): List<Int> = codes(key).filter { it.first % CODES_PER_PART == 0 }.map { it.second }

    /** The line at or just above [offset] pixels into song [key]'s item, as its code; null above the first. */
    fun lineAt(key: String, offset: Int): Int? = codes(key).lastOrNull { it.second <= offset + SLACK_PX }?.first

    private fun codes(key: String): List<Pair<Int, Int>> {
        val top = itemTops[key] ?: return emptyList()
        return lineTops[key].orEmpty().map { (code, y) -> code to (y - top).toInt() }.sortedBy { it.second }
    }

    companion object {
        const val CODES_PER_PART = 1000
        const val SLACK_PX = 8
        /** Frames to wait for the page to be laid out at its new width: two seconds at 60 Hz. */
        const val RESTORE_FRAMES = 120
    }
}

/**
 * guiDesign D-42, FR-DISP-04: keeps the line at the top of a lyric page through a rotation. The line is
 * saved as song key and line code while the page scrolls; after the activity is made anew, and the text
 * has wrapped at its new width, the page is scrolled so that line is at the top again. Pixel offsets do not
 * survive a rotation, which is why the list's own saved position is not enough. [keyAt] names the song of
 * a list item (null for an item that is no song), [indexOf] finds a song's item again.
 */
@Composable
fun KeepTopLine(list: LazyListState, lines: LineKeeper, keyAt: (Int) -> String?, indexOf: (String) -> Int) {
    var topLine by rememberSaveable { mutableStateOf<String?>(null) }
    val keyOf by rememberUpdatedState(keyAt)
    val find by rememberUpdatedState(indexOf)
    LaunchedEffect(list, lines) {
        topLine?.let { saved ->
            val key = saved.substringBeforeLast('|')
            val code = saved.substringAfterLast('|').toIntOrNull()
            val index = find(key)
            if (code != null && index >= 0) {
                // The song's item first, so it is laid out even while the page is still filling (the leader's page
                // reads its lyrics anew); then its line, once its position is known.
                var offset: Int? = null
                repeat(LineKeeper.RESTORE_FRAMES) {
                    if (offset == null) {
                        if (list.layoutInfo.visibleItemsInfo.none { it.index == index }) list.scrollToItem(index)
                        withFrameNanos { }
                        offset = lines.offsetOf(key, code)
                    }
                }
                list.scrollToItem(index, offset ?: 0)
            }
        }
        snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }.collect { (i, off) ->
            val key = keyOf(i)
            topLine = key?.let { k -> lines.lineAt(k, off)?.let { "$k|$it" } }
        }
    }
}
