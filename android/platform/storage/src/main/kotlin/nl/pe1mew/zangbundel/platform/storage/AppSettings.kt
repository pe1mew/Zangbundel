// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.platform.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The settings kept across starts (architecture section 6: DataStore `settings.preferences_pb`, removed
 * by "remove all data"). The reception grid has no setting (guiDesign D-81 as of v0.30). The lyric view's
 * theme, text size and keep-screen-on are set from the singer's toolbar (D-17) and remembered per device.
 */
class AppSettings private constructor(private val store: DataStore<Preferences>) {

    /** *Naam voor de leider* (FR-SING-01, guiDesign D-44): empty unless the singer typed one; never prefilled. */
    val displayName: Flow<String> = store.data.map { it[DISPLAY_NAME].orEmpty() }

    /** Whether the first-use introduction was finished (guiDesign D-07). */
    val firstUseDone: Flow<Boolean> = store.data.map { it[FIRST_USE_DONE] ?: false }

    /** The lyric view's theme (D-15): night unless the singer chose day. */
    val lyricNight: Flow<Boolean> = store.data.map { it[LYRIC_NIGHT] ?: true }

    /** The step of D-18's five lyric sizes, 0 to [LYRIC_SIZES] - 1; 28 sp by default. */
    val lyricSize: Flow<Int> = store.data.map { (it[LYRIC_SIZE] ?: LYRIC_SIZE_DEFAULT).coerceIn(0, LYRIC_SIZES - 1) }

    /** FR-DISP-07: the screen stays on in a gathering unless the singer switched it off. */
    val keepScreenOn: Flow<Boolean> = store.data.map { it[KEEP_SCREEN_ON] ?: true }

    /** guiDesign D-28: fit-to-width, per orientation; off in portrait and on in landscape until the singer changes it. */
    val fitPortrait: Flow<Boolean> = store.data.map { it[FIT_PORTRAIT] ?: false }
    val fitLandscape: Flow<Boolean> = store.data.map { it[FIT_LANDSCAPE] ?: true }

    suspend fun setFit(landscape: Boolean, on: Boolean) {
        store.edit { it[if (landscape) FIT_LANDSCAPE else FIT_PORTRAIT] = on }
    }

    /** FR-LIB-13 (architecture A-14, SP-29): the automatic check on Wi-Fi, off until the user switches it on. */
    val autoUpdateCheck: Flow<Boolean> = store.data.map { it[AUTO_UPDATE_CHECK] ?: false }

    suspend fun setAutoUpdateCheck(on: Boolean) {
        store.edit { it[AUTO_UPDATE_CHECK] = on }
    }

    /** What the check found, by library id: the newer release's version, or empty when the index names none. */
    val availableUpdates: Flow<Map<String, String>> = store.data.map { prefs ->
        prefs.asMap().mapNotNull { (k, v) -> if (k.name.startsWith(UPDATE_PREFIX)) k.name.removePrefix(UPDATE_PREFIX) to v.toString() else null }.toMap()
    }

    /** Records what the check found for library [id]; null when it is current. */
    suspend fun setAvailableUpdate(id: String, version: String?) {
        store.edit { if (version == null) it.remove(stringPreferencesKey(UPDATE_PREFIX + id)) else it[stringPreferencesKey(UPDATE_PREFIX + id)] = version }
    }

    /** The release index's `ETag` from the last check of library [id], for a conditional request (song-format 12.7). */
    suspend fun indexEtag(id: String): String? = store.data.first()[stringPreferencesKey(ETAG_PREFIX + id)]

    suspend fun setIndexEtag(id: String, etag: String?) {
        store.edit { if (etag == null) it.remove(stringPreferencesKey(ETAG_PREFIX + id)) else it[stringPreferencesKey(ETAG_PREFIX + id)] = etag }
    }

    suspend fun setLyricNight(night: Boolean) {
        store.edit { it[LYRIC_NIGHT] = night }
    }

    suspend fun setLyricSize(step: Int) {
        store.edit { it[LYRIC_SIZE] = step.coerceIn(0, LYRIC_SIZES - 1) }
    }

    suspend fun setKeepScreenOn(on: Boolean) {
        store.edit { it[KEEP_SCREEN_ON] = on }
    }

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

        /** guiDesign D-18: five steps, 18, 22, 28, 36 and 48 sp; the third is the default. */
        const val LYRIC_SIZES = 5
        const val LYRIC_SIZE_DEFAULT = 2
        private val LYRIC_NIGHT = booleanPreferencesKey("lyric_night")
        private val LYRIC_SIZE = intPreferencesKey("lyric_size")
        private val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        private val FIT_PORTRAIT = booleanPreferencesKey("fit_portrait")
        private val FIT_LANDSCAPE = booleanPreferencesKey("fit_landscape")
        private val AUTO_UPDATE_CHECK = booleanPreferencesKey("auto_update_check")
        private const val UPDATE_PREFIX = "update_available_"
        private const val ETAG_PREFIX = "index_etag_"
        private val Context.settingsStore by preferencesDataStore(name = "settings")

        fun open(context: Context) = AppSettings(context.applicationContext.settingsStore)
    }
}
