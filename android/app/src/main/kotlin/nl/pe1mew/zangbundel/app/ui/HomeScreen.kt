// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.app.diagnostics.Diagnostics
import nl.pe1mew.zangbundel.app.diagnostics.DiagnosticsEntry

/**
 * The home screen (guiDesign D-21): two primary actions, join and start, and a small row at the bottom
 * for the songbook, memories, settings and About (the songbook leads on to the libraries, D-21 as amended
 * in v0.16). Join and start wait for the gathering screens and are disabled until then; of the row, only
 * the songbook is built.
 */
@Composable
fun HomeScreen(onOpen: (String) -> Unit) {
    ConfigurationScreen {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.weight(1f))
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.home_join)) }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.home_start)) }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                IconEntry(R.drawable.ic_library, R.string.home_songbook) { onOpen(Routes.SONGBOOK) }
                IconEntry(R.drawable.ic_memories, R.string.home_memories, null)
                IconEntry(R.drawable.ic_settings, R.string.home_settings, null)
                IconEntry(R.drawable.ic_about, R.string.home_about, null)
            }
            // Debug and field-test builds only; the release stand-in draws nothing.
            if (Diagnostics.AVAILABLE) DiagnosticsEntry(onOpen)
        }
    }
}

/** One entry of D-21's icon row: the icon with its name under it, small; [onClick] null while the screen is not built. */
@Composable
private fun IconEntry(icon: Int, label: Int, onClick: (() -> Unit)?) {
    val alpha = if (onClick == null) 0.38f else 1f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = onClick != null) { onClick?.invoke() }.padding(8.dp).alpha(alpha),
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(28.dp))
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall)
    }
}
