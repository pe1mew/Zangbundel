// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app

import android.bluetooth.BluetoothManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import nl.pe1mew.zangbundel.app.gathering.Gathering
import nl.pe1mew.zangbundel.app.library.HttpsDownloads
import nl.pe1mew.zangbundel.app.library.LibraryRepository
import nl.pe1mew.zangbundel.app.library.SetlistBook
import nl.pe1mew.zangbundel.app.library.SetlistTransfer
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

    /** *Ontvangst tonen* (D-81 as of v0.28), kept in [storage]'s settings. */
    val singerSettings: SingerSettings by lazy { SingerSettings({ storage.settings }, CoroutineScope(SupervisorJob() + Dispatchers.IO)) }

    /** Null when the device has no Bluetooth adapter at all; such a device can neither lead nor join. */
    val transport: Transport? by lazy {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        adapter?.let { AndroidTransport(context, it) }
    }

    /** This app's `major.minor.patch`, as the library checks compare it (song-format 4.3). */
    val versionName: String by lazy { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0" }

    val fetcher: HttpsFetcher by lazy { HttpsFetcher(versionName) }

    /** Prepared setlists and the one being edited (guiDesign D-53, D-54), shared by the screens that fill it. */
    val setlists: SetlistBook by lazy { SetlistBook(storage.setlists) }

    /** `.zbs` import, export and copies of library setlists (guiDesign D-59); the HTTPS path is the libraries' one (A-14). */
    val setlistTransfer: SetlistTransfer by lazy { SetlistTransfer(storage.songbook, setlists, HttpsDownloads(fetcher)) }

    /** The one gathering, leading or singing (architecture A-06); its radio is [transport]. */
    val gathering: Gathering by lazy { Gathering(context, { transport }, crypto, storage.songbook, setlists, storage.memories, storage.settings, { singerSettings.receptionBar.value }) }

    /** Whether the welcome screen was shown in this process (guiDesign D-05: never a second time while the app stays in memory). */
    var welcomed = false

    /**
     * "Remove all data" (FR-DATA-07, architecture section 6): libraries with their files, setlists,
     * gathering memories, settings and the share cache, so the app is back at its first use. Only
     * outside a gathering.
     */
    suspend fun removeAllData() {
        check(gathering.mode.value == Gathering.Mode.Idle) { "not during a gathering" }
        for (l in storage.index.all()) libraries.remove(l.id)
        for (s in storage.setlists.all()) storage.setlists.delete(s.uid)
        storage.memories.deleteAll()
        storage.settings.clear()
        singerSettings.forget()
        java.io.File(context.cacheDir, "share").deleteRecursively()
    }

    /** Adding, updating and removing libraries (guiDesign D-50); files under `filesDir/libraries/` (architecture section 6). */
    val libraries: LibraryRepository by lazy {
        LibraryRepository(HttpsDownloads(fetcher), LibraryInstaller(storage.files, storage.index), storage.index, versionName)
    }
}
