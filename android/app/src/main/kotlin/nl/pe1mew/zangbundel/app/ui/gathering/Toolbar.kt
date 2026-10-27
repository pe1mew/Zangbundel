// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.gathering

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.app.ui.LyricColors
import nl.pe1mew.zangbundel.app.ui.theme.Palette
import nl.pe1mew.zangbundel.core.songformat.SongMeta

/**
 * The singer's toolbar of guiDesign D-17, under the header strip: theme, text size smaller and larger
 * (D-18), song info (D-45), brightness (FR-DISP-08), keep the screen on (FR-DISP-07) and follow the
 * leader (FR-SING-12, D-70). Dark in both themes so it never dazzles; the caller hides it 3 s after the
 * last touch. *Passend* switches fit-to-width (D-28) for the orientation the phone is in. [onInfo] is null
 * while the song in view has not come in.
 */
@Composable
internal fun SingerToolbar(
    night: Boolean,
    onTheme: () -> Unit,
    sizeStep: Int,
    sizeSteps: Int,
    onSize: (Int) -> Unit,
    fit: Boolean,
    onFit: (Boolean) -> Unit,
    onInfo: (() -> Unit)?,
    brightness: Float,
    onBrightness: (Float) -> Unit,
    keepOn: Boolean,
    onKeepOn: (Boolean) -> Unit,
    follow: Boolean,
    onFollow: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(8.dp).background(ToolbarDark, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // The theme button names the theme it switches to; U+FE0E asks for the plain glyph, not the colour emoji.
            Tool(if (night) "☀︎" else "☾︎", stringResource(if (night) R.string.toolbar_day else R.string.toolbar_night), true, onTheme, Modifier.weight(1f))
            Tool("A−", stringResource(R.string.toolbar_smaller), sizeStep > 0, { onSize(sizeStep - 1) }, Modifier.weight(1f))
            Tool("A+", stringResource(R.string.toolbar_larger), sizeStep < sizeSteps - 1, { onSize(sizeStep + 1) }, Modifier.weight(1f))
            Tool("↔", stringResource(R.string.toolbar_fit), true, { onFit(!fit) }, Modifier.weight(1f), selected = fit)
            Tool("ⓘ", stringResource(R.string.toolbar_info), onInfo != null, { onInfo?.invoke() }, Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("☼", color = Color.White, fontSize = 22.sp)
            Spacer(Modifier.width(12.dp))
            Slider(
                value = brightness, onValueChange = onBrightness, valueRange = BRIGHTNESS_MIN..1f, modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = Palette.FireOrange, activeTrackColor = Palette.FireOrange, inactiveTrackColor = Color(0xFF444444)),
            )
        }
        SwitchRow(stringResource(R.string.toolbar_keep_on), keepOn, onKeepOn)
        SwitchRow(stringResource(R.string.toolbar_follow), follow, onFollow)
    }
}

@Composable
private fun Tool(glyph: String, label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier, selected: Boolean = false) {
    val ink = (if (selected) Palette.FireOrange else Color.White).copy(alpha = if (enabled) 1f else 0.3f)
    Column(
        modifier.heightIn(min = 56.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(glyph, color = ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(label, color = ink, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Switch) { onChecked(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(checkedTrackColor = Palette.FireOrange, checkedThumbColor = Color.White, uncheckedTrackColor = Color(0xFF2A2A2A)),
        )
    }
}

/**
 * The song-info sheet of guiDesign D-45: the song file's metadata as it is, in D-45's order and only
 * what is present, then the library it was shown from, or *Ontvangen van de leider* for a song that came
 * over the air ([library] null). Variant links are not built yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SongInfoSheet(meta: SongMeta, library: String?, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SheetNight, contentColor = Color.White) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Text(meta.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            meta.subtitle?.let { Text(it, color = LyricColors.Night.secondary, fontSize = 16.sp) }
            Spacer(Modifier.padding(top = 8.dp))
            Info(stringResource(R.string.info_lyricist), meta.lyricists.joinToString(", "))
            Info(stringResource(R.string.info_composer), meta.composers.joinToString(", "))
            Info(stringResource(R.string.info_melody), meta.melody)
            Info(stringResource(R.string.info_year), meta.year)
            Info(stringResource(R.string.info_copyright), meta.copyright?.let { "© $it" })
            Info(stringResource(R.string.info_licence), meta.license ?: library?.let { stringResource(R.string.info_licence_library, it) })
            Info(stringResource(R.string.info_source), meta.source)
            Info(stringResource(R.string.info_tags), meta.tags.joinToString(", "))
            Info(stringResource(R.string.info_library), library ?: stringResource(R.string.info_from_leader))
        }
    }
}

@Composable
private fun Info(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, color = LyricColors.Night.secondary, fontSize = 13.sp)
        Text(value, fontSize = 16.sp)
    }
}

/** D-17: dark in both themes, translucent over the lyrics. */
private val ToolbarDark = Color(0xE61A1A1A)

/** The slider's lowest brightness: dim, never black. */
internal const val BRIGHTNESS_MIN = 0.02f
