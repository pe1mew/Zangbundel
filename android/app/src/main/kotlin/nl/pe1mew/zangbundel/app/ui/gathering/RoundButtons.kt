// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.gathering

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import nl.pe1mew.zangbundel.app.ui.songbook.ColourSquare
import nl.pe1mew.zangbundel.platform.storage.SongbookEntry
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.app.ui.LyricColors
import nl.pe1mew.zangbundel.app.ui.theme.Palette

/**
 * The three round buttons of guiDesign D-30, bottom right: previous song, songlist, next song, 56 dp with
 * 12 dp between them, dark translucent so they do not dazzle at the fire [FR-DISP-03]. They dim to 40 %
 * after [DIM_AFTER_MS] without a touch; [touched] changes on every touch on the page and brings them back.
 * In the [day] theme they are light grey with black icons.
 */
@Composable
internal fun RoundButtons(
    touched: Long,
    dimmed: Boolean,
    onDim: () -> Unit,
    prevEnabled: Boolean,
    nextEnabled: Boolean,
    onPrev: () -> Unit,
    onList: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    day: Boolean = false,
) {
    LaunchedEffect(touched) { delay(DIM_AFTER_MS); onDim() }
    Column(modifier.alpha(if (dimmed) DIMMED else 1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Round(R.drawable.ic_chevron_up, stringResource(R.string.round_prev), prevEnabled, day, onPrev)
        Round(R.drawable.ic_list, stringResource(R.string.round_list), true, day, onList)
        Round(R.drawable.ic_chevron_down, stringResource(R.string.round_next), nextEnabled, day, onNext)
    }
}

@Composable
private fun Round(icon: Int, label: String, enabled: Boolean, day: Boolean, onClick: () -> Unit) {
    val ink = if (day) Color.Black else Color.White
    Box(
        Modifier.size(56.dp).background(if (day) ButtonDay else ButtonNight, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = ink.copy(alpha = if (enabled) 1f else 0.3f), modifier = Modifier.size(28.dp))
    }
}

/** Every touch on the page, without taking it: for the buttons' dimming (D-30). */
internal fun Modifier.onAnyTouch(touch: () -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope { while (true) { awaitPointerEvent(PointerEventPass.Initial); touch() } }
}

/** One row of a songlist sheet. */
internal class SheetRow(val title: String, val current: Boolean, val sung: Boolean)

/**
 * The songlist sheet (guiDesign D-31 for singers, D-58 in part for the leader): the current song pinned
 * at the top with the orange bar, then the [rows] in order, sung ones with a check; a tap jumps to that
 * song. [footer] is the sheet's last row (*Kampvuur verlaten* for a singer), null for none. With a
 * [songbook] the sheet has a second tab, *Zangbundel* (D-40): the installed songs with a title search; a
 * tap opens one to read without leaving the campfire ([onBrowse], FR-SING-14).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SongSheet(
    rows: List<SheetRow>,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    footer: String? = null,
    onFooter: () -> Unit = {},
    songbook: List<SongbookEntry>? = null,
    onBrowse: (SongbookEntry) -> Unit = {},
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var tab by remember { mutableIntStateOf(0) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = SheetNight, contentColor = Color.White) {
        if (songbook == null) Text(stringResource(R.string.round_list), color = LyricColors.Night.secondary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp))
        else TabRow(
            selectedTabIndex = tab, containerColor = SheetNight, contentColor = Color.White,
            indicator = { positions -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[tab]), color = Palette.FireOrange) },
        ) {
            Tab(tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.round_list)) })
            Tab(tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.sheet_tab_songbook)) })
        }
        if (songbook != null && tab == 1) {
            BrowseList(songbook, onBrowse)
            return@ModalBottomSheet
        }
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding()) {
            val current = rows.indexOfFirst { it.current }
            if (current >= 0) item { SheetLine(current, rows[current], onPick) }
            if (current >= 0) item { HorizontalDivider(color = LyricColors.Night.divider) }
            itemsIndexed(rows) { i, r -> if (i != current) SheetLine(i, r, onPick) }
            if (footer != null) item {
                HorizontalDivider(color = LyricColors.Night.divider)
                Text(footer, color = Palette.FireOrange, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onFooter).padding(16.dp))
            }
        }
    }
}

/** D-40: the songbook to browse during a campfire, title search only (lyrics search and filter chips are not built). */
@Composable
private fun BrowseList(songbook: List<SongbookEntry>, onBrowse: (SongbookEntry) -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = remember(songbook, query) {
        val q = fold(query.trim())
        songbook.filter { q.isEmpty() || fold(it.title).contains(q) }.sortedBy { fold(it.sortTitle ?: it.title) }
    }
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).navigationBarsPadding()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text(stringResource(R.string.sheet_search)) },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Palette.FireOrange, cursorColor = Palette.FireOrange),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        // A singer's phone often has no library: say so, with the way to fill the songbook.
        if (songbook.isEmpty()) Text(stringResource(R.string.songbook_empty), color = LyricColors.Night.secondary, modifier = Modifier.padding(16.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(shown, key = { it.libraryId + "/" + it.songId }) { e ->
                Row(Modifier.fillMaxWidth().clickable { onBrowse(e) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ColourSquare(e.colour)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.title, color = LyricColors.Night.lyric, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(e.libraryName, color = LyricColors.Night.secondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetLine(i: Int, r: SheetRow, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onPick(i) }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(24.dp).background(if (r.current) Palette.FireOrange else Color.Transparent))
        Spacer(Modifier.width(12.dp))
        Text(r.title, color = LyricColors.Night.lyric, fontSize = 18.sp, fontWeight = if (r.current) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (r.sung && !r.current) Text("✓", color = LyricColors.Night.secondary)
    }
}

/** D-30: night colours, #1A1A1A at 80 %; day colours, #E6E6E6 at 80 %. */
private val ButtonNight = Color(0xCC1A1A1A)
private val ButtonDay = Color(0xCCE6E6E6)
internal val SheetNight = Color(0xFF121212)
private const val DIMMED = 0.4f
private const val DIM_AFTER_MS = 3_000L
