// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.gathering

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/** The phone's power as guiDesign D-68 needs it: battery saver, the charge in percent (-1 unknown), charging. */
internal data class Power(val saver: Boolean, val percent: Int, val charging: Boolean) {
    /** D-68 before *Starten*: battery saver on, or below 30 % and not on a charger or power bank. */
    val warnBeforeStart: Boolean get() = saver || (percent in 0 until LOW_PERCENT && !charging)
}

/** Read now and every few seconds while shown; no permission is needed for either value. */
@Composable
internal fun rememberPower(): Power {
    val context = LocalContext.current
    var power by remember { mutableStateOf(readPower(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(POLL_MS)
            power = readPower(context)
        }
    }
    return power
}

private fun readPower(context: Context): Power {
    val pm = context.getSystemService(PowerManager::class.java)
    val bm = context.getSystemService(BatteryManager::class.java)
    return Power(
        saver = pm?.isPowerSaveMode == true,
        percent = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 } ?: -1,
        charging = bm?.isCharging == true,
    )
}

/** D-68: the system's battery saver screen; the leader changes it there, the app never does (FRD 2.6). */
internal fun openBatterySettings(context: Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .recoverCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private const val LOW_PERCENT = 30
private const val POLL_MS = 5_000L
