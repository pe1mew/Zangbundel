// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import nl.pe1mew.zangbundel.app.diagnostics.BleTestScreen
import nl.pe1mew.zangbundel.app.diagnostics.Diagnostics
import nl.pe1mew.zangbundel.app.diagnostics.RadioCheckScreen
import nl.pe1mew.zangbundel.app.ui.library.LibraryScreen
import nl.pe1mew.zangbundel.app.ui.theme.ZangbundelTheme

/** Compose root: theme and navigation graph. Screens follow design/guiDesign.md; the home and library routes exist yet. */
@Composable
fun ZangbundelApp() {
    ZangbundelTheme {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = Routes.HOME) {
            composable(Routes.HOME) { HomeScreen { route -> navController.navigate(route) } }
            composable(Routes.LIBRARY) { LibraryScreen() }
            if (Diagnostics.AVAILABLE) {
                composable(Diagnostics.ROUTE) { RadioCheckScreen() }
                composable(Diagnostics.BLE_TEST_ROUTE) { BleTestScreen() }
            }
        }
    }
}

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
}
