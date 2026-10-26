// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.platform.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The settings kept across starts (architecture section 6: DataStore `settings.preferences_pb`, removed
 * by "remove all data"). *Ontvangst tonen* is not here: it is off at every start (guiDesign D-81).
 */
class AppSettings private constructor(private val store: DataStore<Preferences>) {

    /** *Naam voor de leider* (FR-SING-01, guiDesign D-44): empty unless the singer typed one; never prefilled. */
    val displayName: Flow<String> = store.data.map { it[DISPLAY_NAME].orEmpty() }

    /** Whether the first-use introduction was finished (guiDesign D-07). */
    val firstUseDone: Flow<Boolean> = store.data.map { it[FIRST_USE_DONE] ?: false }

    suspend fun displayName(): String = displayName.first()

    suspend fun setDisplayName(name: String) {
        store.edit { it[DISPLAY_NAME] = name.trim().take(DISPLAY_NAME_MAX) }
    }

    suspend fun setFirstUseDone() {
        store.edit { it[FIRST_USE_DONE] = true }
    }

    /** "Remove all data" (FR-DATA-07): every setting goes, so the app is back at its first use. */
    suspend fun clear() {
        store.edit { it.clear() }
    }

    companion object {
        /** guiDesign D-44. */
        const val DISPLAY_NAME_MAX = 24
        private val DISPLAY_NAME = stringPreferencesKey("display_name")
        private val FIRST_USE_DONE = booleanPreferencesKey("first_use_done")
        private val Context.settingsStore by preferencesDataStore(name = "settings")

        fun open(context: Context) = AppSettings(context.applicationContext.settingsStore)
    }
}
