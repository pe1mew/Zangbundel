// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.gathering

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.app.ZangbundelApp
import nl.pe1mew.zangbundel.app.ui.ConfigurationScreen
import nl.pe1mew.zangbundel.app.ui.textPanel
import nl.pe1mew.zangbundel.app.ui.theme.Palette
import java.text.DateFormat
import java.util.Date

/**
 * The summary after the leader ends a gathering (guiDesign D-69), a configuration screen: the name, the
 * date, the time sung, the songs sung and the evening's maximum number of singers, and *Terug naar
 * start*. The same figures are what the leader's gathering memory keeps [FR-DATA-03].
 */
@Composable
fun SummaryScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val gathering = remember { (context.applicationContext as ZangbundelApp).container.gathering }
    val summary by gathering.summary.collectAsState()
    val done = { gathering.dismissSummary(); onDone() }
    BackHandler(onBack = done)
    val s = summary
    val locale = LocalConfiguration.current.locales[0]
    ConfigurationScreen {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.summary_title), style = MaterialTheme.typography.titleMedium, color = Palette.OffWhite)
            if (s != null) {
                Text(s.name, style = MaterialTheme.typography.headlineMedium)
                Text(DateFormat.getDateInstance(DateFormat.LONG, locale).format(Date(s.startedWall)), color = Palette.OffWhite)
                Column(Modifier.fillMaxWidth().textPanel().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val minutes = s.durationMs / 60_000
                    Line(stringResource(R.string.summary_time), "%d:%02d".format(minutes / 60, minutes % 60))
                    Line(stringResource(R.string.summary_songs), s.songs.toString())
                    Line(stringResource(R.string.summary_singers), s.singers.toString())
                }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = done, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.summary_home)) }
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Bold)
    }
}
