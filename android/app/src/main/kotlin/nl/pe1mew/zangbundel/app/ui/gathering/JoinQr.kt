// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.gathering

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.app.gathering.Gathering

/**
 * The join panel (guiDesign D-57 as amended in v0.26 and v0.34, FR-GATH-18, FR-GATH-19, FR-DIST-02), opened
 * from the rows of the leader's drawer (D-61) on the QR code or on the words or join code: black on a
 * white panel, as large as the screen allows, brightness raised; a tap anywhere closes it and restores the
 * brightness. An open gathering shows the two words, a join code its digits with the words under them,
 * QR-only the QR code. The first two end in *QR-code tonen*, so late arrivals can still scan and pin the
 * leader's key; the QR code then also carries the spoken code as text (never in QR-only mode,
 * FR-GATH-21). ZXing encodes only (architecture A-15); level M as ble-protocol 6.5 sizes it.
 */
@Composable
fun JoinPanel(m: Gathering.Mode.Leading, startWithQr: Boolean, onClose: () -> Unit) {
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        val window = activity?.window
        val before = window?.attributes?.screenBrightness
        window?.attributes = window?.attributes?.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        onDispose { if (window != null && before != null) window.attributes = window.attributes.apply { screenBrightness = before } }
    }
    var qr by remember { mutableStateOf(startWithQr || m.protection is Gathering.Protection.QrOnly) }
    val code = (m.protection as? Gathering.Protection.Code)?.code
    Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp).background(Color.White).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (qr) QrCode(m.joinLink)
            Text(m.name, color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            when {
                qr -> {
                    Text(m.fingerprint, color = Color.Black, fontSize = 18.sp)
                    if (code != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.qr_code_text, code), color = Color.Black, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(stringResource(R.string.qr_hint), color = Grey, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
                code != null -> {
                    Text(stringResource(R.string.panel_code_title), color = Grey, fontSize = 18.sp)
                    Text(code, color = Color.Black, fontSize = 96.sp, fontWeight = FontWeight.Bold, letterSpacing = 8.sp, maxLines = 1, softWrap = false)
                    Text(m.fingerprint, color = Color.Black, fontSize = 24.sp)
                    Text(stringResource(R.string.panel_code_hint), color = Grey, fontSize = 14.sp, textAlign = TextAlign.Center)
                    TextButton(onClick = { qr = true }) { Text(stringResource(R.string.panel_show_qr), color = LinkOrange, fontSize = 16.sp) }
                }
                else -> {
                    Text(stringResource(R.string.panel_words_title), color = Grey, fontSize = 18.sp)
                    m.fingerprint.split('-').forEach { Text(it, color = Color.Black, fontSize = 56.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) }
                    Text(stringResource(R.string.panel_words_hint), color = Grey, fontSize = 14.sp, textAlign = TextAlign.Center)
                    TextButton(onClick = { qr = true }) { Text(stringResource(R.string.panel_show_qr), color = LinkOrange, fontSize = 16.sp) }
                }
            }
        }
    }
}

/** The join link as a QR code, black modules on the white panel. */
@Composable
private fun QrCode(link: String) {
    val modules = remember(link) {
        val matrix = QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2))
        Array(matrix.height) { y -> BooleanArray(matrix.width) { x -> matrix.get(x, y) } }
    }
    Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
        val cell = size.width / modules.size
        for (y in modules.indices) for (x in modules[y].indices) {
            if (modules[y][x]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

private val Grey = Color(0xFF444444)

/** Fire orange darkened for text on white (contrast about 5:1; the icon's orange is about 2.3:1 there). */
private val LinkOrange = Color(0xFFB34A00)
