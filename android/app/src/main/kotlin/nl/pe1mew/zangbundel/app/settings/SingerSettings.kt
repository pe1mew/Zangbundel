// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.settings

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Singer settings that live only as long as the app process. *Ontvangst tonen* (guiDesign D-81,
 * FR-SING-05) is off whenever the app starts, so it is deliberately never stored.
 */
class SingerSettings {
    val receptionBar = MutableStateFlow(false)
}
