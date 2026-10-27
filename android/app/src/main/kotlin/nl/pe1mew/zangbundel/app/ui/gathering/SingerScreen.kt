// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.gathering

import android.content.Context
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.app.ZangbundelApp
import nl.pe1mew.zangbundel.app.gathering.Gathering
import nl.pe1mew.zangbundel.app.ui.LYRIC_SIZES_SP
import nl.pe1mew.zangbundel.app.ui.LyricBody
import nl.pe1mew.zangbundel.app.ui.LyricColors
import nl.pe1mew.zangbundel.app.ui.lyricLineHeight
import nl.pe1mew.zangbundel.app.ui.singer.Blocks
import nl.pe1mew.zangbundel.app.ui.singer.ReceptionIsland
import nl.pe1mew.zangbundel.app.ui.theme.Palette
import nl.pe1mew.zangbundel.platform.storage.AppSettings

/**
 * The singer's lyric view (guiDesign D-27, D-29, D-47) in the night theme: the 24 dp header strip with
 * the gathering name and the connection state (D-20), then the songs the leader pushed in this
 * session, in the order pushed, each behind a divider with its title and position; a new push
 * scrolls the page to it. Leaving asks first (D-34); once ended, the page stays readable and the strip
 * turns into an orange bar that leaves on a tap (D-34 as amended in v0.19). The strip carries the fingerprint
 * (D-36); a join from a QR code shows the joining screen of D-35 until the leader accepts, and a refused join
 * code asks again (D-26). The reception grid of D-81 is a hidden feature since v0.30: a long press on
 * the strip opens it, a tap closes it; it never shows by itself. The round buttons of D-30 (previous
 * song, songlist, next song) sit at the bottom right and open the songlist sheet of D-31, which ends with
 * *Kampvuur verlaten*. A tap on the strip or a long press on the lyrics shows the toolbar of D-17 (theme, text
 * size, song info, brightness, keep the screen on, follow the leader); a tap on the lyrics scrolls one line,
 * down on the lower half and up on the upper half (FR-SING-11). Follow the leader (FR-SING-12, D-70) takes
 * the page to each pushed song; a manual scroll pauses it until the next push or the switch.
 */
