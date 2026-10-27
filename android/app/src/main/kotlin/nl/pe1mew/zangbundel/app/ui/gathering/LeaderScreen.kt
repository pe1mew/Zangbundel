// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.gathering

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.app.ZangbundelApp
import nl.pe1mew.zangbundel.app.gathering.Gathering
import nl.pe1mew.zangbundel.app.ui.LyricBody
import nl.pe1mew.zangbundel.app.ui.LyricColors
import nl.pe1mew.zangbundel.app.ui.songbook.ColourSquare
import nl.pe1mew.zangbundel.app.ui.theme.Palette
import nl.pe1mew.zangbundel.core.songformat.Song
import nl.pe1mew.zangbundel.core.songformat.SongParser

/**
 * The leader's page (guiDesign D-55, D-56) in the night theme (D-15): the closed leader bar of D-61
 * with name, singers and time; the setlist block, the song being sung marked with the orange bar and
 * sung songs with a check, a tap scrolling to the song; then every song of the setlist behind its
 * separator with *Nu*. The bar carries the fingerprint of D-36 and opens, on a tap or a drag down, the
 * drawer of D-61 with the statistics of D-62, the rows that show how to join (D-57 as of v0.34) and
 * *Kampvuur beëindigen*. The ▲ ≡ ▼ buttons of D-57 sit at the bottom
 * right and move between separators; ≡ opens the leader's sheet of D-58 (v0.40): the setlist to jump,
 * move, remove and put back, and the songbook to add a song or sing it now. *Wis* on the current song's
 * separator clears it (D-65). A campfire without a setlist starts with *Kies een lied uit de zangbundel*.
 */
