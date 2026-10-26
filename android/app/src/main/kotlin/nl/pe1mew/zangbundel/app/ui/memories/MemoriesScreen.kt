// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.memories

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.app.ZangbundelApp
import nl.pe1mew.zangbundel.app.ui.ConfigurationScreen
import nl.pe1mew.zangbundel.app.ui.songbook.ColourSquare
import nl.pe1mew.zangbundel.app.ui.textPanel
import nl.pe1mew.zangbundel.app.ui.theme.Palette
import nl.pe1mew.zangbundel.platform.storage.GatheringMemory
import nl.pe1mew.zangbundel.platform.storage.MemorySong
import nl.pe1mew.zangbundel.platform.storage.SongbookEntry
import java.text.DateFormat
import java.util.Date

/**
 * *Herinneringen* (guiDesign D-49; FR-DATA-03, FR-DATA-04): one card per campfire with its name, date
 * and number of songs, and on the leader's side how many sang along; *Alles verwijderen* with a
 * confirmation. Deliberately plain (D-48): no photos, no likes, nothing sent anywhere by the app.
 */
@Composable
fun MemoriesScreen(onOpen: (Long) -> Unit) {
    val context = LocalContext.current
    val memories = remember { (context.applicationContext as ZangbundelApp).container.storage.memories }
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<GatheringMemory>?>(null) }
    var confirmAll by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { list = memories.all() }
    ConfigurationScreen {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(stringResource(R.string.memories_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            val l = list
            if (l != null && l.isEmpty()) {
                Text(stringResource(R.string.memories_empty), modifier = Modifier.fillMaxWidth().textPanel().padding(16.dp))
                Spacer(Modifier.weight(1f))
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(l.orEmpty(), key = { it.uid }) { m ->
                        Column(Modifier.fillMaxWidth().textPanel().clickable { onOpen(m.uid) }.padding(16.dp)) {
                            Text(m.name, style = MaterialTheme.typography.titleMedium)
                            Text(summary(m), style = MaterialTheme.typography.bodySmall, color = Palette.OffWhite)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { confirmAll = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.memories_delete_all)) }
            }
        }
    }
    if (confirmAll) ConfirmDelete(R.string.memories_delete_all_title, onDismiss = { confirmAll = false }) {
        confirmAll = false
        scope.launch { memories.deleteAll(); list = memories.all() }
    }
}

/** One memory (D-49, FR-DATA-05): the titles in order; a song in a local library opens for reading, the others are plain text. */
@Composable
fun MemoryScreen(uid: Long, onSong: (String, String) -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val storage = remember { (context.applicationContext as ZangbundelApp).container.storage }
    val scope = rememberCoroutineScope()
    var memory by remember { mutableStateOf<GatheringMemory?>(null) }
    var songs by remember { mutableStateOf<List<MemorySong>>(emptyList()) }
    var installed by remember { mutableStateOf<Map<String, SongbookEntry>>(emptyMap()) }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(uid) {
        memory = storage.memories.all().firstOrNull { it.uid == uid }
        songs = storage.memories.songs(uid)
        installed = storage.songbook.entries().associateBy { it.libraryId + "/" + it.songId }
    }
    val m = memory ?: return
    val shareText = shareText(m, songs)
    ConfigurationScreen {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(m.name, style = MaterialTheme.typography.headlineMedium)
            Text(summary(m), color = Palette.OffWhite)
            Spacer(Modifier.height(16.dp))
            LazyColumn(Modifier.weight(1f).fillMaxWidth().textPanel()) {
                itemsIndexed(songs) { i, s ->
                    val local = installed[s.songKey]
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = local != null) { onSong(s.libraryId, s.songId) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${i + 1}. ${s.title}")
                            if (local == null) Text(stringResource(R.string.memories_from_leader), style = MaterialTheme.typography.bodySmall, color = Palette.OffWhite)
                        }
                        local?.let { ColourSquare(it.colour) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { confirm = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.memories_delete)) }
                // FR-DATA-05: the titles as plain text, only through an app the user picks.
                Button(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
                    context.startActivity(Intent.createChooser(send, null))
                }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.memories_share)) }
            }
        }
    }
    if (confirm) ConfirmDelete(R.string.memories_delete_title, onDismiss = { confirm = false }) {
        confirm = false
        scope.launch { storage.memories.delete(uid); onDeleted() }
    }
}

@Composable
private fun summary(m: GatheringMemory): String {
    val date = DateFormat.getDateInstance(DateFormat.LONG, LocalConfiguration.current.locales[0]).format(Date(m.startedAt))
    val songs = pluralStringResource(R.plurals.library_songs, m.songs, m.songs)
    val singers = m.singers
    return if (m.led && singers != null) "$date · $songs · " + stringResource(R.string.memories_led, pluralStringResource(R.plurals.leader_singers, singers, singers))
    else "$date · $songs"
}

@Composable
private fun shareText(m: GatheringMemory, songs: List<MemorySong>): String {
    val date = DateFormat.getDateInstance(DateFormat.LONG, LocalConfiguration.current.locales[0]).format(Date(m.startedAt))
    return "${m.name}, $date\n" + songs.mapIndexed { i, s -> "${i + 1}. ${s.title}" }.joinToString("\n")
}

@Composable
private fun ConfirmDelete(title: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(R.string.memories_delete_text)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.memories_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
