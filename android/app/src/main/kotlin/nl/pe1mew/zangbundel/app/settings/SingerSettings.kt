// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.settings

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The two-phone test's switch for the reception island, for as long as the process lives. Singers
 * have no setting since guiDesign v0.30: their grid opens on a long press (D-81, FR-SING-05).
 */
class SingerSettings {
    val receptionBar = MutableStateFlow(false)
}
