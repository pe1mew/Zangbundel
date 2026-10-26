// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.app.ui.theme.Palette

/**
 * The configuration-screen family (guiDesign D-01, D-08 to D-14): the one backdrop photograph,
 * portrait or landscape by the resource system (D-13), under the scrim of D-14, a gradient from night
 * blue at 60 % at the top to black at 75 % at the bottom. Content keeps clear of the system bars.
 */
@Composable
fun ConfigurationScreen(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Palette.NightBlueDark)) {
        Image(painterResource(R.drawable.backdrop), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Palette.NightBlueDark.copy(alpha = 0.6f), Palette.Black.copy(alpha = 0.75f)))))
        // Text and icons are white on the scrim; outside a Surface they would default to black.
        CompositionLocalProvider(LocalContentColor provides Palette.White) {
            Box(Modifier.fillMaxSize().safeDrawingPadding(), content = content)
        }
    }
}

/** D-14: behind any block of body text, a solid panel of night blue at 85 %. */
fun Modifier.textPanel(): Modifier = background(Palette.NightBlueDark.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
