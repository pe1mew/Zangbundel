// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import nl.pe1mew.zangbundel.app.diagnostics.Diagnostics
import nl.pe1mew.zangbundel.app.diagnostics.RadioCheckScreen
import nl.pe1mew.zangbundel.app.gathering.Gathering
import nl.pe1mew.zangbundel.app.ui.about.AboutScreen
import nl.pe1mew.zangbundel.app.ui.firstuse.IntroScreen
import nl.pe1mew.zangbundel.app.ui.firstuse.PrivacyScreen
import nl.pe1mew.zangbundel.app.ui.firstuse.WelcomeScreen
import nl.pe1mew.zangbundel.app.ui.gathering.LeaderScreen
import nl.pe1mew.zangbundel.app.ui.gathering.ResumeScreen
import nl.pe1mew.zangbundel.app.ui.settings.SettingsScreen
import nl.pe1mew.zangbundel.app.ui.gathering.SingerScreen
import nl.pe1mew.zangbundel.app.ui.gathering.StartScreen
import nl.pe1mew.zangbundel.app.ui.gathering.SummaryScreen
import nl.pe1mew.zangbundel.app.ui.library.LibraryScreen
import nl.pe1mew.zangbundel.app.ui.memories.MemoriesScreen
import nl.pe1mew.zangbundel.app.ui.memories.MemoryScreen
import nl.pe1mew.zangbundel.app.ui.setlists.LibrarySetlistScreen
import nl.pe1mew.zangbundel.app.ui.setlists.LibrarySetlistViewModel
import nl.pe1mew.zangbundel.app.ui.setlists.SetlistScreen
import nl.pe1mew.zangbundel.app.ui.setlists.SetlistViewModel
import nl.pe1mew.zangbundel.app.ui.setlists.SetlistsScreen
import nl.pe1mew.zangbundel.app.ui.songbook.SongScreen
import nl.pe1mew.zangbundel.app.ui.songbook.SongViewModel
import nl.pe1mew.zangbundel.app.ui.songbook.SongbookScreen
import nl.pe1mew.zangbundel.app.ui.theme.ZangbundelTheme

