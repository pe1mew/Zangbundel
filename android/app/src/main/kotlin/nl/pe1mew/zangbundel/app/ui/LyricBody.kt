// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.TextStyle
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
 * wrapping with a hanging indent of 1 em (D-28), remarks in the secondary colour.
 */
@Composable
fun LyricBody(song: Song, colors: LyricColors, modifier: Modifier = Modifier, size: Int = LYRIC_SIZES_SP[2]) {
    val line = TextStyle(fontSize = size.sp, lineHeight = lyricLineHeight(size).sp, color = colors.lyric)
    val wrap = ParagraphStyle(textIndent = TextIndent(firstLine = 0.sp, restLine = 1.em))
    Column(modifier) {
        for ((i, part) in song.body.withIndex()) {
            if (i > 0) Spacer(Modifier.height(20.dp))
            when (part) {
                is Part.Remark -> Text(part.text, fontSize = 16.sp, fontStyle = FontStyle.Italic, color = colors.secondary)
                is Part.Section -> {
                    Text(sectionLabel(part), fontSize = 14.sp, color = colors.secondary)
                    for (item in part.items) when (item) {
                        is Item.Line -> Text(item.text, style = line.merge(wrap))
                        is Item.Remark -> Text(item.text, fontSize = 16.sp, fontStyle = FontStyle.Italic, color = colors.secondary)
                        Item.Gap -> Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

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
