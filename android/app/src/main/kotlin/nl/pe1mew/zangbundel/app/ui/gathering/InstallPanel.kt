// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.gathering

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.core.protocol.beacon.JoinLink

/**
 * The install panel of guiDesign D-83 [FR-DIST-01 to FR-DIST-04]: black on white with the brightness
 * raised, a QR code of the install page (the join page without join data), its address as text, the note
 * that installing needs internet and is best done before the trip, and *Link delen* for the share sheet.
 * A tap outside the button closes it. Nothing about the phone or a gathering is in the address (SP-28).
 */
@Composable
fun InstallPanel(onClose: () -> Unit) {
    val context = LocalContext.current
    FullBrightness()
    Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp).background(Color.White).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.install_title), color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            QrCode(INSTALL_URL)
            Text(INSTALL_URL, color = Color.Black, fontSize = 16.sp, textAlign = TextAlign.Center)
            Text(stringResource(R.string.install_note), color = PanelGrey, fontSize = 14.sp, textAlign = TextAlign.Center)
            val chooser = stringResource(R.string.install_share)
            TextButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, INSTALL_URL)
                context.startActivity(Intent.createChooser(send, chooser))
            }) { Text(chooser, color = PanelLinkOrange, fontSize = 16.sp) }
        }
    }
}

/** The join page without join data shows only how to install (BLE protocol 6.5, `docs/j/index.html`). */
const val INSTALL_URL = JoinLink.HTTPS_PREFIX
