// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.gathering

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.app.ui.theme.Palette
import nl.pe1mew.zangbundel.core.protocol.beacon.JoinLink

/**
 * The install panel of guiDesign D-83 [FR-DIST-01 to FR-DIST-04]: black on white with the brightness
 * raised, a QR code of the install page (the join page without join data), its address as text, the note
 * that installing needs internet and is best done before the trip, and *Link delen* for the share sheet.
 * Under it *Delen zonder internet* (D-84, FR-DIST-05) opens the steps for the receiving phone and *Delen*,
 * which hands this install's own package to the share sheet; it is absent on a split install.
 * A tap outside the buttons closes it. Nothing about the phone or a gathering is in the address (SP-28).
 */
@Composable
fun InstallPanel(onClose: () -> Unit) {
    val context = LocalContext.current
    val shareable = remember { AppPackage.shareable(context) }
    var offline by remember { mutableStateOf(false) }
    if (offline) {
        OfflineSteps(onBack = { offline = false }, onClose = onClose)
        return
    }
    FullBrightness()
    Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp).background(Color.White).verticalScroll(rememberScrollState()).padding(16.dp),
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
            if (shareable) TextButton(onClick = { offline = true }) { Text(stringResource(R.string.install_offline), color = PanelLinkOrange, fontSize = 16.sp) }
        }
    }
}

/**
 * D-84: the steps for the receiving phone, in the order they happen [FR-DIST-05], then *Delen*. Nothing
 * here is scanned, so it keeps the app's night colours and the normal brightness (owner, 2026-10-27).
 */
@Composable
private fun OfflineSteps(onBack: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var copying by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp).background(Palette.NightBlueDark, RoundedCornerShape(16.dp)).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.install_offline), color = Palette.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.install_offline_intro, AppPackage.megabytes(context)), color = Palette.OffWhite, fontSize = 16.sp)
            listOf(R.string.install_offline_step1, R.string.install_offline_step2, R.string.install_offline_step3, R.string.install_offline_step4).forEachIndexed { i, step ->
                Row(Modifier.fillMaxWidth()) {
                    Text("${i + 1}.", color = Palette.FireOrange, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(step), color = Palette.White, fontSize = 16.sp)
                }
            }
            val chooser = stringResource(R.string.install_offline_share)
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.install_offline_back), color = Palette.OffWhite, fontSize = 16.sp) }
                TextButton(
                    onClick = {
                        copying = true
                        scope.launch { runCatching { AppPackage.share(context, chooser) }; copying = false }
                    },
                    enabled = !copying,
                ) { Text(chooser, color = Palette.FireOrange, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** The join page without join data shows only how to install (BLE protocol 6.5, `docs/j/index.html`). */
const val INSTALL_URL = JoinLink.HTTPS_PREFIX
