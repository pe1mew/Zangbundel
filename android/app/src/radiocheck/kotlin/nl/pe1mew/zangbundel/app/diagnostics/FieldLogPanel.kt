// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.diagnostics

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.app.ui.setlists.rememberShare

/**
 * The field-test log on the radio check (architecture A-20): how much is logged, *Log delen*, which shows the
 * file before the share sheet opens (as for the statistics, assessment SP-16, P6), and *Log wissen*.
 * Shown in the `.fieldtest` app only.
 */
@Composable
internal fun FieldLogPanel() {
    val context = LocalContext.current
    var text by remember { mutableStateOf(FieldLog.text(context)) }
    var preview by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val share = rememberShare()
    val lines = text.lines().count { it.isNotBlank() }
    HorizontalDivider()
    Text(stringResource(R.string.fieldtest_log_title), style = MaterialTheme.typography.titleMedium)
    Text(if (lines == 0) stringResource(R.string.fieldtest_log_empty) else pluralStringResource(R.plurals.fieldtest_log_lines, lines, lines))
    Button(enabled = lines > 0, onClick = { text = FieldLog.text(context); preview = true }) { Text(stringResource(R.string.fieldtest_log_share)) }
    OutlinedButton(enabled = lines > 0, onClick = { confirmClear = true }) { Text(stringResource(R.string.fieldtest_log_clear)) }
    if (preview) AlertDialog(
        onDismissRequest = { preview = false },
        title = { Text(stringResource(R.string.fieldtest_log_preview)) },
        text = {
            Text(text, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()))
        },
        confirmButton = { TextButton(onClick = { share(FILE_NAME, text); preview = false }) { Text(stringResource(R.string.fieldtest_log_share)) } },
        dismissButton = { TextButton(onClick = { preview = false }) { Text(stringResource(R.string.fieldtest_cancel)) } },
    )
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text(stringResource(R.string.fieldtest_log_clear_title)) },
        confirmButton = { TextButton(onClick = { FieldLog.clear(context); text = ""; confirmClear = false }) { Text(stringResource(R.string.fieldtest_log_clear)) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.fieldtest_cancel)) } },
    )
}

private const val FILE_NAME = "zangbundel-veldtest.jsonl"
