// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.gathering

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.app.gathering.Gathering
import nl.pe1mew.zangbundel.app.ui.ConfigurationScreen
import nl.pe1mew.zangbundel.app.ui.fingerprintText
import nl.pe1mew.zangbundel.app.ui.textPanel
import nl.pe1mew.zangbundel.app.ui.theme.Palette
import nl.pe1mew.zangbundel.core.protocol.beacon.JoinLink

/**
 * The join code dialog (guiDesign D-26, FR-GATH-05): asked before joining a gathering with a lock, and
 * again when the leader refused the code. [wrong] says the last code was refused.
 */
@Composable
internal fun CodeDialog(name: String, wrong: Boolean, onCode: (String) -> Unit, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.code_title, name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(if (wrong) R.string.code_wrong else R.string.code_text))
                OutlinedTextField(
                    code, { code = it.filter { c -> !c.isWhitespace() }.take(JoinLink.CODE_MAX_CHARS) }, singleLine = true,
                    label = { Text(stringResource(R.string.code_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onCode(code) }, enabled = code.isNotEmpty()) { Text(stringResource(R.string.code_join)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * The joining screen after a QR code (guiDesign D-35, FR-GATH-19, FR-GATH-20): the name and the
 * fingerprint, and one status line: *Kampvuur zoeken…* until the beacon is heard, *Gevonden,
 * controleren…* until the leader's signed state is in; a state the code's key did not sign says *Dit is
 * niet het kampvuur van de QR-code* and offers only *Naar start*. After [SEARCH_MS] without the
 * gathering it offers *Opnieuw zoeken* and *Naar start*; the engine never stops looking meanwhile.
 */
@Composable
internal fun JoiningScreen(m: Gathering.Mode.Singing, now: () -> Long, onHome: () -> Unit) {
    var since by remember { mutableLongStateOf(m.joiningSince) }
    var clock by remember { mutableLongStateOf(now()) }
    LaunchedEffect(Unit) { while (true) { clock = now(); delay(500) } }
    val waitedLong = !m.found && clock - since >= SEARCH_MS
    ConfigurationScreen {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.fillMaxWidth().textPanel().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.joining_title), style = MaterialTheme.typography.titleMedium)
                Text(m.name, style = MaterialTheme.typography.headlineSmall)
                Text(fingerprintText(m.fingerprint), color = Palette.OffWhite)
                Spacer(Modifier.height(8.dp))
                if (m.notTheCampfire) {
                    Text("⚠ " + stringResource(R.string.joining_not_this), color = Palette.FireOrangeLight)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(if (m.found) R.string.joining_found else R.string.joining_searching))
                    }
                    if (!m.found) Text(stringResource(R.string.joining_closer), style = MaterialTheme.typography.bodySmall, color = Palette.OffWhite)
                }
            }
            Spacer(Modifier.height(16.dp))
            if (waitedLong && !m.notTheCampfire) {
                Button(onClick = { since = now() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.joining_again)) }
                Spacer(Modifier.height(8.dp))
            }
            if (waitedLong || m.notTheCampfire) OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.joining_home)) }
            else TextButton(onClick = onHome) { Text(stringResource(R.string.cancel), color = Palette.OffWhite) }
        }
    }
}

/** D-35: how long the joining screen looks before it offers to search again. */
private const val SEARCH_MS = 60_000L
