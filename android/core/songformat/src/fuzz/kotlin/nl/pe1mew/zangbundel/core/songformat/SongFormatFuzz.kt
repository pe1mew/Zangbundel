// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.songformat

import com.code_intelligence.jazzer.junit.FuzzTest

/**
 * Jazzer targets for every parser of untrusted library input (test plan 3.4, NFR-SEC-04, assessment
 * SP-09; release check G-09). These parsers report problems and never throw: any exception is a finding.
 * Their own size bounds (song-format 5.5, 12.2, 12.8, 13.1) keep each input's work small, so a hang or an
 * out-of-memory is a finding too. Without JAZZER_FUZZ=1 each target runs its fixed inputs only.
 */
class SongFormatFuzz {
    @FuzzTest(maxDuration = MAX)
    fun songParser(data: ByteArray) {
        SongParser.parse(data, "songs/nl/fuzz.cho", "fuzz.cho")
    }

    @FuzzTest(maxDuration = MAX)
    fun setlistParser(data: ByteArray) {
        SetlistParser.parse(data, "setlists/fuzz.zbs", "fuzz.zbs", library = "fuzz", songIds = setOf("advocaatje", "vader-jacob"))
    }

    @FuzzTest(maxDuration = MAX)
    fun libraryFile(data: ByteArray) {
        LibraryFileParser.parse(data, APP_VERSION)
    }

    @FuzzTest(maxDuration = MAX)
    fun releaseIndex(data: ByteArray) {
        ReleaseIndex.parse(data)
    }

    @FuzzTest(maxDuration = MAX)
    fun archiveManifest(data: ByteArray) {
        ArchiveImport.parseManifest(data)
    }

    /** The ZIP reader and everything behind it: manifest, library file, songs, setlists (song-format 12.8). */
    @FuzzTest(maxDuration = MAX)
    fun archive(data: ByteArray) {
        ArchiveImport.open(data, APP_VERSION)
    }

    private companion object {
        const val APP_VERSION = "0.1.0"
        /** The CI budget of test plan 3.4; the release run passes a longer one (scripts/fuzz.sh). */
        const val MAX = "60s"
    }
}
