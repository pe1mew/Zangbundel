// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.gathering

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.app.gathering.Gathering
import nl.pe1mew.zangbundel.app.ui.LyricColors
import nl.pe1mew.zangbundel.app.ui.theme.Palette

/**
 * The leader bar's drawer (guiDesign D-61) as far as its functions exist: on top the live statistics of
 * D-62 (time since the start, songs sung, singers now with the evening's maximum, link quality as one
 * word with the last song's duration under it), the rows that open the join panels of D-57 (the two
 * words or the *Deelnamecode*, and *QR-code tonen*; v0.34), and, set apart, *Kampvuur beëindigen* (D-69). Requests (D-63), assistants (D-73), sharing
 * statistics (D-60) and another campfire nearby (D-68) get their rows when they are built. A swipe up, the
 * handle at the bottom, a tap on the lyrics or *Nu* close it.
 */
@Composable
internal fun LeaderDrawer(m: Gathering.Mode.Leading, now: Long, onJoin: (qr: Boolean) -> Unit, onEnd: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Palette.NightBlueDark, RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy < -DRAG_PX) onClose() } }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Figure(elapsed(now - m.startedAt), stringResource(R.string.drawer_time))
            Figure(m.songsSung.toString(), stringResource(R.string.drawer_songs))
            Figure(m.singers.toString(), stringResource(R.string.drawer_singers), stringResource(R.string.drawer_max, maxOf(m.peak, m.singers)))
            Figure(
                stringResource(
                    when {
                        m.quality < GOOD_BELOW -> R.string.drawer_link_good
                        m.quality < FAIR_BELOW -> R.string.drawer_link_fair
                        else -> R.string.drawer_link_poor
                    },
                ),
                stringResource(R.string.drawer_link),
                m.lastSongMs?.let { stringResource(R.string.drawer_last_song, elapsed(it, seconds = true)) },
            )
        }
        // D-57 as amended in v0.34: how singers join, in this gathering's mode; each row opens its full-screen panel.
        HorizontalDivider(color = LyricColors.Night.divider)
        when (val p = m.protection) {
            Gathering.Protection.Open -> JoinRow(stringResource(R.string.panel_words_title), m.fingerprint, big = false) { onJoin(false) }
            is Gathering.Protection.Code -> JoinRow(stringResource(R.string.panel_code_title), p.code, big = true) { onJoin(false) }
            is Gathering.Protection.QrOnly -> Unit
        }
        JoinRow(stringResource(R.string.panel_show_qr), null, big = false) { onJoin(true) }
        HorizontalDivider(color = LyricColors.Night.divider)
        Text(
            stringResource(R.string.drawer_end),
            color = Palette.FireOrange,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onEnd).padding(vertical = 8.dp),
        )
        // The handle at the bottom edge slides it shut.
        Box(Modifier.fillMaxWidth().clickable(onClick = onClose).padding(top = 4.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 40.dp, height = 4.dp).background(Palette.OffWhite.copy(alpha = 0.5f), RoundedCornerShape(2.dp)))
        }
    }
}

/** A row of the drawer that opens a join panel: its name, and the code or the words when there are some. */
@Composable
private fun JoinRow(label: String, value: String?, big: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Palette.OffWhite, modifier = Modifier.weight(1f))
        if (value != null) Text(value, color = Palette.White, fontSize = if (big) 22.sp else 16.sp, fontWeight = FontWeight.Bold, letterSpacing = if (big) 4.sp else 0.sp)
        Text("  ›", color = Palette.FireOrange, fontSize = 18.sp)
    }
}

@Composable
private fun Figure(value: String, label: String, small: String? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Palette.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = Palette.OffWhite, fontSize = 12.sp, textAlign = TextAlign.Center)
        if (small != null) Text(small, color = Palette.OffWhite, fontSize = 11.sp, textAlign = TextAlign.Center) else Spacer(Modifier.height(0.dp))
    }
}

/** h:mm, or m:ss with [seconds]. */
private fun elapsed(ms: Long, seconds: Boolean = false): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (seconds) "%d:%02d".format(s / 60, s % 60) else "%d:%02d".format(s / 3600, (s / 60) % 60)
}

/** D-62: link quality from the share of singers the leader helped over GATT in the last minute. */
private const val GOOD_BELOW = 0.3f
private const val FAIR_BELOW = 0.7f
private const val DRAG_PX = 8f
