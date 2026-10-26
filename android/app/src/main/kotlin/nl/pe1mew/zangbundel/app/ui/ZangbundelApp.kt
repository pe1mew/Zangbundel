// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import nl.pe1mew.zangbundel.app.diagnostics.BleTestScreen
import nl.pe1mew.zangbundel.app.diagnostics.Diagnostics
import nl.pe1mew.zangbundel.app.diagnostics.RadioCheckScreen
import nl.pe1mew.zangbundel.app.ui.library.LibraryScreen
import nl.pe1mew.zangbundel.app.ui.setlists.LibrarySetlistScreen
import nl.pe1mew.zangbundel.app.ui.setlists.LibrarySetlistViewModel
import nl.pe1mew.zangbundel.app.ui.setlists.SetlistScreen
import nl.pe1mew.zangbundel.app.ui.setlists.SetlistViewModel
import nl.pe1mew.zangbundel.app.ui.setlists.SetlistsScreen
import nl.pe1mew.zangbundel.app.ui.songbook.SongScreen
import nl.pe1mew.zangbundel.app.ui.songbook.SongViewModel
import nl.pe1mew.zangbundel.app.ui.songbook.SongbookScreen
import nl.pe1mew.zangbundel.app.ui.theme.ZangbundelTheme

/** Compose root: theme and navigation graph. Screens follow design/guiDesign.md; home, songbook, song, setlists and library exist yet. */
@Composable
fun ZangbundelApp() {
    ZangbundelTheme {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = Routes.HOME) {
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
                composable(Diagnostics.BLE_TEST_ROUTE) { BleTestScreen() }
            }
        }
    }
}

object Routes {
    const val HOME = "home"
    const val SONGBOOK = "songbook"
    /** Library and song ids have id syntax (song-format 3.2), so they are safe as path segments. */
    const val SONG = "song/{${SongViewModel.LIBRARY}}/{${SongViewModel.SONG}}"
    const val LIBRARY = "library"
    const val SETLISTS = "setlists"
    const val SETLIST = "setlist/{${SetlistViewModel.UID}}"
    /** Library and setlist ids have id syntax (song-format 3.2, 13.2). */
    const val LIBRARY_SETLIST = "libsetlist/{${LibrarySetlistViewModel.LIBRARY}}/{${LibrarySetlistViewModel.SETLIST}}"
}
