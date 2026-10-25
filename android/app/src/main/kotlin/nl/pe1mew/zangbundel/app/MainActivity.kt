// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import nl.pe1mew.zangbundel.app.ui.ZangbundelApp as ZangbundelUi

/**
 * The single activity (architecture A-03): system splash (guiDesign D-05), edge to edge, Compose
 * navigation. AppCompatActivity so the per-app locale API works from API 26 (A-16).
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Every screen so far is dark (night blue, or white on black at the fire), so the system
        // bars always get light icons. Left to the default, Android takes the icon colour from the
        // phone's light or dark mode and draws dark icons on night blue in light mode.
        // The lyric view's day theme (black on white) will switch to SystemBarStyle.light.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent { ZangbundelUi() }
    }
}
