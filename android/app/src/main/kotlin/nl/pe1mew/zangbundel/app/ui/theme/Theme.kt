// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Palette of design/guiDesign.md section 9. */
object Palette {
    val NightBlueDark = Color(0xFF0B2545)
    val NightBlue = Color(0xFF13315C)
    val FireOrange = Color(0xFFFF8A3D)
    val FireOrangeLight = Color(0xFFFFB36B)
    val SkyBlue = Color(0xFF4FC3F7)
    val OffWhite = Color(0xFFCFE6FF)
    val White = Color(0xFFFFFFFF)
    val Black = Color(0xFF000000)
}

/** Configuration screens: night blue with orange primary. The lyric view has its own day/night themes (FR-DISP-01). */
private val ConfigurationScheme = darkColorScheme(
    primary = Palette.FireOrange,
    onPrimary = Palette.NightBlueDark,
    secondary = Palette.FireOrangeLight,
    onSecondary = Palette.NightBlueDark,
    tertiary = Palette.SkyBlue,
    background = Palette.NightBlueDark,
    onBackground = Palette.White,
    surface = Palette.NightBlue,
    onSurface = Palette.White,
    onSurfaceVariant = Palette.OffWhite,
)

/** Day theme of the lyric view: black on white. */
val LyricDayScheme = lightColorScheme(
    background = Palette.White,
    onBackground = Palette.Black,
    surface = Palette.White,
    onSurface = Palette.Black,
    primary = Palette.FireOrange,
    onPrimary = Palette.NightBlueDark,
)

/** Night theme of the lyric view: white on black, no bright areas (FR-DISP-03). */
val LyricNightScheme = darkColorScheme(
    background = Palette.Black,
    onBackground = Palette.White,
    surface = Palette.Black,
    onSurface = Palette.White,
    primary = Palette.FireOrange,
    onPrimary = Palette.NightBlueDark,
)

@Composable
fun ZangbundelTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ConfigurationScheme, content = content)
}
