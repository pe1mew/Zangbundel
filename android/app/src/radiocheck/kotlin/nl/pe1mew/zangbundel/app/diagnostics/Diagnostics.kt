// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.diagnostics

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.pe1mew.zangbundel.app.Log
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.platform.ble.AdvertisingProbe

/**
 * Developer and field-test tools, compiled into the `debug` and `fieldtest` builds only
 * (`src/radiocheck`); the release build gets the empty stand-in in `src/release`.
 */
object Diagnostics {
    const val AVAILABLE = true
    const val ROUTE = "radiocheck"
}

/** The home screen's way in to the radio check. The two-phone test was removed on 2026-10-27: every device test now runs in a real campfire. */
@Composable
fun DiagnosticsEntry(onOpen: (String) -> Unit) {
    TextButton(onClick = { onOpen(Diagnostics.ROUTE) }) { Text(stringResource(R.string.radiocheck_title)) }
}

/**
 * Radio check of test-plan F-01. Shows the phone's advertising capabilities and what the probe
 * measured; the tester shares the result as plain text. Asks for the advertise permission only
 * when the tester taps the run button (API 31+), never on opening.
 */
@Composable
fun RadioCheckScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val adapter = remember {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }
    var running by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Int?>(null) } // a string resource, resolved while drawing
    var report by remember { mutableStateOf<AdvertisingProbe.Report?>(null) }

    fun start() {
        val probe = AdvertisingProbe(context, adapter ?: return)
        running = true
        message = null
        scope.launch {
            try {
                val result = probe.run()
                report = result
                Log.i("radiocheck " + result.rows().joinToString(" ") { (k, v) -> "$k=${v.replace(' ', '_')}" })
            } finally {
                running = false
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else message = R.string.radiocheck_permission_denied
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.radiocheck_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.radiocheck_explanation), style = MaterialTheme.typography.bodyMedium)
            Button(
                enabled = !running,
                onClick = {
                    when {
                        adapter == null -> message = R.string.radiocheck_no_bluetooth
                        !adapter.isEnabled -> message = R.string.radiocheck_bluetooth_off
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            !AdvertisingProbe(context, adapter).hasAdvertisePermission() ->
                            permission.launch(Manifest.permission.BLUETOOTH_ADVERTISE)
                        else -> start()
                    }
                },
            ) { Text(stringResource(R.string.radiocheck_run)) }

            if (running) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.radiocheck_running))
                }
            }
            message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }

            report?.let { r ->
                HorizontalDivider()
                Text(header(context), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                r.rows().forEach { (key, value) ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(key, modifier = Modifier.weight(1f), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        Text(value, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedButton(onClick = {
                    val text = header(context) + "\n" + r.toText()
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    context.startActivity(Intent.createChooser(send, null))
                }) { Text(stringResource(R.string.radiocheck_share)) }
            }
            // Architecture A-20: the field-test log, in the .fieldtest app only.
            if (FieldLog.isFieldTest(context)) FieldLogPanel()
        }
    }
}

/** App build only; the phone's model is left out (test plan section 8: models only with consent). */
private fun header(context: Context): String {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    return "zangbundel ${info.versionName} ($code) ${context.packageName.substringAfterLast('.')}"
}
