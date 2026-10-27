// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.gathering

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.app.gathering.Gathering
import nl.pe1mew.zangbundel.app.ui.LyricColors
import nl.pe1mew.zangbundel.app.ui.songbook.ColourSquare
import nl.pe1mew.zangbundel.app.ui.theme.Palette
import nl.pe1mew.zangbundel.platform.storage.SongbookEntry
import java.text.Normalizer

/**
 * The leader's songlist sheet (guiDesign D-58 as built in v0.40) with two tabs. *Setlist*: the
 * setlist with the current song marked and sung songs checked, which stay in place; upcoming songs move
 * by their handle and go with a swipe, with *Ongedaan maken* for a few seconds; a tap jumps to the song.
 * *Zangbundel*: the installed songs with a title search, each with *Toevoegen* (at the end) and *Nu
 * zingen* (after the current song, pushed at once). Dark, as every gathering sheet (D-15).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LeaderSheet(
    m: Gathering.Mode.Leading,
    startTab: Int,
    songbook: List<SongbookEntry>,
    onPick: (Int) -> Unit,
    onEdit: (List<Gathering.LeaderSong>) -> Unit,
    onAdd: (SongbookEntry) -> Unit,
    onSingNow: (SongbookEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember { mutableIntStateOf(startTab) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = SheetNight, contentColor = Color.White) {
        Column(Modifier.fillMaxHeight(0.92f).navigationBarsPadding()) {
            TabRow(
                selectedTabIndex = tab, containerColor = SheetNight, contentColor = Color.White,
                indicator = { positions -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[tab]), color = Palette.FireOrange) },
            ) {
                Tab(tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.sheet_tab_setlist)) })
                Tab(tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.sheet_tab_songbook)) })
            }
            if (tab == 0) SetlistTab(m, onPick, onEdit, onAddFromSongbook = { tab = 1 }, Modifier.weight(1f))
            else SongbookTab(m, songbook, onAdd, onSingNow, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SetlistTab(
    m: Gathering.Mode.Leading,
    onPick: (Int) -> Unit,
    onEdit: (List<Gathering.LeaderSong>) -> Unit,
    onAddFromSongbook: () -> Unit,
    modifier: Modifier,
) {
    // The rows as dragged; the gathering's list replaces them whenever it changes.
    var rows by remember(m.songs) { mutableStateOf(m.songs) }
    var removed by remember { mutableStateOf<Pair<Int, Gathering.LeaderSong>?>(null) }
    LaunchedEffect(removed) { if (removed != null) { delay(UNDO_MS); removed = null } }
    val currentLine = m.current?.let { m.songs.getOrNull(it)?.line }
    val sungLines = m.sung.mapNotNull { m.songs.getOrNull(it)?.line }.toSet()
    fun fixed(s: Gathering.LeaderSong) = s.line == currentLine || s.line in sungLines
    val listState = rememberLazyListState()
    var dragged by remember { mutableStateOf<Long?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val move = stringResource(R.string.setlist_move)
    Column(modifier) {
        removed?.let { (at, song) ->
            Row(Modifier.fillMaxWidth().background(Palette.NightBlue).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.sheet_removed, song.title), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    removed = null
                    onEdit(rows.toMutableList().also { it.add(at.coerceAtMost(it.size), song) })
                }) { Text(stringResource(R.string.sheet_undo), color = Palette.FireOrange) }
            }
        }
        LazyColumn(Modifier.weight(1f), state = listState) {
            itemsIndexed(rows, key = { _, s -> s.line }) { i, s ->
                val isDragged = dragged == s.line
                val swipe = rememberSwipeToDismissBoxState()
                LaunchedEffect(swipe.currentValue) {
                    if (swipe.currentValue != SwipeToDismissBoxValue.Settled) {
                        removed = i to s
                        onEdit(rows - s)
                        // Then back to settled: the list keeps a row's saved state by its key, and a song put back with
                        // *Ongedaan maken* has the same line, so a dismissed state would remove it again. Last, since
                        // the new value restarts this effect.
                        swipe.snapTo(SwipeToDismissBoxValue.Settled)
                    }
                }
                SwipeToDismissBox(
                    state = swipe,
                    backgroundContent = {},
                    enableDismissFromStartToEnd = !fixed(s),
                    enableDismissFromEndToStart = !fixed(s),
                    modifier = Modifier.zIndex(if (isDragged) 1f else 0f).graphicsLayer { translationY = if (isDragged) offset else 0f },
                ) {
                    Row(Modifier.fillMaxWidth().background(SheetNight).clickable { onPick(m.songs.indexOfFirst { it.line == s.line }) }.heightIn(min = 52.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(4.dp).height(24.dp).background(if (s.line == currentLine) Palette.FireOrange else Color.Transparent))
                        if (fixed(s)) Spacer(Modifier.size(48.dp)) else Box(
                            Modifier.size(48.dp).semantics { contentDescription = move }.pointerInput(s.line) {
                                detectDragGestures(
                                    onDragStart = { dragged = s.line; offset = 0f },
                                    onDragEnd = { dragged = null; offset = 0f; onEdit(rows) },
                                    onDragCancel = { dragged = null; offset = 0f; onEdit(rows) },
                                ) { change, amount ->
                                    change.consume()
                                    offset += amount.y
                                    // As the setlist editor (D-54): swap with the neighbour once the dragged row's centre is over it.
                                    val items = listState.layoutInfo.visibleItemsInfo
                                    val here = items.firstOrNull { it.key == s.line } ?: return@detectDragGestures
                                    val centre = here.offset + offset + here.size / 2f
                                    val target = items.firstOrNull { it.key != s.line && it.key is Long && centre.toInt() in it.offset until it.offset + it.size }
                                    if (target != null) {
                                        val from = rows.indexOfFirst { it.line == s.line }
                                        val to = rows.indexOfFirst { it.line == target.key }
                                        if (from >= 0 && to >= 0) {
                                            offset += here.offset - target.offset
                                            rows = rows.toMutableList().also { it.add(to, it.removeAt(from)) }
                                        }
                                    }
                                }
                            },
                            contentAlignment = Alignment.Center,
                        ) { Text("≡", fontSize = 22.sp, color = Palette.OffWhite) }
                        s.song?.let { ColourSquare(it.colour) } ?: Spacer(Modifier.size(12.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 18.sp,
                            fontWeight = if (s.line == currentLine) FontWeight.Bold else FontWeight.Normal,
                            color = if (s.song != null) Color.White else LyricColors.Night.secondary, modifier = Modifier.weight(1f))
                        if (s.line in sungLines) Text("✓", color = LyricColors.Night.secondary, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
        HorizontalDivider(color = LyricColors.Night.divider)
        TextButton(onClick = onAddFromSongbook, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Text(stringResource(R.string.sheet_add_from_songbook), color = Palette.FireOrange, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SongbookTab(
    m: Gathering.Mode.Leading,
    songbook: List<SongbookEntry>,
    onAdd: (SongbookEntry) -> Unit,
    onSingNow: (SongbookEntry) -> Unit,
    modifier: Modifier,
) {
    var query by remember { mutableStateOf("") }
    val inSetlist = m.songs.mapNotNull { it.song?.let { e -> e.libraryId + "/" + e.songId } }.toSet()
    val shown = remember(songbook, query) {
        val q = fold(query.trim())
        songbook.filter { q.isEmpty() || fold(it.title).contains(q) }.sortedBy { fold(it.sortTitle ?: it.title) }
    }
    Column(modifier) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text(stringResource(R.string.sheet_search)) },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Palette.FireOrange, cursorColor = Palette.FireOrange),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.weight(1f)) {
            items(shown, key = { it.libraryId + "/" + it.songId }) { e ->
                val added = (e.libraryId + "/" + e.songId) in inSetlist
                // The two buttons stacked at the right, so the title has the width (titles run to 60 characters).
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ColourSquare(e.colour)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 17.sp)
                        Text(e.libraryName, color = LyricColors.Night.secondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Button(
                            onClick = { onSingNow(e) },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Palette.FireOrange),
                            border = BorderStroke(2.dp, Palette.FireOrange),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                        ) { Text(stringResource(R.string.sheet_sing_now), fontWeight = FontWeight.Bold) }
                        TextButton(onClick = { onAdd(e) }, enabled = !added, modifier = Modifier.alpha(if (added) 0.5f else 1f)) {
                            Text(if (added) "✓" else stringResource(R.string.sheet_add), color = Palette.FireOrange)
                        }
                    }
                }
            }
        }
    }
}

/** Case and accents do not count in the title search. */
private fun fold(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

/** How long a removed song can be put back (D-58). */
private const val UNDO_MS = 6_000L
