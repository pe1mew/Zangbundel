// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.library

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.pe1mew.zangbundel.app.ZangbundelApp
import nl.pe1mew.zangbundel.app.library.DefaultLibrary
import nl.pe1mew.zangbundel.app.library.LibraryRepository
import nl.pe1mew.zangbundel.app.library.readPicked
import nl.pe1mew.zangbundel.core.songformat.Limits
import nl.pe1mew.zangbundel.platform.storage.AppSettings
import nl.pe1mew.zangbundel.platform.storage.LibraryIndex
import nl.pe1mew.zangbundel.platform.storage.LibraryInstaller
import nl.pe1mew.zangbundel.platform.storage.LibraryRow

/**
 * The *Bibliotheek* screen's state (guiDesign D-50): the installed libraries and the steps of adding
 * one. Every step waits for the leader's tap; nothing downloads or installs on its own (FR-LIB-12).
 */
class LibraryViewModel(
    private val repository: LibraryRepository,
    private val index: LibraryIndex,
    private val settings: AppSettings,
    /** Starts or stops the Wi-Fi check (FR-LIB-13): [LibraryUpdateWorker.schedule] in the app. */
    private val scheduleCheck: (Boolean) -> Unit,
) : ViewModel() {

    sealed interface Step {
        /** Paste a URL or pick a file. */
        data object Choose : Step
        data object Busy : Step
        /** A release was found: version and size before anything downloads (FR-NET-04). */
        data class Found(val release: LibraryRepository.Found.Release, val installed: LibraryRow? = null) : Step
        /** *Bijwerken* found the archive that is already installed. */
        data class UpToDate(val library: LibraryRow) : Step
        data class Failed(val reason: LibraryRepository.Reason, val detail: String?) : Step
        /** The licence, summary and full text, before the import completes (FR-LIB-21). */
        data class Licence(val checked: LibraryRepository.Checked) : Step
        /** Refused archives come here too, with their report. */
        data class Report(val checked: LibraryRepository.Checked, val outcome: LibraryInstaller.Outcome?) : Step
    }

    data class State(
        val libraries: List<LibraryRow> = emptyList(),
        val step: Step? = null,
        val confirmRemove: LibraryRow? = null,
        /** FR-LIB-13: the switch, and what the last checks found, by library id (the version, or empty). */
        val autoCheck: Boolean = false,
        val updates: Map<String, String> = emptyMap(),
    ) {
        /** The default library is offered while it is not installed (FR-LIB-02). */
        val offerDefault: Boolean get() = libraries.none { it.id == DefaultLibrary.ID }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    init {
        refresh()
        viewModelScope.launch { settings.autoUpdateCheck.collect { on -> _state.update { it.copy(autoCheck = on) } } }
        viewModelScope.launch { settings.availableUpdates.collect { found -> _state.update { it.copy(updates = found) } } }
    }

    /** *Automatisch controleren op wifi* (D-50, FR-LIB-13): on, it checks right away and then daily; off, never. */
    fun setAutoCheck(on: Boolean) = viewModelScope.launch {
        settings.setAutoUpdateCheck(on)
        scheduleCheck(on)
    }

    /** An installed update, or a *Bijwerken* that found the library current: the check's line goes. */
    private suspend fun clearUpdate(libraryId: String) {
        settings.setAvailableUpdate(libraryId, null)
        settings.setIndexEtag(libraryId, null)
    }

    fun refresh() = viewModelScope.launch {
        // Computed first: a copy taken before index.all() suspends would write back a stale state (the switch below).
        val libraries = index.all().sortedWith(DefaultLibrary.order)
        _state.update { it.copy(libraries = libraries) }
    }

    /** The leader's tap on the default library: looked up like a pasted URL, nothing downloads yet. */
    fun addDefault() = find(DefaultLibrary.URL)

    fun add() { step(Step.Choose) }
    fun cancel() { step(null) }

    fun find(url: String) = viewModelScope.launch {
        step(Step.Busy)
        val next = when (val f = repository.find(url)) {
            is LibraryRepository.Found.Release -> Step.Found(f)
            is LibraryRepository.Found.Failed -> Step.Failed(f.reason, f.detail)
        }
        step(next)
    }

    private fun step(step: Step?) = _state.update { it.copy(step = step) }

    /**
     * *Bijwerken* (FR-LIB-11, FR-LIB-12): looks up the newest release where the library came from and
     * shows it before anything downloads; a library from a file without an index goes to the file picker.
     */
    fun update(library: LibraryRow) {
        val source = repository.updateSource(library) ?: return add()
        viewModelScope.launch {
            step(Step.Busy)
            val next = when (val f = repository.find(source)) {
                is LibraryRepository.Found.Release ->
                    if (f.sha256 != null && f.sha256.equals(library.archiveSha256, ignoreCase = true)) Step.UpToDate(library).also { clearUpdate(library.id) }
                    else Step.Found(f, library)
                is LibraryRepository.Found.Failed -> Step.Failed(f.reason, f.detail)
            }
            step(next)
        }
    }

    fun setEnabled(library: LibraryRow, enabled: Boolean) = viewModelScope.launch {
        repository.setEnabled(library.id, enabled)
        refresh()
    }

    fun download(release: LibraryRepository.Found.Release) = viewModelScope.launch {
        step(Step.Busy)
        afterCheck(repository.download(release))
    }

    /** A `.zbl` or `.zip` from the system file picker (FR-LIB-16); read with the archive's size cap. */
    fun file(resolver: ContentResolver, uri: Uri) = viewModelScope.launch {
        step(Step.Busy)
        val (name, bytes) = readPicked(resolver, uri, Limits.ARCHIVE_MAX_BYTES.toInt())
        afterCheck(repository.checkFile(bytes, name))
    }


    private fun afterCheck(checked: LibraryRepository.Checked) {
        step(if (checked.refused) Step.Report(checked, null) else Step.Licence(checked))
    }

    fun accept(checked: LibraryRepository.Checked) = viewModelScope.launch {
        step(Step.Busy)
        val outcome = repository.install(checked)
        (outcome as? LibraryInstaller.Outcome.Installed)?.let { clearUpdate(it.library.id) }
        step(Step.Report(checked, outcome))
        refresh()
    }

    fun askRemove(library: LibraryRow?) { _state.update { it.copy(confirmRemove = library) } }

    fun remove(library: LibraryRow) = viewModelScope.launch {
        repository.remove(library.id)
        _state.update { it.copy(confirmRemove = null) }
        refresh()
    }

    companion object {
        /** Built from the application's [nl.pe1mew.zangbundel.app.AppContainer] (architecture A-17). */
        val Factory = viewModelFactory {
            initializer {
                val container = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ZangbundelApp).container
                LibraryViewModel(container.libraries, container.storage.index, container.storage.settings, container::scheduleUpdateCheck)
            }
        }
    }
}
