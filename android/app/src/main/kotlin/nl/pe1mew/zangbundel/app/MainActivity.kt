// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import nl.pe1mew.zangbundel.app.gathering.BluetoothAccess
import nl.pe1mew.zangbundel.core.protocol.beacon.JoinLink
import nl.pe1mew.zangbundel.app.ui.ZangbundelApp as ZangbundelUi

/**
 * The single activity (architecture A-03): system splash (guiDesign D-05), edge to edge, Compose
 * navigation. AppCompatActivity so the per-app locale API works from API 26 (A-16). A join link from the
 * camera app (FR-GATH-19, ble-protocol 6.5) skips the welcome screen and joins (guiDesign D-35).
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
        // Not again after a rotation: the link was handled, and a singer who left meanwhile stays out.
        if (savedInstanceState == null) handle(intent)
        setContent { ZangbundelUi() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val url = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString ?: return
        val container = (application as ZangbundelApp).container
        val link = JoinLink.parse(url, container.crypto)
        if (link == null) {
            Toast.makeText(this, R.string.join_link_invalid, Toast.LENGTH_LONG).show()
            return
        }
        container.welcomed = true
        if (BluetoothAccess.ready(this)) container.gathering.join(link) else container.gathering.pendingLink.value = link
    }
}
