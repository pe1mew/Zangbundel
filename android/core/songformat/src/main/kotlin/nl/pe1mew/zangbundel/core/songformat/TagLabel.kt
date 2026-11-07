// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.songformat

import java.util.Locale

/** Which label a tag is shown by (song format 4.2 as of v0.28), for `zangbundel.json` and a package's manifest alike. */
object TagLabel {
    /**
     * The first that exists among [byLanguage] (language code → label): the app's language [appLanguage] (a BCP 47
     * tag such as `nl-BE`) exactly, its primary subtag (`nl`), `en`, and the label whose code sorts first in ordinal
     * order of the lower-cased codes. Codes are compared case-insensitively. Null when there is no label: the caller
     * shows the tag id.
     */
    fun choose(byLanguage: Map<String, String>, appLanguage: String): String? {
        if (byLanguage.isEmpty()) return null
        val sorted = byLanguage.entries.sortedBy { it.key.lowercase(Locale.ROOT) }
        fun find(code: String) = sorted.firstOrNull { it.key.equals(code, ignoreCase = true) }?.value
        val app = appLanguage.lowercase(Locale.ROOT)
        return find(app) ?: find(app.substringBefore('-')) ?: find("en") ?: sorted.first().value
    }
}
