// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.library

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import nl.pe1mew.zangbundel.app.Log
import nl.pe1mew.zangbundel.app.ZangbundelApp
import java.util.concurrent.TimeUnit

/**
 * The opt-in update check on Wi-Fi (FR-LIB-13, architecture A-14, assessment SP-29): the one job of the
 * app that uses the network without a tap. Once a day on an unmetered network it asks each installed
 * library's release index whether a newer release exists and keeps the answer for the library screen
 * (guiDesign D-50). It never downloads an archive and never installs (FR-LIB-12).
 */
class LibraryUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as ZangbundelApp).container
        val settings = container.storage.settings
        if (!settings.autoUpdateCheck.first()) return Result.success()
        for (library in container.storage.index.all()) {
            val outcome = container.libraries.checkUpdate(library, settings.indexEtag(library.id))
            when (outcome) {
                is LibraryRepository.UpdateCheck.Available -> {
                    settings.setAvailableUpdate(library.id, outcome.version.orEmpty())
                    settings.setIndexEtag(library.id, outcome.etag)
                }
                is LibraryRepository.UpdateCheck.Current -> {
                    settings.setAvailableUpdate(library.id, null)
                    settings.setIndexEtag(library.id, outcome.etag)
                }
                else -> Unit
            }
            Log.d("library update check ${library.id}: ${outcome::class.simpleName}")
        }
        return Result.success()
    }

    companion object {
        private const val DAILY = "library-update-check"

        /**
         * Switched on: once a day, only on Wi-Fi; a new periodic job runs its first check as soon as Wi-Fi is
         * there, so that is the check right away (a second, one-time job only doubled the request, seen on
         * the A52 on 2026-10-27). Switched off: cancelled.
         */
        fun schedule(context: Context, on: Boolean) {
            val work = WorkManager.getInstance(context)
            if (!on) {
                work.cancelUniqueWork(DAILY)
                return
            }
            val wifi = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()
            work.enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<LibraryUpdateWorker>(1, TimeUnit.DAYS).setConstraints(wifi).build())
        }
    }
}
