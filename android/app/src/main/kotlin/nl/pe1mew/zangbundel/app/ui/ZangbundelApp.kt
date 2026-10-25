// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.app.diagnostics.Diagnostics
import nl.pe1mew.zangbundel.app.diagnostics.BleTestScreen
import nl.pe1mew.zangbundel.app.diagnostics.DiagnosticsEntry
import nl.pe1mew.zangbundel.app.diagnostics.RadioCheckScreen
import nl.pe1mew.zangbundel.app.ui.theme.ZangbundelTheme

/** Compose root: theme and navigation graph. Screens follow design/guiDesign.md; only the home route exists yet. */
@Composable
fun ZangbundelApp() {
    ZangbundelTheme {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = Routes.HOME) {
            composable(Routes.HOME) { HomeScreen(navController) }
            if (Diagnostics.AVAILABLE) {
                composable(Diagnostics.ROUTE) { RadioCheckScreen() }
                composable(Diagnostics.BLE_TEST_ROUTE) { BleTestScreen() }
            }
        }
    }
}

object Routes {
    const val HOME = "home"
}

@Composable
private fun HomeScreen(navController: NavController) {
    Scaffold { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.align(Alignment.Center),
            )
            // Debug and field-test builds only; the release stand-in draws nothing.
            if (Diagnostics.AVAILABLE) {
                Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                    DiagnosticsEntry { route -> navController.navigate(route) }
                }
            }
        }
    }
}