/** Compose root: theme and navigation graph. Screens follow design/guiDesign.md; home, gatherings (start, leader, singer), songbook, song, setlists and library exist yet. */
@Composable
fun ZangbundelApp() {
    ZangbundelTheme {
        val navController = rememberNavController()
        val context = LocalContext.current
        val gathering = remember { (context.applicationContext as nl.pe1mew.zangbundel.app.ZangbundelApp).container.gathering }
        val mode by gathering.mode.collectAsState()
        // The gathering decides the screen: leading or singing opens its page, the end returns home (D-25, D-26, D-34, D-37).
        LaunchedEffect(mode::class) {
            val target = when (mode) {
                is Gathering.Mode.Leading -> Routes.LEADER
                is Gathering.Mode.Singing -> Routes.SINGER
                Gathering.Mode.Idle -> Routes.HOME
            }
            // Before the graph exists (the start is still being chosen) the start itself is right for an idle app.
            if (navController.currentDestination == null) return@LaunchedEffect
            if (navController.currentDestination?.route != target) {
                // D-67: a gathering resumed from the resume screen has no home below it yet; put it there first.
                if (target != Routes.HOME && runCatching { navController.getBackStackEntry(Routes.HOME) }.isFailure) {
                    navController.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } }
                }
                // D-69: a leader who just ended sees the summary first, on top of home.
                if (target == Routes.HOME && gathering.summary.value != null) navController.navigate(Routes.SUMMARY) { popUpTo(Routes.HOME) }
                else if (target == Routes.HOME) navController.popBackStack(Routes.HOME, inclusive = false)
                else navController.navigate(target) { popUpTo(Routes.HOME) }
            }
        }
        // D-05: the welcome screen once per process; D-07: the introduction until it was finished once; D-67: after
        // the app died while leading, the resume screen in place of the welcome screen. Nothing shows until that is known.
        val container = remember { (context.applicationContext as nl.pe1mew.zangbundel.app.ZangbundelApp).container }
        val fresh = remember { gathering.mode.value == Gathering.Mode.Idle && !container.welcomed }
        var offer by remember { mutableStateOf<Gathering.ResumeOffer?>(null) }
        var start by remember { mutableStateOf(if (fresh) null else Routes.HOME) }
        LaunchedEffect(Unit) {
            if (start == null) {
                offer = gathering.resumeOffer()
                start = if (offer != null) Routes.RESUME else Routes.WELCOME
            }
        }
        val first = start ?: return@ZangbundelTheme
        NavHost(navController = navController, startDestination = first) {
            composable(Routes.RESUME) {
                offer?.let { o ->
                    ResumeScreen(
                        o, now = System::currentTimeMillis,
                        onResume = { container.welcomed = true; gathering.resume() },
                        onRestart = {
                            gathering.discardResume()
                            container.welcomed = true
                            navController.navigate(Routes.HOME) { popUpTo(Routes.RESUME) { inclusive = true } }
                        },
                    )
                }
            }
            composable(Routes.WELCOME) {
                WelcomeScreen { firstUse -> navController.navigate(if (firstUse) Routes.INTRO else Routes.HOME) { popUpTo(Routes.WELCOME) { inclusive = true } } }
            }
            composable(Routes.INTRO) {
                IntroScreen(onPrivacy = { navController.navigate(Routes.PRIVACY) }, onDone = { navController.navigate(Routes.HOME) { popUpTo(Routes.INTRO) { inclusive = true } } })
            }
            composable(Routes.PRIVACY) { PrivacyScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(onAllRemoved = { navController.navigate(Routes.INTRO) { popUpTo(0) { inclusive = true } } })
            }
            composable(Routes.ABOUT) { AboutScreen(onPrivacy = { navController.navigate(Routes.PRIVACY) }) }
            composable(Routes.HOME) { HomeScreen { route -> navController.navigate(route) } }
            composable(Routes.SONGBOOK) {
                SongbookScreen(
                    onLibraries = { navController.navigate(Routes.LIBRARY) },
                    onSetlists = { navController.navigate(Routes.SETLISTS) },
                    onSong = { l, s -> navController.navigate("song/$l/$s") },
                    onDone = { navController.popBackStack() },
                )
            }
            composable(
                Routes.SONG,
                arguments = listOf(navArgument(SongViewModel.LIBRARY) { type = NavType.StringType }, navArgument(SongViewModel.SONG) { type = NavType.StringType }),
            ) { SongScreen() }
            composable(Routes.LIBRARY) { LibraryScreen() }
            composable(Routes.START) { StartScreen() }
            composable(Routes.MEMORIES) { MemoriesScreen(onOpen = { navController.navigate("memory/$it") }) }
            composable(Routes.MEMORY, arguments = listOf(navArgument("uid") { type = NavType.LongType })) { entry ->
                MemoryScreen(entry.arguments!!.getLong("uid"), onSong = { l, s -> navController.navigate("song/$l/$s") }, onDeleted = { navController.popBackStack() })
            }
            composable(Routes.LEADER) { LeaderScreen() }
            composable(Routes.SUMMARY) { SummaryScreen(onDone = { navController.popBackStack(Routes.HOME, inclusive = false) }) }
            composable(Routes.SINGER) { SingerScreen() }
            composable(Routes.SETLISTS) {
                SetlistsScreen(onOpen = { navController.navigate("setlist/$it") }, onOpenShipped = { navController.navigate("libsetlist/${it.libraryId}/${it.setlistId}") })
            }
            composable(
                Routes.LIBRARY_SETLIST,
                arguments = listOf(navArgument(LibrarySetlistViewModel.LIBRARY) { type = NavType.StringType }, navArgument(LibrarySetlistViewModel.SETLIST) { type = NavType.StringType }),
            ) {
                // The copy replaces the read-only view, so back from the copy returns to the setlists.
                LibrarySetlistScreen(onDuplicated = { navController.navigate("setlist/$it") { popUpTo(Routes.LIBRARY_SETLIST) { inclusive = true } } })
            }
            composable(Routes.SETLIST, arguments = listOf(navArgument(SetlistViewModel.UID) { type = NavType.LongType })) {
                SetlistScreen(onAddSongs = { navController.navigate(Routes.SONGBOOK) })
            }
            if (Diagnostics.AVAILABLE) {
                composable(Diagnostics.ROUTE) { RadioCheckScreen() }
            }
        }
    }
}

object Routes {
    const val WELCOME = "welcome"
    const val RESUME = "resume"
    const val INTRO = "intro"
    const val PRIVACY = "privacy"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val HOME = "home"
    const val SONGBOOK = "songbook"
    /** Library and song ids have id syntax (song-format 3.2), so they are safe as path segments. */
    const val SONG = "song/{${SongViewModel.LIBRARY}}/{${SongViewModel.SONG}}"
    const val LIBRARY = "library"
    const val START = "start"
    const val MEMORIES = "memories"
    const val MEMORY = "memory/{uid}"
    const val LEADER = "leader"
    const val SUMMARY = "summary"
    const val SINGER = "singer"
    const val SETLISTS = "setlists"
    const val SETLIST = "setlist/{${SetlistViewModel.UID}}"
    /** Library and setlist ids have id syntax (song-format 3.2, 13.2). */
    const val LIBRARY_SETLIST = "libsetlist/{${LibrarySetlistViewModel.LIBRARY}}/{${LibrarySetlistViewModel.SETLIST}}"
}
