// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nl.pe1mew.zangbundel.platform.storage.AppSettings

/**
 * *Ontvangst tonen* (guiDesign D-81, FR-SING-05): kept across starts since guiDesign v0.28, read once
 * when the app starts and written on every change; the screens read [receptionBar].
 */
class SingerSettings(private val settings: () -> AppSettings, private val scope: CoroutineScope) {
    private val on = MutableStateFlow(false)
    val receptionBar: StateFlow<Boolean> = on

    init {
        scope.launch { on.value = settings().receptionView.first() }
    }

    fun setReceptionView(value: Boolean) {
        on.value = value
        scope.launch { settings().setReceptionView(value) }
    }

    /** After "remove all data", which cleared the stored value with the rest. */
    fun forget() {
        on.value = false
    }
}