@Composable
fun SingerScreen() {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as ZangbundelApp).container }
    val gathering = container.gathering
    val settings = container.storage.settings
    val mode by gathering.mode.collectAsState()
    val m = mode as? Gathering.Mode.Singing ?: return
    // D-17: the toolbar's settings; theme, size and keep-screen-on are remembered per device, brightness is not.
    val night by settings.lyricNight.collectAsState(initial = true)
    val sizeStep by settings.lyricSize.collectAsState(initial = AppSettings.LYRIC_SIZE_DEFAULT)
    val keepOn by settings.keepScreenOn.collectAsState(initial = true)
    var brightness by remember { mutableStateOf<Float?>(null) }
    var toolbar by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<Gathering.ReceivedSong?>(null) }
    val colors = if (night) LyricColors.Night else LyricColors.Day
    val textSize = LYRIC_SIZES_SP[sizeStep]
    val linePx = with(LocalDensity.current) { lyricLineHeight(textSize).sp.toPx() }
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    var confirmLeave by remember { mutableStateOf(false) }
    var islandHeight by remember { mutableIntStateOf(0) }
    // D-81 (v0.30): a hidden feature, open after a long press on the strip until a tap.
    var requested by remember { mutableStateOf(false) }
    val showIsland = requested && m.reception.isNotEmpty()
    // D-30, D-31: the round buttons, dimmed after 3 s without a touch, and the songlist sheet.
    var touched by remember { mutableLongStateOf(0L) }
    var dimmed by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    val at by remember { derivedStateOf { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset } }
    // FR-SING-12: [followOn] is the switch; [paused] is set by any manual scroll and cleared by the next push.
    var followOn by remember { mutableStateOf(true) }
    var paused by remember { mutableStateOf(false) }
    val dragged by list.interactionSource.collectIsDraggedAsState()
    val currentRef = m.current?.let { m.songs.getOrNull(it)?.ref }
    val ended = m.connection == Gathering.Connection.ENDED
    val joining = m.viaQr && m.connection == Gathering.Connection.CONNECTING
    KeepScreenOn(keepOn)
    ScreenBrightness(brightness)
    LightSystemBars(!night && !joining)
    BackHandler { if (ended || joining) scope.launch { gathering.leave() } else confirmLeave = true }
    // D-70: keyed on the song, not its index, so a song sung earlier and placed above it does not move the page.
    LaunchedEffect(currentRef) { paused = false; if (followOn) m.current?.let { list.animateScrollToItem(it) } }
    LaunchedEffect(dragged) { if (dragged) paused = true }
    LaunchedEffect(toolbar, touched) { if (toolbar) { delay(TOOLBAR_MS); toolbar = false } }
    if (m.needsCode) CodeDialog(m.name, wrong = true, onCode = { gathering.enterCode(it) }, onDismiss = { scope.launch { gathering.leave() } })
    if (joining) {
        JoiningScreen(m, gathering::now) { scope.launch { gathering.leave() } }
        return
    }

    // Every touch on the page counts for the round buttons' dimming and the toolbar's 3 s, the buttons' own included.
    Box(Modifier.fillMaxSize().onAnyTouch { touched++; dimmed = false }) {
    Column(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val state = stringResource(
            when (m.connection) {
                Gathering.Connection.CONNECTING -> R.string.singer_connecting
                Gathering.Connection.CONNECTED -> if (m.cleared) R.string.singer_cleared else R.string.singer_connected
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
            // D-36: only the name gives way when the strip is narrow, never the fingerprint or the state.
            Row(
                Modifier.fillMaxWidth().height(24.dp).pointerInput(Unit) { detectTapGestures(onTap = { toolbar = !toolbar }, onLongPress = { requested = true }) }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(m.name, color = colors.secondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text(" · " + m.fingerprint + " · " + state, color = colors.secondary, fontSize = 13.sp, maxLines = 1, softWrap = false)
            }
        }
        // D-81: while the island is shown, the page ends with room for it, so it never covers the line being sung.
        val room = if (showIsland) with(LocalDensity.current) { islandHeight.toDp() } else 0.dp
        // FR-SING-11: a tap scrolls one line, down on the lower half, up on the upper; a long press shows the toolbar (D-17).
        val lyricGestures = Modifier.pointerInput(linePx) {
            detectTapGestures(
                onTap = { o -> paused = true; scope.launch { list.animateScrollBy(if (o.y > size.height / 2) linePx else -linePx) } },
                onLongPress = { toolbar = true },
            )
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().then(lyricGestures), state = list, contentPadding = PaddingValues(bottom = room)) {
            if (m.songs.isEmpty()) item { Text(stringResource(R.string.singer_waiting), color = colors.secondary, modifier = Modifier.padding(16.dp)) }
            itemsIndexed(m.songs, key = { _, s -> s.ref.library.value + "/" + s.ref.song.value }) { i, s ->
                Column {
                    if (i > 0) HorizontalDivider(color = colors.divider)
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (i == m.current) Box(Modifier.width(4.dp).height(24.dp).background(Palette.FireOrange))
                        Spacer(Modifier.width(12.dp))
                        Text("${i + 1} · ${s.title}", color = colors.lyric, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        // D-29 (v0.21): 1 cm showing how much of this song is in, while it is still coming in.
                        if (i == m.current && s.song == null && !s.libraryOnly) {
                            Spacer(Modifier.width(12.dp))
                            Blocks(m.collecting.orEmpty(), Modifier.width(SONG_BAR_WIDTH).height(8.dp), emptyAsPending = true)
                        }
                    }
                    val song = s.song
                    when {
                        song != null -> LyricBody(song, colors, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp), textSize)
                        s.libraryOnly -> Text(stringResource(R.string.singer_library_only, s.ref.library.value), color = colors.secondary, modifier = Modifier.padding(16.dp))
                        else -> Text(stringResource(R.string.singer_receiving), color = colors.secondary, modifier = Modifier.padding(16.dp))
                    }
                }
            }
            // D-29 (v0.24, the owner's request): the page runs on below the last song, so a song set Nu scrolls to the top of
            // the screen and can be scrolled up until only its last line is left, instead of resting against the bottom.
            if (m.songs.isNotEmpty()) item(key = "end") { Spacer(Modifier.fillParentMaxHeight(TAIL_FRACTION)) }
        }
    }
    // D-81: the reception island at the very bottom; a tap closes it.
    if (showIsland) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            ReceptionIsland(
                m.reception,
                titleOf = { ref -> m.songs.firstOrNull { it.ref == ref }?.title },
                onDismiss = { requested = false },
                modifier = Modifier.onSizeChanged { islandHeight = it.height },
            )
        }
    }
    if (m.songs.isNotEmpty()) {
        val (index, offset) = at
        val last = m.songs.lastIndex
        val shown = index.coerceAtMost(last)
        val islandDp = if (showIsland) with(LocalDensity.current) { islandHeight.toDp() } else 0.dp
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.BottomEnd) {
            RoundButtons(
                touched = touched, dimmed = dimmed, onDim = { dimmed = true }, day = !night,
                prevEnabled = index > 0 || offset > 0,
                // D-30: next is disabled on the most recently pushed song.
                nextEnabled = index < last,
                onPrev = { paused = true; scope.launch { list.animateScrollToItem(if (offset > 0 && index <= last) shown else (shown - 1).coerceAtLeast(0)) } },
                onList = { sheet = true },
                onNext = { paused = true; scope.launch { list.animateScrollToItem((shown + 1).coerceAtMost(last)) } },
                modifier = Modifier.padding(end = 16.dp, bottom = 16.dp + islandDp),
            )
        }
    }
    if (toolbar && !ended) {
        val inView = m.songs.getOrNull(at.first)
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 24.dp), contentAlignment = Alignment.TopCenter) {
            SingerToolbar(
                night = night, onTheme = { scope.launch { settings.setLyricNight(!night) } },
                sizeStep = sizeStep, sizeSteps = AppSettings.LYRIC_SIZES, onSize = { scope.launch { settings.setLyricSize(it) } },
                onInfo = inView?.song?.let { { toolbar = false; info = inView } },
                brightness = brightness ?: systemBrightness(context), onBrightness = { brightness = it },
                keepOn = keepOn, onKeepOn = { scope.launch { settings.setKeepScreenOn(it) } },
                follow = followOn && !paused,
                onFollow = { on ->
                    followOn = on
                    paused = false
                    if (on) m.current?.let { scope.launch { list.animateScrollToItem(it) } }
                },
            )
        }
    }
    }
    info?.let { s -> s.song?.let { SongInfoSheet(it.meta, s.library, onDismiss = { info = null }) } }
    if (sheet) SongSheet(
        rows = m.songs.mapIndexed { i, s -> SheetRow(s.title, current = i == m.current, sung = i != m.current) },
        onPick = { i -> sheet = false; paused = true; scope.launch { list.animateScrollToItem(i) } },
        onDismiss = { sheet = false },
        footer = stringResource(R.string.sheet_leave),
        onFooter = { sheet = false; if (ended) scope.launch { gathering.leave() } else confirmLeave = true },
    )
    if (confirmLeave) AlertDialog(
        onDismissRequest = { confirmLeave = false },
        title = { Text(stringResource(R.string.singer_leave_title)) },
        text = { Text(stringResource(R.string.singer_leave_text)) },
        confirmButton = { TextButton(onClick = { confirmLeave = false; scope.launch { gathering.leave() } }) { Text(stringResource(R.string.singer_leave)) } },
        dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.singer_stay)) } },
    )
}

