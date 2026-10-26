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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
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
 * The join QR screen (guiDesign D-57, FR-GATH-19, FR-DIST-02): the code black on a white panel, as large
 * as the screen allows, brightness raised; the name, the fingerprint and, with a spoken join code, the
 * code as text below it (never in QR-only mode, FR-GATH-21). A tap anywhere closes it and restores the
 * brightness. ZXing encodes only (architecture A-15); level M as ble-protocol 6.5 sizes it.
 */
@Composable
fun JoinQrScreen(m: Gathering.Mode.Leading, onClose: () -> Unit) {
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        val window = activity?.window
        val before = window?.attributes?.screenBrightness
        window?.attributes = window?.attributes?.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        onDispose { if (window != null && before != null) window.attributes = window.attributes.apply { screenBrightness = before } }
    }
    val modules = remember(m.joinLink) {
        val matrix = QRCodeWriter().encode(m.joinLink, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2))
        Array(matrix.height) { y -> BooleanArray(matrix.width) { x -> matrix.get(x, y) } }
    }
    Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp).background(Color.White).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
                val cell = size.width / modules.size
                for (y in modules.indices) for (x in modules[y].indices) {
                    if (modules[y][x]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
                }
            }
            Text(m.name, color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(m.fingerprint, color = Color.Black, fontSize = 18.sp)
            val code = (m.protection as? Gathering.Protection.Code)?.code
            if (code != null) {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.qr_code_text, code), color = Color.Black, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Text(stringResource(R.string.qr_hint), color = Color(0xFF444444), fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}
