// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
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

    /**
     * What arrives is sorted by action and scheme (architecture A-24, assessment SP-37 condition 7): a library
     * file goes to the library screen's import; a join link (`https` or `zangbundel:`) to the join; anything
     * else is ignored.
     */
    private fun handle(intent: Intent?) {
        intent ?: return
        val container = (application as ZangbundelApp).container
        libraryUri(intent)?.let { uri ->
            container.welcomed = true
            container.pendingLibrary.value = uri
            return
        }
        val data = intent.takeIf { it.action == Intent.ACTION_VIEW }?.data ?: return
        if (data.scheme != "https" && data.scheme != "zangbundel") return
        val link = JoinLink.parse(data.toString(), container.crypto)
        if (link == null) {
            Toast.makeText(this, R.string.join_link_invalid, Toast.LENGTH_LONG).show()
            return
        }
        container.welcomed = true
        if (BluetoothAccess.ready(this)) container.gathering.join(link) else container.gathering.pendingLink.value = link
    }

    /**
     * A library file handed over by Open (VIEW) or Share (SEND, one `EXTRA_STREAM`) (FR-LIB-18; SP-37 conditions 1
     * and 6): a `content:` URI only, never the app's own FileProvider; every other extra is ignored. The bytes are
     * read later, with the intent's temporary grant and the picker's cap (condition 2, 3).
     */
    private fun libraryUri(intent: Intent): Uri? {
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT }
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        } ?: return null
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null
        if (uri.authority == "$packageName.files") return null
        return uri
    }
}
