// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.gathering

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.app.ZangbundelApp
import nl.pe1mew.zangbundel.app.gathering.Gathering
import nl.pe1mew.zangbundel.app.ui.LyricBody
import nl.pe1mew.zangbundel.app.ui.LyricColors
import nl.pe1mew.zangbundel.app.ui.singer.Blocks
import nl.pe1mew.zangbundel.app.ui.theme.Palette

/**
 * The singer's lyric view (guiDesign D-27, D-29, D-47) in the night theme: the 24 dp header strip with
 * the gathering name and the connection state (D-20), then the songs the leader pushed in this
 * session, in the order pushed, each behind a divider with its title and position; a new push
 * scrolls the page to it. Leaving asks first (D-34); once ended, the page stays readable and the strip
 * turns into an orange bar that leaves on a tap (D-34 as amended in v0.19). The round buttons, the songlist sheet, the toolbar and the fingerprint are not built yet.
 */
@Composable
fun SingerScreen() {
    val context = LocalContext.current
    val gathering = remember { (context.applicationContext as ZangbundelApp).container.gathering }
    val mode by gathering.mode.collectAsState()
    val m = mode as? Gathering.Mode.Singing ?: return
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    var confirmLeave by remember { mutableStateOf(false) }
    val ended = m.connection == Gathering.Connection.ENDED
    KeepScreenOn()
    BackHandler { if (ended) scope.launch { gathering.leave() } else confirmLeave = true }
    LaunchedEffect(m.current) { m.current?.let { list.animateScrollToItem(it) } }

    Column(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val state = stringResource(
            when (m.connection) {
                Gathering.Connection.CONNECTING -> R.string.singer_connecting
                Gathering.Connection.CONNECTED -> R.string.singer_connected
                Gathering.Connection.RECONNECTING -> R.string.singer_reconnecting
                Gathering.Connection.ENDED -> R.string.singer_ended
            },
        )
        if (ended) {
            // D-34 (v0.19, the owner's request): once ended, the strip turns orange with a warning sign, and a tap leaves.
            Row(
                Modifier.fillMaxWidth().height(48.dp).background(Palette.FireOrange).clickable { scope.launch { gathering.leave() } }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("⚠", color = Palette.NightBlueDark, fontSize = 18.sp)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.singer_ended_banner, m.name), color = Palette.NightBlueDark, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Box(Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                Text(m.name + " · " + state, color = LyricColors.Night.secondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list) {
            if (m.songs.isEmpty()) item { Text(stringResource(R.string.singer_waiting), color = LyricColors.Night.secondary, modifier = Modifier.padding(16.dp)) }
            itemsIndexed(m.songs, key = { _, s -> s.ref.library.value + "/" + s.ref.song.value }) { i, s ->
                Column {
                    if (i > 0) HorizontalDivider(color = LyricColors.Night.divider)
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (i == m.current) Box(Modifier.width(4.dp).height(24.dp).background(Palette.FireOrange))
                        Spacer(Modifier.width(12.dp))
                        Text("${i + 1} · ${s.title}", color = LyricColors.Night.lyric, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        // D-29 (v0.21): 1 cm showing how much of this song is in, while it is still coming in.
                        if (i == m.current && s.song == null && !s.libraryOnly) {
                            Spacer(Modifier.width(12.dp))
                            Blocks(m.collecting.orEmpty(), Modifier.width(SONG_BAR_WIDTH).height(8.dp), emptyAsPending = true)
                        }
                    }
                    val song = s.song
                    when {
                        song != null -> LyricBody(song, LyricColors.Night, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp))
                        s.libraryOnly -> Text(stringResource(R.string.singer_library_only, s.ref.library.value), color = LyricColors.Night.secondary, modifier = Modifier.padding(16.dp))
                        else -> Text(stringResource(R.string.singer_receiving), color = LyricColors.Night.secondary, modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
    if (confirmLeave) AlertDialog(
        onDismissRequest = { confirmLeave = false },
        title = { Text(stringResource(R.string.singer_leave_title)) },
        text = { Text(stringResource(R.string.singer_leave_text)) },
        confirmButton = { TextButton(onClick = { confirmLeave = false; scope.launch { gathering.leave() } }) { Text(stringResource(R.string.singer_leave)) } },
        dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.singer_stay)) } },
    )
}

/** About one centimetre: 160 dp are an inch. */
internal val SONG_BAR_WIDTH = 63.dp

/** Gathering screens keep the screen on while shown (FR-DISP-07); the toolbar switch of D-17 comes later. */
@Composable
internal fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
