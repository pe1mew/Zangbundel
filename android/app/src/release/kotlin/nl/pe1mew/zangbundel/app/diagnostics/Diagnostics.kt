// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.diagnostics

import androidx.compose.runtime.Composable

/**
 * Release stand-in: the store build has no developer or field-test tools. The real versions live in
 * `src/radiocheck`, compiled into `debug` and `fieldtest` only.
 */
object Diagnostics {
    const val AVAILABLE = false
    const val ROUTE = "radiocheck"
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun RadioCheckEntry(onOpen: () -> Unit) = Unit

@Composable
fun RadioCheckScreen() = Unit
