// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app

import android.bluetooth.BluetoothManager
import android.content.Context
import nl.pe1mew.zangbundel.core.protocol.Crypto
import nl.pe1mew.zangbundel.core.protocol.Transport
import nl.pe1mew.zangbundel.platform.ble.AndroidTransport
import nl.pe1mew.zangbundel.platform.crypto.SodiumCrypto
import nl.pe1mew.zangbundel.platform.net.HttpsFetcher
import nl.pe1mew.zangbundel.platform.storage.ZangbundelDatabase

/**
 * Wires the app's objects by hand (architecture A-17). Everything is created lazily, so a screen
 * that never touches the radio never initialises Bluetooth.
 */
class AppContainer(private val context: Context) {

    val database: ZangbundelDatabase by lazy { ZangbundelDatabase.open(context) }

    val crypto: Crypto by lazy { SodiumCrypto() }

    /** Null when the device has no Bluetooth adapter at all; such a device can neither lead nor join. */
    val transport: Transport? by lazy {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        adapter?.let { AndroidTransport(context, it) }
    }

    val fetcher: HttpsFetcher by lazy {
        val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
        HttpsFetcher(versionName)
    }
}
