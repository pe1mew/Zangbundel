// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.core.protocol.beacon.Fingerprint

/**
 * The two words of a campfire in the app's language (FR-GATH-18, ble-protocol 6.6, guiDesign D-36), looked up
 * when the screen is drawn, so they follow a change of language at once. Empty while the campfire is unknown.
 */
@Composable
fun fingerprintText(key: Fingerprint.Key?): String {
    val first = stringArrayResource(R.array.fingerprint_words_1)
    val second = stringArrayResource(R.array.fingerprint_words_2)
    return key?.let { Fingerprint.words(it, first.asList(), second.asList()) } ?: ""
}
