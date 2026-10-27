// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.app.ZangbundelApp
import nl.pe1mew.zangbundel.app.ui.ConfigurationScreen
import nl.pe1mew.zangbundel.app.ui.setlists.rememberShare
import nl.pe1mew.zangbundel.app.ui.textPanel
import nl.pe1mew.zangbundel.app.ui.theme.Palette
import nl.pe1mew.zangbundel.platform.storage.CampfireStats

/**
 * *Statistieken* (guiDesign D-60, D-62; assessment SP-16): what the leader's phone keeps of a campfire
 * once switched on, listed in the plain words of FR-GEN-08 with the switch at the bottom; then the
 * season: totals, each campfire, the songs sung most and least; *Delen* shows the JSON file and then hands it
 * to the share sheet, the only way out; *Wissen* deletes it all. Off by default, and no prompt anywhere else (P7).
 */
@Composable
fun StatisticsScreen() {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as ZangbundelApp).container }
    val settings = container.storage.settings
    val scope = rememberCoroutineScope()
    val on by settings.statisticsOn.collectAsState(initial = false)
    var records by remember { mutableStateOf<List<CampfireStats>>(emptyList()) }
    var titles by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loaded by remember { mutableIntStateOf(0) }
    var confirmWipe by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(loaded) {
        records = container.storage.statistics.all()
        titles = container.storage.songbook.entries().associate { it.libraryId + "/" + it.songId to it.title }
    }
    ConfigurationScreen {
        Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.statistics_title), style = MaterialTheme.typography.headlineMedium)
            // D-60: exactly what is kept and what never is, then the switch.
            Column(Modifier.fillMaxWidth().textPanel().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.statistics_kept_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.statistics_kept))
                Text(stringResource(R.string.statistics_never_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.statistics_never))
                Text(stringResource(R.string.statistics_leaves))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.statistics_switch), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Switch(checked = on, onCheckedChange = { v -> scope.launch { settings.setStatisticsOn(v) } })
                }
            }
            // D-62: the season.
            Text(stringResource(R.string.statistics_season), style = MaterialTheme.typography.titleMedium)
            Column(Modifier.fillMaxWidth().textPanel().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (records.isEmpty()) Text(stringResource(R.string.statistics_none))
                else {
                    val songs = records.sumOf { it.songs }
                    val average = records.map { it.maxSingers }.average().let { Math.round(it).toInt() }
                    Text(
                        pluralStringResource(R.plurals.statistics_campfires, records.size, records.size) + ", " +
                            pluralStringResource(R.plurals.statistics_songs, songs, songs) + ", " +
                            pluralStringResource(R.plurals.statistics_average_singers, average, average),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    // The date on its own line: beside the figures it squeezed them on a phone's width.
                    for (r in records.asReversed()) Column {
                        Text(r.date)
                        Text(
                            pluralStringResource(R.plurals.statistics_songs, r.songs, r.songs) + " · " +
                                pluralStringResource(R.plurals.statistics_singers, r.maxSingers, r.maxSingers) + " · " + duration(r.durationMs),
                            color = Palette.OffWhite, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    val counts = songCounts(records)
                    if (counts.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.statistics_most), style = MaterialTheme.typography.titleSmall)
                        for ((key, n) in counts.take(TOP)) Text("${titles[key] ?: key} · $n×")
                        if (counts.size > TOP) {
                            Text(stringResource(R.string.statistics_least), style = MaterialTheme.typography.titleSmall)
                            for ((key, n) in counts.takeLast(TOP).asReversed()) Text("${titles[key] ?: key} · $n×")
                        }
                    }
                }
            }
            Button(onClick = { preview = exportJson(records) }, enabled = records.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.statistics_share))
            }
            OutlinedButton(onClick = { confirmWipe = true }, enabled = records.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.statistics_wipe))
            }
        }
    }
    if (confirmWipe) AlertDialog(
        onDismissRequest = { confirmWipe = false },
        title = { Text(stringResource(R.string.statistics_wipe_title)) },
        confirmButton = {
            TextButton(onClick = { confirmWipe = false; scope.launch { container.storage.statistics.deleteAll(); loaded++ } }) { Text(stringResource(R.string.statistics_wipe)) }
        },
        dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text(stringResource(R.string.cancel)) } },
    )
    preview?.let { StatisticsPreview(it) { preview = null } }
}

/**
 * The file as it will be shared, before the share sheet opens, so the leader sends what they saw
 * (assessment SP-16, P6); from this screen and from the drawer's *Statistieken delen* (D-61).
 */
@Composable
internal fun StatisticsPreview(json: String, onDismiss: () -> Unit) {
    val share = rememberShare()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.statistics_preview_title)) },
        text = {
            Text(json, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()))
        },
        confirmButton = { TextButton(onClick = { share(FILE_NAME, json); onDismiss() }) { Text(stringResource(R.string.statistics_share)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private const val FILE_NAME = "zangbundel-statistieken.json"

/** The file *Delen* writes: every kept record, as an array (D-60). */
internal fun exportJson(records: List<CampfireStats>): String = records.joinToString(",\n", prefix = "[\n", postfix = "\n]\n") { it.record }

/** How often each song was pushed over the season, most first, from the records' pushes (not their skipped songs). */
internal fun songCounts(records: List<CampfireStats>): List<Pair<String, Int>> =
    records.flatMap { r -> PUSHED.findAll(r.record).map { it.groupValues[1] + "/" + it.groupValues[2] }.toList() }
        .groupingBy { it }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key to it.value }

private val PUSHED = Regex("\\{\"library\":\"([^\"]*)\",\"song\":\"([^\"]*)\",\"atMs\"")

private fun duration(ms: Long): String = "%d:%02d".format(ms / 3_600_000, (ms / 60_000) % 60)

/** D-62: three most and three least sung. */
private const val TOP = 3
