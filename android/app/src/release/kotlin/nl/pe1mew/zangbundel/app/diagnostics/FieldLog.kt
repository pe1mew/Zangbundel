// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.diagnostics

import android.content.Context
import androidx.compose.runtime.Composable

/**
 * Release stand-in: the store build keeps no field-test log and asks no field-test consent (architecture
 * A-20). The real versions live in `src/radiocheck`.
 */
@Suppress("UNUSED_PARAMETER")
object FieldLog {
    fun needsConsent(context: Context): Boolean = false
    fun consent(context: Context) = Unit
    fun active(context: Context): Boolean = false
    fun start(context: Context, role: String, fields: Map<String, Any?>) = Unit
    fun sample(context: Context, t: Long, fields: Map<String, Any?>) = Unit
    fun end(context: Context, t: Long, fields: Map<String, Any?>) = Unit
    const val SAMPLE_MS = 60_000L
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun FieldConsentScreen(onAccept: () -> Unit, onDecline: () -> Unit) = Unit
