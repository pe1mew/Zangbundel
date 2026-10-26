// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.singer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.app.ui.theme.Palette
import nl.pe1mew.zangbundel.core.model.SongRef
import nl.pe1mew.zangbundel.core.protocol.engine.SingerEngine
import nl.pe1mew.zangbundel.core.protocol.frame.Reassembler.Block
import nl.pe1mew.zangbundel.core.protocol.payload.DocumentType

/**
 * The reception view (guiDesign D-81, FR-SING-05): a floating island at the very bottom of the singer's
 * screen, on a night-blue-dark surface in both themes so that all three colours read: green received,
 * white still to come, orange missed. With songs sent ahead (v0.28, FR-BLE-18) it is a grid, *Lijst*
 * and then one row per setlist song, a song not yet pushed labelled with its setlist number only (D-47),
 * a pushed one with its title ([titleOf]), the current one marked with the orange bar of D-29; from a
 * leader without songs ahead it keeps the two rows *Lied* and *Lijst*. At most a third of the screen
 * high, scrolling inside beyond that. Colours change; nothing moves, blinks or counts (D-48). A tap
 * anywhere calls [onDismiss].
 */
@Composable
fun ReceptionIsland(rows: List<SingerEngine.Reception>, titleOf: (SongRef) -> String?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val song = stringResource(R.string.reception_song)
    val setlist = stringResource(R.string.reception_setlist)
    val description = stringResource(R.string.reception_description)
    val grid = rows.any { it.position != null }
    val shown = if (grid) rows.filter { it.doc != DocumentType.SONG } else rows
    val maxHeight = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height / 3).toDp() }
    Column(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .background(Palette.NightBlueDark, RoundedCornerShape(12.dp))
            .clickable(onClick = onDismiss)
            .semantics { contentDescription = description }
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .heightIn(max = maxHeight)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(if (grid) 4.dp else 6.dp),
    ) {
        for (row in shown) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // D-29's orange bar marks the current song's row.
                Box(Modifier.width(3.dp).height(12.dp).background(if (row.current) Palette.FireOrange else Color.Transparent))
                Spacer(Modifier.width(4.dp))
                val label = when {
                    row.position != null -> row.ref?.let(titleOf) ?: row.position.toString()
                    row.doc == DocumentType.SONG -> song
                    else -> setlist
                }
                Text(
                    label,
                    color = Palette.OffWhite,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(if (grid) 88.dp else 40.dp),
                )
                Blocks(row.blocks, Modifier.weight(1f).height(if (grid) 8.dp else 10.dp), emptyAsPending = row.onAir && row.position != null)
            }
        }
    }
}

/**
 * A document's source blocks as a row: green received, white still to come, orange missed (D-81).
 * Shared with the song bar on the divider of a song still coming in (D-29, v0.21), where a version
 * with no frame yet reads as all white ([emptyAsPending]).
 */
@Composable
internal fun Blocks(blocks: List<Block>, modifier: Modifier, emptyAsPending: Boolean = false) {
    Canvas(modifier) {
        if (blocks.isEmpty()) {
            drawRect(if (emptyAsPending) Palette.White else Palette.White.copy(alpha = 0.25f))
            return@Canvas
        }
        val w = size.width / blocks.size
        // A one-pixel gap between blocks while they are wide enough to keep one; at least a pixel each.
        val gap = if (w >= 4f) 1f else 0f
        blocks.forEachIndexed { i, b ->
            val color = when (b) {
                Block.RECEIVED -> Palette.ReceptionGreen
                Block.MISSED -> Palette.FireOrange
                Block.PENDING -> Palette.White
            }
            drawRect(color, topLeft = Offset(i * w, 0f), size = Size(maxOf(1f, w - gap), size.height))
        }
    }
}
