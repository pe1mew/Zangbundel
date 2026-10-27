// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.gathering

import nl.pe1mew.zangbundel.app.ui.settings.exportJson
import nl.pe1mew.zangbundel.app.ui.settings.songCounts
import nl.pe1mew.zangbundel.platform.storage.CampfireStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** The statistics record of guiDesign D-60 (assessment SP-16): only identifiers, times from the start, counts and versions. */
class CampfireRecordTest {
    private fun record(vararg songs: String) = CampfireRecord(
        date = "2026-10-27", durationMs = 3_600_000,
        pushes = songs.mapIndexed { i, s -> CampfireRecord.Push("lib", s, i * 600_000L, 600_000) },
        skipped = listOf("lib" to "niet-gezongen"),
        singers = listOf(0L to 3, 60_000L to 5), maxSingers = 5,
        radio = CampfireRecord.Radio(3, true, 12_000, 4, 9_000, 2, 6, 0),
        versions = CampfireRecord.Versions("0.1.0", "1.1", "1.1", "1.0"),
    )

    @Test
    fun `the record is the JSON of D-60, and nothing else`() {
        assertEquals(
            "{\"format\":\"zangbundel-statistics\",\"version\":1,\"date\":\"2026-10-27\",\"durationMs\":3600000," +
                "\"songs\":[{\"library\":\"lib\",\"song\":\"advocaatje\",\"atMs\":0,\"durationMs\":600000}]," +
                "\"skipped\":[{\"library\":\"lib\",\"song\":\"niet-gezongen\"}],\"singers\":[[0,3],[60000,5]],\"maxSingers\":5," +
                "\"radio\":{\"legacySets\":3,\"extendedSet\":true,\"framesSent\":12000,\"repairsServed\":4,\"bytesRepaired\":9000," +
                "\"peakConnections\":2,\"maxConnections\":6,\"refusedConnections\":0}," +
                "\"versions\":{\"app\":\"0.1.0\",\"songFormat\":\"1.1\",\"archive\":\"1.1\",\"protocol\":\"1.0\"}}",
            record("advocaatje").json(),
        )
    }

    @Test
    fun `a value with quotes cannot break out of its string`() {
        val json = record("a\"b\\c").json()
        assertFalse(json.contains("a\"b"), json)
        assertEquals(true, json.contains("\"song\":\"a\\\"b\\\\c\""), json)
    }

    @Test
    fun `the season counts pushed songs, not skipped ones, most first, and the export is an array of the records`() {
        val records = listOf(
            CampfireStats(1, "2026-10-26", 0, 2, 3, record("advocaatje", "vader-jacob").json()),
            CampfireStats(2, "2026-10-27", 0, 1, 4, record("advocaatje").json()),
        )
        assertEquals(listOf("lib/advocaatje" to 2, "lib/vader-jacob" to 1), songCounts(records))
        val exported = exportJson(records)
        assertEquals(true, exported.startsWith("[\n{") && exported.endsWith("}\n]\n"), exported)
    }
}
