// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.gathering

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.pe1mew.zangbundel.app.Log
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.app.gathering.Gathering
import nl.pe1mew.zangbundel.app.ui.ConfigurationScreen
import nl.pe1mew.zangbundel.app.ui.textPanel
import nl.pe1mew.zangbundel.app.ui.theme.Palette

/**
 * guiDesign D-67: after the app died while leading, this replaces the welcome screen on the same
 * background: the gathering's name and two words (D-36), how long ago it stopped, and *Hervatten* or
 * *Opnieuw beginnen*. A failed resume (Bluetooth off, the radio refused) says so and keeps both.
 */
@Composable
fun ResumeScreen(offer: Gathering.ResumeOffer, now: () -> Long, onResume: suspend () -> Unit, onRestart: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val minutes = ((now() - offer.stoppedWall) / 60_000L).toInt()
    ConfigurationScreen {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.resume_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth().textPanel().padding(16.dp)) {
                Text(offer.name, style = MaterialTheme.typography.titleLarge)
                Text(offer.fingerprint, color = Palette.OffWhite)
                Spacer(Modifier.height(8.dp))
                Text(if (minutes < 1) stringResource(R.string.resume_stopped_now) else stringResource(R.string.resume_stopped, minutes))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.resume_text))
            }
            if (failed) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.resume_failed), color = Palette.FireOrange)
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    busy = true
                    failed = false
                    scope.launch {
                        runCatching { onResume() }.onFailure { Log.d("resume failed: ${it.message}"); failed = true }
                        busy = false
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(stringResource(R.string.resume_resume)) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { busy = true; scope.launch { onRestart() } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.resume_restart))
            }
        }
    }
}
