// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.gathering

/**
 * One campfire's statistics as guiDesign D-60 lists them (assessment SP-16), written as the JSON the
 * export shares: the date, the songs pushed with their push time from the start and how long each ran,
 * the setlist songs never pushed, the singer count each minute and its maximum, the leader's own radio
 * figures, and the versions. Never a name (not even the gathering's), a device, an address, a clock
 * time finer than the day, or anything about one singer.
 */
internal class CampfireRecord(
    val date: String,
    val durationMs: Long,
    val pushes: List<Push>,
    /** Setlist songs never pushed: `library-id` and `song-id`. */
    val skipped: List<Pair<String, String>>,
    /** Milliseconds from the start, and the singers counted then. */
    val singers: List<Pair<Long, Int>>,
    val maxSingers: Int,
    val radio: Radio,
    val versions: Versions,
) {
    class Push(val library: String, val song: String, val atMs: Long, val durationMs: Long)

    class Radio(
        val legacySets: Int, val extendedSet: Boolean, val framesSent: Long, val repairsServed: Int, val bytesRepaired: Long,
        val peakConnections: Int, val maxConnections: Int, val refusedConnections: Int,
    )

    class Versions(val app: String, val songFormat: String, val archive: String, val protocol: String)

    fun json(): String = buildString {
        append("{\"format\":\"zangbundel-statistics\",\"version\":1")
        append(",\"date\":").append(str(date)).append(",\"durationMs\":").append(durationMs)
        append(",\"songs\":[")
        pushes.forEachIndexed { i, p ->
            if (i > 0) append(',')
            append("{\"library\":").append(str(p.library)).append(",\"song\":").append(str(p.song))
                .append(",\"atMs\":").append(p.atMs).append(",\"durationMs\":").append(p.durationMs).append('}')
        }
        append("],\"skipped\":[")
        skipped.forEachIndexed { i, (l, s) ->
            if (i > 0) append(',')
            append("{\"library\":").append(str(l)).append(",\"song\":").append(str(s)).append('}')
        }
        append("],\"singers\":[")
        singers.forEachIndexed { i, (at, n) ->
            if (i > 0) append(',')
            append('[').append(at).append(',').append(n).append(']')
        }
        append("],\"maxSingers\":").append(maxSingers)
        with(radio) {
            append(",\"radio\":{\"legacySets\":").append(legacySets).append(",\"extendedSet\":").append(extendedSet)
                .append(",\"framesSent\":").append(framesSent).append(",\"repairsServed\":").append(repairsServed)
                .append(",\"bytesRepaired\":").append(bytesRepaired).append(",\"peakConnections\":").append(peakConnections)
                .append(",\"maxConnections\":").append(maxConnections).append(",\"refusedConnections\":").append(refusedConnections).append('}')
        }
        with(versions) {
            append(",\"versions\":{\"app\":").append(str(app)).append(",\"songFormat\":").append(str(songFormat))
                .append(",\"archive\":").append(str(archive)).append(",\"protocol\":").append(str(protocol)).append('}')
        }
        append('}')
    }

    private companion object {
        /** A JSON string; the values are identifiers, dates and versions, escaped all the same. */
        fun str(s: String): String = buildString {
            append('"')
            for (c in s) when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
            append('"')
        }
    }
}