@Composable
fun LeaderScreen() {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as ZangbundelApp).container }
    val gathering = container.gathering
    val mode by gathering.mode.collectAsState()
    val m = mode as? Gathering.Mode.Leading ?: return
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    var confirmEnd by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf<Boolean?>(null) }   // null: closed; true: on the QR code
    var drawer by remember { mutableStateOf(false) }
    // D-57: the ▲ ≡ ▼ column, dimmed after 3 s without a touch, and the setlist sheet.
    var touched by remember { mutableLongStateOf(0L) }
    var dimmed by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<Int?>(null) }   // null: closed; else the tab it opens on (D-58)
    var songbook by remember { mutableStateOf<List<nl.pe1mew.zangbundel.platform.storage.SongbookEntry>>(emptyList()) }
    LaunchedEffect(sheet != null) { if (sheet != null && songbook.isEmpty()) songbook = runCatching { container.storage.songbook.entries() }.getOrDefault(emptyList()) }
    val at by remember { derivedStateOf { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset } }
    // The lyrics by setlist line, read once per line (D-58: lines come, go and move).
    var songs by remember { mutableStateOf<Map<Long, Song>>(emptyMap()) }
    LaunchedEffect(m.songs) {
        val known = songs
        songs = m.songs.mapNotNull { s ->
            known[s.line]?.let { s.line to it }
                ?: s.song?.let { e -> runCatching { container.storage.songbook.read(e.libraryId, e.directory, e.fileName) }.getOrNull()?.let { SongParser.parse(it).song }?.let { s.line to it } }
        }.toMap()
    }
    var now by remember { mutableLongStateOf(gathering.now()) }
    LaunchedEffect(Unit) { while (true) { now = gathering.now(); delay(1_000) } }
    KeepScreenOn()
    BackHandler { confirmEnd = true }
    BackHandler(enabled = drawer) { drawer = false }
    BackHandler(enabled = showJoin != null) { showJoin = null }

    Box(Modifier.fillMaxSize().onAnyTouch { touched++; dimmed = false }) {
        Column(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing)) {
            LeaderBar(m, now, onOpen = { drawer = true })
            Box(Modifier.weight(1f).fillMaxWidth()) {
                // D-61: a tap on the lyrics closes the drawer; the lyrics keep scrolling under it.
                LazyColumn(
                    Modifier.fillMaxSize().then(if (drawer) Modifier.pointerInput(Unit) { detectTapGestures(onTap = { drawer = false }) } else Modifier),
                    state = list,
                    // D-69 with D-57: room under the closing bar so it scrolls clear of the ▲ ≡ ▼ column.
                    contentPadding = PaddingValues(bottom = if (m.songs.isEmpty()) 0.dp else BUTTON_COLUMN_ROOM),
                ) {
                    item(key = "block") {
                        SetlistBlock(m) { i -> scope.launch { list.animateScrollToItem(1 + i) } }
                    }
                    itemsIndexed(m.songs, key = { _, s -> "song:${s.line}" }) { i, s ->
                        Column {
                            Separator(i, m, s, onClear = { scope.launch { gathering.clear() } }) { drawer = false; scope.launch { gathering.push(i) } }
                            val song = songs[s.line]
                            if (song != null) LyricBody(song, LyricColors.Night, Modifier.padding(horizontal = 16.dp, vertical = 16.dp))
                            else Text(stringResource(R.string.setlist_not_found), color = LyricColors.Night.secondary, modifier = Modifier.padding(16.dp))
                        }
                    }
                    // D-55 (v0.40): without a setlist the page starts by asking for a song.
                    if (m.songs.isEmpty()) item(key = "empty") {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(stringResource(R.string.leader_choose_song), color = LyricColors.Night.lyric, fontSize = 20.sp)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { sheet = 1 }, colors = ButtonDefaults.buttonColors(containerColor = Palette.FireOrange, contentColor = Palette.NightBlueDark)) {
                                Text(stringResource(R.string.leader_open_songbook), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    // D-69: the closing bar ends the setlist and the page.
                    else item(key = "closing") { ClosingBar { confirmEnd = true } }
                }
                DrawerSlot(drawer, m, now, onJoin = { qr -> drawer = false; showJoin = qr }, onEnd = { drawer = false; confirmEnd = true }, onClose = { drawer = false })
            }
        }
        // Items: 0 is the setlist block, 1 + i the separator of song i. The ≡ button opens the sheet also without songs (D-58).
        run {
            val (index, offset) = at
            val last = m.songs.size
            RoundButtons(
                touched = touched, dimmed = dimmed, onDim = { dimmed = true },
                prevEnabled = m.songs.isNotEmpty() && (index > 0 || offset > 0),
                nextEnabled = index < last,
                onPrev = { scope.launch { list.animateScrollToItem(if (offset > 0) index else (index - 1).coerceAtLeast(0)) } },
                onList = { sheet = 0 },
                onNext = { scope.launch { list.animateScrollToItem((index + 1).coerceAtMost(last)) } },
                modifier = Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
            )
        }
        showJoin?.let { qr -> JoinPanel(m, startWithQr = qr) { showJoin = null } }
    }
    sheet?.let { tab ->
        LeaderSheet(
            m, tab, songbook,
            onPick = { i -> sheet = null; if (i >= 0) scope.launch { list.animateScrollToItem(1 + i) } },
            onEdit = { rows -> scope.launch { gathering.edit(rows) } },
            onAdd = { e -> scope.launch { gathering.add(e) } },
            onSingNow = { e ->
                sheet = null
                scope.launch {
                    gathering.singNow(e)
                    (gathering.mode.value as? Gathering.Mode.Leading)?.current?.let { list.animateScrollToItem(1 + it) }
                }
            },
            onDismiss = { sheet = null },
        )
    }
    if (confirmEnd) AlertDialog(
        onDismissRequest = { confirmEnd = false },
        title = { Text(stringResource(R.string.leader_end_title)) },
        text = { Text(stringResource(R.string.leader_end_text)) },
        confirmButton = { TextButton(onClick = { confirmEnd = false; gathering.end() }) { Text(stringResource(R.string.leader_end)) } },
        dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

/** D-61: the drawer slides open from under the leader bar over the top of the page. */
@Composable
private fun DrawerSlot(open: Boolean, m: Gathering.Mode.Leading, now: Long, onJoin: (Boolean) -> Unit, onEnd: () -> Unit, onClose: () -> Unit) {
    AnimatedVisibility(open, enter = expandVertically(expandFrom = Alignment.Top), exit = shrinkVertically(shrinkTowards = Alignment.Top)) {
        LeaderDrawer(m, now, onJoin = onJoin, onEnd = onEnd, onClose = onClose)
    }
}

/**
 * The closed leader bar (D-61): one 32 dp line in the secondary colour, the fingerprint after the name
 * (D-36), and a small handle in the middle of its lower edge; a tap or a drag down opens the drawer.
 */
@Composable
private fun LeaderBar(m: Gathering.Mode.Leading, now: Long, onOpen: () -> Unit) {
    val seconds = ((now - m.startedAt) / 1000).coerceAtLeast(0)
    val time = "%d:%02d".format(seconds / 3600, (seconds / 60) % 60)
    val open = stringResource(R.string.drawer_open)
    Box(
        Modifier.fillMaxWidth().height(32.dp)
            .clickable(onClickLabel = open, onClick = onOpen)
            .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy > 8f) onOpen() } },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            Text(m.name, color = LyricColors.Night.secondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Text(" · " + m.fingerprint + " · " + pluralStringResource(R.plurals.leader_singers, m.singers, m.singers) + " · " + time,
                color = LyricColors.Night.secondary, fontSize = 13.sp, maxLines = 1, softWrap = false)
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp).size(width = 32.dp, height = 3.dp).background(LyricColors.Night.secondary.copy(alpha = 0.6f), CircleShape))
    }
}

