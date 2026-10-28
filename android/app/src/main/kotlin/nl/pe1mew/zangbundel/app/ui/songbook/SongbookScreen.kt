// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.songbook

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.app.ZangbundelApp
import nl.pe1mew.zangbundel.app.library.SongbookFilter
import nl.pe1mew.zangbundel.app.ui.ConfigurationScreen
import nl.pe1mew.zangbundel.app.ui.textPanel
import nl.pe1mew.zangbundel.app.ui.theme.Palette

/**
 * The *Zangbundel* screen (guiDesign D-52, D-21 as amended in v0.16): every song of the enabled libraries
 * with its library's colour square [FR-LIB-30]; a search on the title or, with *Ook in de tekst*, the
 * lyrics [FR-LIB-31]; A–Z / Z–A; chips for library, language and tag; *Wis filters* whenever anything
 * differs from the default. The orders and filters by use wait for the gathering memories.
 * *Bibliotheken* opens the library screen (D-50), *Setlists* the setlists (D-54). In editing mode (D-54)
 * a banner names the setlist being filled with its song count and *Klaar*, and every row has a checkbox
 * and shows its library's name.
 */
@Composable
fun SongbookScreen(
    onLibraries: () -> Unit,
    onSetlists: () -> Unit,
    onSong: (String, String) -> Unit,
    onDone: () -> Unit,
    model: SongbookViewModel = viewModel(factory = SongbookViewModel.Factory),
) {
    val state by model.state.collectAsState()
    val container = (LocalContext.current.applicationContext as ZangbundelApp).container
    LaunchedEffect(Unit) { model.load() }
    val f = state.filter
    ConfigurationScreen {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            val editing = state.editing
            if (editing != null) {
                Row(Modifier.fillMaxWidth().background(Palette.FireOrange, RoundedCornerShape(12.dp)).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(editing.title + " · " + pluralStringResource(R.plurals.library_songs, editing.songs, editing.songs),
                        color = Palette.NightBlueDark, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = onDone) { Text(stringResource(R.string.done), color = Palette.NightBlueDark) }
                }
            } else {
                // The title on its own line: *Zangbundel* (guiDesign v0.27) no longer fits beside the two buttons.
                Text(stringResource(R.string.songbook_title), style = MaterialTheme.typography.headlineMedium, maxLines = 1)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onSetlists) { Text(stringResource(R.string.songbook_setlists)) }
                    TextButton(onClick = onLibraries) { Text(stringResource(R.string.songbook_libraries)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (!state.loading && state.entries.isEmpty()) {
                Column(Modifier.fillMaxWidth().textPanel().padding(16.dp)) {
                    Text(stringResource(R.string.songbook_empty))
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onLibraries, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.songbook_libraries)) }
                }
                return@Column
            }
            OutlinedTextField(
                value = f.query, onValueChange = { model.set(f.copy(query = it)) }, singleLine = true,
                label = { Text(stringResource(R.string.songbook_search)) }, modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = f.inText, onCheckedChange = { model.set(f.copy(inText = it)) })
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.songbook_in_text), modifier = Modifier.weight(1f))
                TextButton(onClick = { model.set(f.copy(descending = !f.descending)) }) {
                    Text(stringResource(if (f.descending) R.string.songbook_sort_za else R.string.songbook_sort_az))
                }
            }
            Chips(state, model)
            if (!f.isDefault) TextButton(onClick = model::clear) { Text(stringResource(R.string.songbook_clear)) }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.weight(1f).fillMaxWidth().textPanel()) {
                if (state.rows.isEmpty() && !state.loading) Text(stringResource(R.string.songbook_none), modifier = Modifier.padding(16.dp))
                LazyColumn {
                    items(state.rows, key = { it.key }) { row ->
                        SongRow(row, editing?.let { e -> (row.key in e.keys) to { model.toggle(row.entry) } }) {
                            // D-85: the reading view swipes through the songbook as shown now: search, filters and sort.
                            container.readingOrder = state.rows.map { it.entry.libraryId to it.entry.songId }
                            onSong(row.entry.libraryId, row.entry.songId)
                        }
                        HorizontalDivider(color = Palette.NightBlue)
                    }
                }
            }
        }
    }
}

/** One row of chips: libraries (when more than one), languages (when more than one), tags. */
@Composable
private fun Chips(state: SongbookViewModel.State, model: SongbookViewModel) {
    val f = state.filter
    fun <T> Set<T>.toggle(x: T) = if (x in this) this - x else this + x
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.libraries.size > 1) items(state.libraries, key = { "l:" + it.id }) { l ->
            FilterChip(selected = l.id in f.libraries, onClick = { model.set(f.copy(libraries = f.libraries.toggle(l.id))) },
                label = { Text(l.name) }, leadingIcon = { ColourSquare(l.colour) })
        }
        if (state.languages.size > 1) items(state.languages, key = { "t:$it" }) { lang ->
            FilterChip(selected = lang in f.languages, onClick = { model.set(f.copy(languages = f.languages.toggle(lang))) },
                label = { Text(languageName(lang)) })
        }
        items(state.tags.entries.toList(), key = { "g:" + it.key }) { (tag, label) ->
            FilterChip(selected = tag in f.tags, onClick = { model.set(f.copy(tags = f.tags.toggle(tag))) }, label = { Text(label) })
        }
    }
}

@Composable
private fun SongRow(row: SongbookFilter.Row, check: Pair<Boolean, () -> Unit>?, onClick: () -> Unit) {
    val e = row.entry
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = if (check != null) 0.dp else 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (check != null) Checkbox(checked = check.first, onCheckedChange = { check.second() })
        ColourSquare(e.colour)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            // D-52: the language, and the library's name only where two visible rows share the title.
            // In editing mode every row names its library (D-54).
            Text(listOfNotNull(e.libraryName.takeIf { row.showLibrary || check != null }, e.language).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = Palette.OffWhite)
        }
    }
}

/** D-51: the colour is `colour % 8`; from the ninth library on, the square carries a dot. */
@Composable
internal fun ColourSquare(colour: Int) {
    Box(Modifier.size(12.dp).background(Palette.Libraries[colour % Palette.Libraries.size], RoundedCornerShape(2.dp)), contentAlignment = Alignment.Center) {
        if (colour >= Palette.Libraries.size) Box(Modifier.size(4.dp).background(Palette.NightBlueDark, CircleShape))
    }
}

/** A language code as its name in the app's language (D-77), "nl" → "Nederlands". */
private fun languageName(code: String): String {
    val app = Locale.getDefault()
    val name = Locale.forLanguageTag(code).getDisplayLanguage(app)
    return if (name.isEmpty() || name == code) code else name.replaceFirstChar { it.titlecase(app) }
}
