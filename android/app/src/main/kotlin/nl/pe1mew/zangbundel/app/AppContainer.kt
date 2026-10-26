// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app

import android.bluetooth.BluetoothManager
import android.content.Context
import nl.pe1mew.zangbundel.app.library.HttpsDownloads
import nl.pe1mew.zangbundel.app.library.LibraryRepository
import nl.pe1mew.zangbundel.app.settings.SingerSettings
import nl.pe1mew.zangbundel.core.protocol.Crypto
import nl.pe1mew.zangbundel.core.protocol.Transport
import nl.pe1mew.zangbundel.platform.ble.AndroidTransport
import nl.pe1mew.zangbundel.platform.crypto.SodiumCrypto
import nl.pe1mew.zangbundel.platform.net.HttpsFetcher
import nl.pe1mew.zangbundel.platform.storage.LibraryInstaller
import nl.pe1mew.zangbundel.platform.storage.LibraryStorage

/**
 * Wires the app's objects by hand (architecture A-17). Everything is created lazily, so a screen
 * that never touches the radio never initialises Bluetooth.
 */
class AppContainer(private val context: Context) {

    val storage: LibraryStorage by lazy { LibraryStorage.open(context) }

    val crypto: Crypto by lazy { SodiumCrypto() }

    /** Off at every start (D-81): created with the process, never stored. */
    val singerSettings = SingerSettings()

    /** Null when the device has no Bluetooth adapter at all; such a device can neither lead nor join. */
    val transport: Transport? by lazy {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        adapter?.let { AndroidTransport(context, it) }
    }

    private val versionName: String by lazy { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0" }

    val fetcher: HttpsFetcher by lazy { HttpsFetcher(versionName) }

    /** Adding, updating and removing libraries (guiDesign D-50); files under `filesDir/libraries/` (architecture section 6). */
    val libraries: LibraryRepository by lazy {
        LibraryRepository(HttpsDownloads(fetcher), LibraryInstaller(storage.files, storage.index), storage.index, versionName)
    }
}