/** The ▲ ≡ ▼ column's height with its margin: three 56 dp buttons, two 12 dp gaps, 16 dp below. */
private val BUTTON_COLUMN_ROOM = 208.dp

/** D-69: the closing bar after the last song, like a separator, with *Afsluiten*. */
@Composable
private fun ClosingBar(onEnd: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).background(Palette.NightBlue).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.leader_closing), color = Color.White, fontSize = 20.sp, modifier = Modifier.weight(1f))
        Button(
            onClick = onEnd,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Palette.FireOrange),
            border = BorderStroke(2.dp, Palette.FireOrange),
        ) { Text(stringResource(R.string.leader_end), fontWeight = FontWeight.Bold) }
    }
}

/** D-55: the setlist's titles with their colour squares; current with the orange bar, sung with a check. */
@Composable
private fun SetlistBlock(m: Gathering.Mode.Leading, onTap: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        m.songs.forEachIndexed { i, s ->
            Row(Modifier.fillMaxWidth().clickable { onTap(i) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(4.dp).height(24.dp).background(if (i == m.current) Palette.FireOrange else Color.Transparent))
                Spacer(Modifier.width(12.dp))
                s.song?.let { ColourSquare(it.colour) } ?: Spacer(Modifier.size(12.dp))
                Spacer(Modifier.width(12.dp))
                Text(s.title, color = if (s.song != null) LyricColors.Night.lyric else LyricColors.Night.secondary,
                    fontWeight = if (i == m.current) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                if (i in m.sung) Text("✓", color = LyricColors.Night.secondary, modifier = Modifier.padding(end = 16.dp))
            }
        }
    }
}

/**
 * D-56 (v0.21): about 1 cm whose fill is the share of singers the leader helped over GATT in the last
 * 5 s; green below half, orange from half, as a sign that the air is not reaching enough of them.
 */
@Composable
private fun HelpBar(share: Float, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.leader_help_description, (share * 100).toInt())
    Canvas(modifier.width(SONG_BAR_WIDTH).height(6.dp).semantics { contentDescription = description }) {
        drawRect(Palette.NightBlueDark)
        if (share > 0f) drawRect(if (share < 0.5f) Palette.ReceptionGreen else Palette.FireOrange, size = Size(size.width * share, size.height))
    }
}

/** D-56: 64 dp in night blue, colour square, position and title, state, and the round orange *Nu*. */
@Composable
private fun Separator(i: Int, m: Gathering.Mode.Leading, s: Gathering.LeaderSong, onClear: () -> Unit, onNow: () -> Unit) {
    val state = when {
        i == m.current -> stringResource(R.string.leader_state_current)
        i in m.sung -> stringResource(R.string.leader_state_sung)
        else -> null
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).background(Palette.NightBlue).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        s.song?.let { ColourSquare(it.colour) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${i + 1}/${m.songs.size} · ${s.title}", color = Color.White, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // D-56 (v0.23): the state on the left, the help bar on the same line at the right, against Nu.
            state?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(it, color = LyricColors.Night.secondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (i == m.current) HelpBar(m.help, Modifier.padding(start = 8.dp))
                }
            }
        }
        // D-65: *Wis*, left of Nu, only on the song being sung; hidden elsewhere.
        if (i == m.current) TextButton(onClick = onClear) { Text(stringResource(R.string.leader_clear), color = Palette.OffWhite) }
        Spacer(Modifier.width(8.dp))
        // D-56 (v0.19, the owner's request): the song the singers have now is the filled orange Nu; every other
        // Nu is black with an orange ring, a button not yet chosen.
        val now = i == m.current
        Button(
            onClick = onNow,
            enabled = s.song != null,
            shape = CircleShape,
            colors = if (now) ButtonDefaults.buttonColors(containerColor = Palette.FireOrange, contentColor = Palette.NightBlueDark)
            else ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Palette.FireOrange),
            border = if (now) null else BorderStroke(2.dp, Palette.FireOrange),
            modifier = Modifier.size(56.dp),
            contentPadding = PaddingValues(0.dp),
        ) { Text(stringResource(R.string.leader_now), fontWeight = FontWeight.Bold) }
    }
}
