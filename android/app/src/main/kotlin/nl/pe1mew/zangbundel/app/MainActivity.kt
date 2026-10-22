// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app

import android.os.Bundle
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
        enableEdgeToEdge()
        setContent { ZangbundelUi() }
    }
}
