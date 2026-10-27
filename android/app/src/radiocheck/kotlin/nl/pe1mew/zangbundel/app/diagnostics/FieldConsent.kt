// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.app.ui.ConfigurationScreen
import nl.pe1mew.zangbundel.app.ui.textPanel

/**
 * The consent of test plan 5.5, on the first start of the `fieldtest` build, before anything else: what
 * the log keeps and never keeps, that it leaves the phone only by the tester's hand, how to stop, and the
 * statement the tester agrees to. *Ik doe mee* starts the log; *Niet meedoen* closes the app, which keeps
 * nothing. Testers also sign the same text on paper or by e-mail before a session.
 */
@Composable
fun FieldConsentScreen(onAccept: () -> Unit, onDecline: () -> Unit) {
    ConfigurationScreen {
        Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.fieldtest_consent_title), style = MaterialTheme.typography.headlineMedium)
            Column(Modifier.fillMaxWidth().textPanel().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.fieldtest_consent_keeps))
                Text(stringResource(R.string.fieldtest_consent_never))
                Text(stringResource(R.string.fieldtest_consent_leaves))
                Text(stringResource(R.string.fieldtest_consent_stop))
                Text(stringResource(R.string.fieldtest_consent_statement), style = MaterialTheme.typography.titleMedium)
            }
            Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.fieldtest_consent_accept)) }
            OutlinedButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.fieldtest_consent_decline)) }
        }
    }
}
