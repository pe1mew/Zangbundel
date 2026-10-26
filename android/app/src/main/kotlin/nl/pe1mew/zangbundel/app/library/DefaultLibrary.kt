// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.library

import nl.pe1mew.zangbundel.platform.storage.LibraryRow

/**
 * The default library of FR-LIB-02 (FRD Q3): the project's own repository, pre-configured but added only
 * when the leader taps it (FR-LIB-12), through the same steps as any other: release, size, licence.
 */
object DefaultLibrary {
    const val ID = "zangbundel-bibliotheek"
    /** As in its `zangbundel.json`; library names are not translated (guiDesign D-77). */
    const val NAME = "Zangbundel bibliotheek"
    const val URL = "https://github.com/pe1mew/Zangbundel-bibliotheek"

    /** D-50: the default library first, the others in the order they were added. */
    val order: Comparator<LibraryRow> = compareBy<LibraryRow>({ it.id != ID }, { it.installedAt })
}
