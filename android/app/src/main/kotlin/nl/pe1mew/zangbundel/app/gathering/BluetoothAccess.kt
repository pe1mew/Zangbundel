// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.gathering

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * What a gathering needs from the system (FR-GEN-05, guiDesign D-22): on Android 12 and later the
 * nearby-devices permissions (scan without location, `neverForLocation`); on Android 8 to 11 the
 * location permission, which Android requires for a Bluetooth scan only. Asked only after the
 * user's tap on the explanation card, never on opening a screen.
 */
object BluetoothAccess {
    fun permissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun granted(context: Context): Boolean = permissions().all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    fun enabled(context: Context): Boolean =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled == true

    fun ready(context: Context): Boolean = granted(context) && enabled(context)
}
