// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.gathering

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
 * separator with *Nu*. The bar carries the fingerprint of D-36; the round join button of D-57 (v0.26) sits
 * at the bottom left, clear of the *Nu* buttons, and opens the words, the join code or the QR code. The
 * drawer of D-61 and the ▲ ≡ ▼ buttons of D-57 are not built yet; *Afsluiten* sits in the bar for now.
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
    var showJoin by remember { mutableStateOf(false) }
    var songs by remember { mutableStateOf<Map<Int, Song>>(emptyMap()) }
    LaunchedEffect(m.songs) {
        songs = m.songs.withIndex().mapNotNull { (i, s) ->
            s.song?.let { e -> runCatching { container.storage.songbook.read(e.libraryId, e.directory, e.fileName) }.getOrNull()?.let { SongParser.parse(it).song }?.let { i to it } }
        }.toMap()
    }
    var now by remember { mutableLongStateOf(gathering.now()) }
    LaunchedEffect(Unit) { while (true) { now = gathering.now(); delay(1_000) } }
    KeepScreenOn()
    BackHandler { confirmEnd = true }
    BackHandler(enabled = showJoin) { showJoin = false }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing)) {
            LeaderBar(m, now, onEnd = { confirmEnd = true })
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list) {
                item(key = "block") {
                    SetlistBlock(m) { i -> scope.launch { list.animateScrollToItem(1 + i) } }
                }
                itemsIndexed(m.songs, key = { i, _ -> "song:$i" }) { i, s ->
                    Column {
                        Separator(i, m, s) { scope.launch { gathering.push(i) } }
                        val song = songs[i]
                        if (song != null) LyricBody(song, LyricColors.Night, Modifier.padding(horizontal = 16.dp, vertical = 16.dp))
                        else Text(stringResource(R.string.setlist_not_found), color = LyricColors.Night.secondary, modifier = Modifier.padding(16.dp))
                    }
                }
                if (m.songs.isEmpty()) item { Text(stringResource(R.string.leader_no_setlist), color = LyricColors.Night.secondary, modifier = Modifier.padding(16.dp)) }
            }
        }
        // D-57 (v0.26, the owner's request): the join button at the bottom left, where no *Nu* button is.
        JoinButton(m, onClick = { showJoin = true }, modifier = Modifier.align(Alignment.BottomStart).windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp))
        if (showJoin) JoinPanel(m) { showJoin = false }
    }
    if (confirmEnd) AlertDialog(
        onDismissRequest = { confirmEnd = false },
        title = { Text(stringResource(R.string.leader_end_title)) },
        text = { Text(stringResource(R.string.leader_end_text)) },
        confirmButton = { TextButton(onClick = { confirmEnd = false; gathering.end() }) { Text(stringResource(R.string.leader_end)) } },
        dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

/** The closed leader bar (D-61): one 32 dp line in the secondary colour, the fingerprint after the name (D-36). */
@Composable
private fun LeaderBar(m: Gathering.Mode.Leading, now: Long, onEnd: () -> Unit) {
    val seconds = ((now - m.startedAt) / 1000).coerceAtLeast(0)
    val time = "%d:%02d".format(seconds / 3600, (seconds / 60) % 60)
    Row(Modifier.fillMaxWidth().height(32.dp).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(m.name, color = LyricColors.Night.secondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Text(" · " + m.fingerprint + " · " + pluralStringResource(R.plurals.leader_singers, m.singers, m.singers) + " · " + time,
                color = LyricColors.Night.secondary, fontSize = 13.sp, maxLines = 1, softWrap = false)
        }
        TextButton(onClick = onEnd) { Text(stringResource(R.string.leader_end), fontSize = 13.sp, color = Palette.FireOrange) }
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
private fun Separator(i: Int, m: Gathering.Mode.Leading, s: Gathering.LeaderSong, onNow: () -> Unit) {
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
