// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app

import android.app.Application
import nl.pe1mew.zangbundel.platform.storage.StartupSweep

/** Creates the one [AppContainer] (manual dependency injection, architecture A-17) and runs the startup sweep. */
class ZangbundelApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        StartupSweep(this).run() // before anything else (architecture 5.4, FR-DATA-08)
        container = AppContainer(this)
    }
}
