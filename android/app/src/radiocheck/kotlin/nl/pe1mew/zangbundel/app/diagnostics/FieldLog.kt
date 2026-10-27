// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.diagnostics

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.edit
import java.io.File
import java.time.LocalDate

/**
 * The field-test log (architecture A-20, test plan 5.1, assessment SP-22): one JSON object per line in a
 * file on this phone, which the tester shares by hand from the radio check. It holds the SP-16 radio
 * figures, the Android version, the radio's capability flags and the battery level over time, with times
 * counted from the start of each session and the date only; never a name, a gathering name, a song, a key,
 * a token or a phone's address. Kept only by the `.fieldtest` app, and only after the tester consented on
 * its first start (test plan 5.5); the debug build compiles it but never writes. Callers pass counts and
 * flags; the log adds the platform values itself.
 */
object FieldLog {
    fun isFieldTest(context: Context): Boolean = context.packageName.endsWith(".fieldtest")

    fun needsConsent(context: Context): Boolean = isFieldTest(context) && !prefs(context).getBoolean(KEY_CONSENT, false)

    fun consent(context: Context) {
        prefs(context).edit { putBoolean(KEY_CONSENT, true) }
    }

    fun active(context: Context): Boolean = isFieldTest(context) && prefs(context).getBoolean(KEY_CONSENT, false)

    /** A session begins: the role, the date, the Android version and the radio's capability flags. */
    fun start(context: Context, role: String, fields: Map<String, Any?>) = append(
        context,
        mapOf("e" to "start", "t" to 0L, "role" to role, "date" to LocalDate.now().toString(),
            "android" to Build.VERSION.RELEASE.substringBefore('.'), "sdk" to Build.VERSION.SDK_INT) + fields,
    )

    /** A sample during the session, with the battery level and whether the phone charges. */
    fun sample(context: Context, t: Long, fields: Map<String, Any?>) =
        append(context, mapOf("e" to "sample", "t" to t) + battery(context) + fields)

    fun end(context: Context, t: Long, fields: Map<String, Any?>) =
        append(context, mapOf("e" to "end", "t" to t) + battery(context) + fields)

    fun text(context: Context): String = synchronized(this) { file(context).takeIf { it.exists() }?.readText().orEmpty() }

    fun clear(context: Context) {
        synchronized(this) { file(context).delete() }
    }

    private fun append(context: Context, fields: Map<String, Any?>) {
        if (!active(context)) return
        synchronized(this) {
            val f = file(context)
            if (f.length() > MAX_BYTES) return      // four hours at one sample a minute stay far below this
            f.parentFile?.mkdirs()
            f.appendText(json(fields) + "\n")
        }
    }

    private fun battery(context: Context): Map<String, Any?> {
        val bm = context.getSystemService(BatteryManager::class.java) ?: return emptyMap()
        return mapOf("battery" to bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY), "charging" to bm.isCharging)
    }

    /** Only numbers, booleans, strings and nested maps of them; strings are escaped. */
    internal fun json(fields: Map<String, Any?>): String = fields.entries.joinToString(",", "{", "}") { (k, v) -> quote(k) + ":" + value(v) }

    private fun value(v: Any?): String = when (v) {
        null -> "null"
        is Number, is Boolean -> v.toString()
        is Map<*, *> -> json(v.entries.associate { it.key.toString() to it.value })
        else -> quote(v.toString())
    }

    private fun quote(s: String): String = buildString {
        append('"')
        for (c in s) when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c < ' ' -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
        append('"')
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun file(context: Context) = File(File(context.noBackupFilesDir, "fieldtest"), "log.jsonl")

    private const val PREFS = "fieldtest"
    private const val KEY_CONSENT = "consent"
    private const val MAX_BYTES = 4L shl 20
    /** One sample a minute (test plan F-04, F-05: battery curves over hours). */
    const val SAMPLE_MS = 60_000L
}