/** The share of the page height left below the last song: enough to scroll it to the top, with its last line still in view. */
private const val TAIL_FRACTION = 0.85f

/** About one centimetre: 160 dp are an inch. */
internal val SONG_BAR_WIDTH = 63.dp

/** How long the toolbar of D-17 stays after the last touch. */
private const val TOOLBAR_MS = 3_000L

/** Gathering screens keep the screen on while shown (FR-DISP-07), unless the singer switched it off in the toolbar (D-17). */
@Composable
internal fun KeepScreenOn(on: Boolean = true) {
    val view = LocalView.current
    DisposableEffect(view, on) {
        view.keepScreenOn = on
        onDispose { view.keepScreenOn = false }
    }
}

/** FR-DISP-08: the toolbar's brightness for this window while the lyric view is shown; null leaves the system's. */
@Composable
private fun ScreenBrightness(level: Float?) {
    val window = LocalActivity.current?.window ?: return
    DisposableEffect(window, level) {
        window.attributes = window.attributes.also { it.screenBrightness = level ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        onDispose { window.attributes = window.attributes.also { it.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
    }
}

/** D-15: the system bars follow the theme, dark icons on the day theme's white. */
@Composable
private fun LightSystemBars(light: Boolean) {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, light) {
        val bars = WindowCompat.getInsetsController(window, view)
        val status = bars.isAppearanceLightStatusBars
        val navigation = bars.isAppearanceLightNavigationBars
        bars.isAppearanceLightStatusBars = light
        bars.isAppearanceLightNavigationBars = light
        onDispose {
            bars.isAppearanceLightStatusBars = status
            bars.isAppearanceLightNavigationBars = navigation
        }
    }
}

/** Where the slider starts: the system's brightness, which an app may read without a permission. */
private fun systemBrightness(context: Context): Float =
    (Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f).coerceIn(BRIGHTNESS_MIN, 1f)
