// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.core.songformat.Item
import nl.pe1mew.zangbundel.core.songformat.Kind
import nl.pe1mew.zangbundel.core.songformat.Part
import nl.pe1mew.zangbundel.core.songformat.Song

/** The two themes of guiDesign D-15: lyric text and secondary text on pure white or pure black. */
data class LyricColors(val background: Color, val lyric: Color, val secondary: Color, val divider: Color) {
    companion object {
        val Day = LyricColors(Color.White, Color.Black, Color(0xFF444444), Color(0xFFDDDDDD))
        val Night = LyricColors(Color.Black, Color.White, Color(0xFFBFBFBF), Color(0xFF2A2A2A))
    }
}

/**
 * A song's body as the lyric view shows it: section labels as written or generated in the app's
 * language (song-format 7.1, D-75), lines at [size], one of D-18's five steps (28 sp by default),
 * wrapping with a hanging indent of 1 em (D-28), remarks in the secondary colour. [onLine] gets the
 * layout of each section's label and item by its line code ([LineKeeper]), for the verse jumps of
 * FR-SING-13 and for keeping the top line through a rotation (D-42). With [fitToWidth] (D-28) the lines are
 * set up to two of D-18's steps smaller, as far as needed for the song's longest line to fit on one line;
 * when even that is not enough, the longest lines wrap at the smallest of those sizes.
 */
@Composable
fun LyricBody(
    song: Song,
    colors: LyricColors,
    modifier: Modifier = Modifier,
    size: Int = LYRIC_SIZES_SP[2],
    fitToWidth: Boolean = false,
    onLine: ((code: Int, LayoutCoordinates) -> Unit)? = null,
) {
    BoxWithConstraints(modifier) {
    val measurer = rememberTextMeasurer()
    val width = constraints.maxWidth
    val shown = remember(song, size, fitToWidth, width) { if (fitToWidth) fittedSize(song, size, width, measurer) else size }
    val line = TextStyle(fontSize = shown.sp, lineHeight = lyricLineHeight(shown).sp, color = colors.lyric)
    val wrap = ParagraphStyle(textIndent = TextIndent(firstLine = 0.sp, restLine = 1.em))
    Column {
        for ((i, part) in song.body.withIndex()) {
            if (i > 0) Spacer(Modifier.height(20.dp))
            when (part) {
                is Part.Remark -> Text(part.text, fontSize = 16.sp, fontStyle = FontStyle.Italic, color = colors.secondary)
                is Part.Section -> {
                    Text(sectionLabel(part), fontSize = 14.sp, color = colors.secondary,
                        modifier = Modifier.report(onLine, i * LineKeeper.CODES_PER_PART))
                    for ((n, item) in part.items.withIndex()) when (item) {
                        is Item.Line -> Text(item.text, style = line.merge(wrap), modifier = Modifier.report(onLine, i * LineKeeper.CODES_PER_PART + n + 1))
                        is Item.Remark -> Text(item.text, fontSize = 16.sp, fontStyle = FontStyle.Italic, color = colors.secondary,
                            modifier = Modifier.report(onLine, i * LineKeeper.CODES_PER_PART + n + 1))
                        Item.Gap -> Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
    }
}

/** D-28: the largest of [size] and the two steps below it at which every line of [song] fits [width] pixels. */
private fun fittedSize(song: Song, size: Int, width: Int, measurer: TextMeasurer): Int {
    val step = LYRIC_SIZES_SP.indexOf(size).takeIf { it >= 0 } ?: return size
    val lines = song.body.flatMap { (it as? Part.Section)?.items.orEmpty() }.filterIsInstance<Item.Line>().map { it.text }
    val smallest = maxOf(0, step - FIT_STEPS)
    for (s in step downTo smallest) {
        val style = TextStyle(fontSize = LYRIC_SIZES_SP[s].sp)
        if (lines.all { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width <= width }) return LYRIC_SIZES_SP[s]
    }
    return LYRIC_SIZES_SP[smallest]
}

/** D-28: fit-to-width goes down at most this many steps of D-18. */
private const val FIT_STEPS = 2

private fun Modifier.report(onLine: ((Int, LayoutCoordinates) -> Unit)?, code: Int): Modifier =
    if (onLine != null) onGloballyPositioned { onLine(code, it) } else this

/** guiDesign D-18: the five lyric sizes in sp. */
val LYRIC_SIZES_SP = listOf(18, 22, 28, 36, 48)

/** The line height for a lyric size, in the proportion of the default's 28/36 sp. */
fun lyricLineHeight(size: Int): Int = (size * 36 + 27) / 28

@Composable
private fun sectionLabel(section: Part.Section): String = section.label ?: when (section.kind) {
    Kind.VERSE -> section.verseNumber?.let { stringResource(R.string.song_verse_n, it) } ?: stringResource(R.string.song_verse)
    Kind.CHORUS -> stringResource(R.string.song_chorus)
    Kind.BRIDGE -> stringResource(R.string.song_bridge)
}
