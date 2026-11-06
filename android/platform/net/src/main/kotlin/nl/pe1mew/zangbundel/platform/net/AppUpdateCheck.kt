// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.platform.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException

/**
 * The app update check on *Over* (FR-GEN-09, guiDesign D-86, architecture A-25): one GET of the app's
 * latest GitHub release, the single source of truth for versions. Started only by the user's tap, never
 * retried, nothing remembered (FR-NET-02). The answer is untrusted (assessment SP-09, SP-38): capped,
 * and only `tag_name` and `html_url` are read.
 */
class AppUpdateCheck(private val fetcher: HttpsFetcher) {

    sealed interface Result {
        /** A release newer than [installed] exists; [page] is its GitHub page. */
        data class Newer(val latest: String, val installed: String, val page: String) : Result
        /** The installed version is the latest release. */
        data class Current(val latest: String) : Result
        /** The installed build is newer than the latest release, as a test build is. */
        data class Ahead(val latest: String, val installed: String) : Result
        /** GitHub not reached, or an answer that cannot be read. */
        data object Unreachable : Result
    }

    /** The latest release as far as this check reads it. */
    data class Latest(val version: String, val page: String)

    /** The fetch runs outside the caller's scope, so the caller gets its answer after [TIMEOUT_MS] even while a blocked socket is still timing out. */
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun check(installedVersionName: String): Result {
        val latest = withTimeoutOrNull(TIMEOUT_MS) { io.async { fetchLatest() }.await() } ?: return Result.Unreachable
        return compare(installedVersionName, latest)
    }

    private fun fetchLatest(): Latest? = try {
        fetcher.open(LATEST_URL, maxBytes = MAX_BYTES, connectTimeoutMs = 8_000, readTimeoutMs = 7_000).use { r ->
            if (r.code != 200) null else parse(r.body.readBytes())
        }
    } catch (e: Exception) {
        null   // no network, a timeout, TLS, the cap: all "not reachable" to the user (D-86)
    }

    /** Where the app was installed from, and the page that opens there (A-25). */
    enum class Store { PLAY, FDROID, GITHUB }

    companion object {
        const val LATEST_URL = "https://api.github.com/repos/pe1mew/Zangbundel/releases/latest"
        const val RELEASES_PREFIX = "https://github.com/pe1mew/Zangbundel/releases/"
        const val RELEASES_LATEST = "https://github.com/pe1mew/Zangbundel/releases/latest"
        /** A release answer is a few kilobytes; its notes are short. */
        const val MAX_BYTES = 256L * 1024
        /** D-86: the user waits at most this long for an answer. */
        const val TIMEOUT_MS = 15_000L

        private const val TAG_MAX = 32
        /** GitHub's answer nests four deep (release, assets, asset, uploader); deeper input is refused before parsing. */
        private const val MAX_DEPTH = 16

        /** Deepest nesting of `{` and `[` outside strings. */
        private fun depth(text: String): Int {
            var depth = 0; var max = 0; var inString = false; var escaped = false
            for (c in text) {
                if (inString) {
                    when { escaped -> escaped = false; c == '\\' -> escaped = true; c == '"' -> inString = false }
                    continue
                }
                when (c) {
                    '"' -> inString = true
                    '{', '[' -> { depth++; if (depth > max) max = depth }
                    '}', ']' -> depth--
                }
            }
            return max
        }
        private val VERSION = Regex("^v?(\\d{1,6})\\.(\\d{1,6})\\.(\\d{1,6})$")
        /** The three numbers at the start of a versionName, before a suffix such as `-review`. */
        private val INSTALLED = Regex("^(\\d{1,6})\\.(\\d{1,6})\\.(\\d{1,6})(?:[-+].*)?$")

        private val PLAY_INSTALLERS = setOf("com.android.vending")
        private val FDROID_INSTALLERS = setOf("org.fdroid.fdroid", "org.fdroid.basic", "com.looker.droidify", "com.machiav3lli.fdroid")

        /** Reads GitHub's release answer; null when it is not one this check can use. */
        fun parse(bytes: ByteArray): Latest? {
            if (bytes.size > MAX_BYTES) return null
            val text = try { Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString() } catch (e: CharacterCodingException) { return null }
            if (depth(text) > MAX_DEPTH) return null
            val root = try { Json.parseToJsonElement(text) as? JsonObject } catch (e: Exception) { null } ?: return null
            fun str(k: String) = (root[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val tag = str("tag_name")?.takeIf { it.length <= TAG_MAX } ?: return null
            val m = VERSION.matchEntire(tag) ?: return null
            val version = m.groupValues.drop(1).joinToString(".") { it.toInt().toString() }
            val page = str("html_url")?.takeIf { it.startsWith(RELEASES_PREFIX) && it.length <= 200 && it.none(Char::isWhitespace) } ?: RELEASES_LATEST
            return Latest(version, page)
        }

        /** Compares by the three numbers; an installed name without them counts as older, so the newest release is offered. */
        fun compare(installedVersionName: String, latest: Latest): Result {
            val mine = INSTALLED.matchEntire(installedVersionName)?.groupValues?.drop(1)?.map { it.toInt() }
            val theirs = latest.version.split('.').map { it.toInt() }
            val shown = mine?.joinToString(".") ?: installedVersionName
            val order = if (mine == null) -1 else mine.zip(theirs).map { (a, b) -> a.compareTo(b) }.firstOrNull { it != 0 } ?: 0
            return when {
                order < 0 -> Result.Newer(latest.version, shown, latest.page)
                order == 0 -> Result.Current(latest.version)
                else -> Result.Ahead(latest.version, shown)
            }
        }

        /** The store that installed the app, from the installer's package name as Android reports it. */
        fun storeFor(installer: String?): Store = when (installer) {
            in PLAY_INSTALLERS -> Store.PLAY
            in FDROID_INSTALLERS -> Store.FDROID
            else -> Store.GITHUB
        }

        /** The address to open for [store]; [page] is the release page from the answer. */
        fun pageFor(store: Store, packageName: String, page: String): String = when (store) {
            Store.PLAY -> "https://play.google.com/store/apps/details?id=$packageName"
            Store.FDROID -> "https://f-droid.org/packages/$packageName/"
            Store.GITHUB -> page
        }
    }
}
