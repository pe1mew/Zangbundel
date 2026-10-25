// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.singer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.app.ui.theme.Palette
import nl.pe1mew.zangbundel.core.protocol.engine.SingerEngine
import nl.pe1mew.zangbundel.core.protocol.frame.Reassembler.Block
import nl.pe1mew.zangbundel.core.protocol.payload.DocumentType

/**
 * The reception bar (guiDesign D-81, FR-SING-05): a floating island at the very bottom of the
 * singer's screen with one row per document, *Lied* and *Lijst*, each a row of the document's source
 * blocks: green received, white still to come, orange missed. On a night-blue-dark surface in both
 * themes so that all three colours read. Colours change; nothing moves, blinks or counts (D-48). A tap
 * anywhere calls [onDismiss], which switches the setting off.
 */
@Composable
fun ReceptionIsland(rows: List<SingerEngine.Reception>, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val song = stringResource(R.string.reception_song)
    val setlist = stringResource(R.string.reception_setlist)
    val description = stringResource(R.string.reception_description)
    Column(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .background(Palette.NightBlueDark, RoundedCornerShape(12.dp))
            .clickable(onClick = onDismiss)
            .semantics { contentDescription = description }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (row in rows) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (row.doc == DocumentType.SONG) song else setlist,
                    color = Palette.OffWhite,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(40.dp),
                )
                Blocks(row.blocks, Modifier.weight(1f).height(10.dp))
            }
        }
    }
}

@Composable
private fun Blocks(blocks: List<Block>, modifier: Modifier) {
    Canvas(modifier) {
        if (blocks.isEmpty()) {
            drawRect(Palette.White.copy(alpha = 0.25f))       // a version with no frame yet: nothing to show
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
