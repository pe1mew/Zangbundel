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
import kotlinx.coroutines.launch
import nl.pe1mew.zangbundel.app.ZangbundelApp
import nl.pe1mew.zangbundel.app.library.DefaultLibrary
import nl.pe1mew.zangbundel.app.library.LibraryRepository
import nl.pe1mew.zangbundel.app.library.readPicked
import nl.pe1mew.zangbundel.core.songformat.Limits
import nl.pe1mew.zangbundel.platform.storage.LibraryIndex
import nl.pe1mew.zangbundel.platform.storage.LibraryInstaller
import nl.pe1mew.zangbundel.platform.storage.LibraryRow

/**
 * The *Bibliotheek* screen's state (guiDesign D-50): the installed libraries and the steps of adding
 * one. Every step waits for the leader's tap; nothing downloads or installs on its own (FR-LIB-12).
 */
class LibraryViewModel(private val repository: LibraryRepository, private val index: LibraryIndex) : ViewModel() {

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

    data class State(val libraries: List<LibraryRow> = emptyList(), val step: Step? = null, val confirmRemove: LibraryRow? = null) {
        /** The default library is offered while it is not installed (FR-LIB-02). */
        val offerDefault: Boolean get() = libraries.none { it.id == DefaultLibrary.ID }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    init { refresh() }

    fun refresh() = viewModelScope.launch { _state.value = _state.value.copy(libraries = index.all().sortedWith(DefaultLibrary.order)) }

    /** The leader's tap on the default library: looked up like a pasted URL, nothing downloads yet. */
    fun addDefault() = find(DefaultLibrary.URL)

    fun add() { _state.value = _state.value.copy(step = Step.Choose) }
    fun cancel() { _state.value = _state.value.copy(step = null) }

    fun find(url: String) = viewModelScope.launch {
        _state.value = _state.value.copy(step = Step.Busy)
        _state.value = _state.value.copy(step = when (val f = repository.find(url)) {
            is LibraryRepository.Found.Release -> Step.Found(f)
            is LibraryRepository.Found.Failed -> Step.Failed(f.reason, f.detail)
        })
    }

    /**
     * *Bijwerken* (FR-LIB-11, FR-LIB-12): looks up the newest release where the library came from and
     * shows it before anything downloads; a library from a file without an index goes to the file picker.
     */
    fun update(library: LibraryRow) {
        val source = repository.updateSource(library) ?: return add()
        viewModelScope.launch {
            _state.value = _state.value.copy(step = Step.Busy)
            _state.value = _state.value.copy(step = when (val f = repository.find(source)) {
                is LibraryRepository.Found.Release ->
                    if (f.sha256 != null && f.sha256.equals(library.archiveSha256, ignoreCase = true)) Step.UpToDate(library) else Step.Found(f, library)
                is LibraryRepository.Found.Failed -> Step.Failed(f.reason, f.detail)
            })
        }
    }

    fun setEnabled(library: LibraryRow, enabled: Boolean) = viewModelScope.launch {
        repository.setEnabled(library.id, enabled)
        refresh()
    }

    fun download(release: LibraryRepository.Found.Release) = viewModelScope.launch {
        _state.value = _state.value.copy(step = Step.Busy)
        afterCheck(repository.download(release))
    }

    /** A `.zbl` or `.zip` from the system file picker (FR-LIB-16); read with the archive's size cap. */
    fun file(resolver: ContentResolver, uri: Uri) = viewModelScope.launch {
        _state.value = _state.value.copy(step = Step.Busy)
        val (name, bytes) = readPicked(resolver, uri, Limits.ARCHIVE_MAX_BYTES.toInt())
        afterCheck(repository.checkFile(bytes, name))
    }


    private fun afterCheck(checked: LibraryRepository.Checked) {
        _state.value = _state.value.copy(step = if (checked.refused) Step.Report(checked, null) else Step.Licence(checked))
    }

    fun accept(checked: LibraryRepository.Checked) = viewModelScope.launch {
        _state.value = _state.value.copy(step = Step.Busy)
        val outcome = repository.install(checked)
        _state.value = _state.value.copy(step = Step.Report(checked, outcome))
        refresh()
    }

    fun askRemove(library: LibraryRow?) { _state.value = _state.value.copy(confirmRemove = library) }

    fun remove(library: LibraryRow) = viewModelScope.launch {
        repository.remove(library.id)
        _state.value = _state.value.copy(confirmRemove = null)
        refresh()
    }

    companion object {
        /** Built from the application's [nl.pe1mew.zangbundel.app.AppContainer] (architecture A-17). */
        val Factory = viewModelFactory {
            initializer {
                val container = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ZangbundelApp).container
                LibraryViewModel(container.libraries, container.storage.index)
            }
        }
    }
}
