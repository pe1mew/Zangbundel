// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.library

import nl.pe1mew.zangbundel.app.R

/**
 * A sentence for each report code (song-format 11.3, 12.8, 13.6), so the report names the reason in
 * words and tells the user to update the app where that is the remedy (4.3). Info codes are not shown.
 */
internal object ReportText {
    private val text = mapOf(
        "L001" to R.string.report_l001,
        "L002" to R.string.report_l002,
        "L003" to R.string.report_l003,
        "L004" to R.string.report_l004,
        "L005" to R.string.report_l005,
        "L006" to R.string.report_l006,
        "L007" to R.string.report_l007,
        "L008" to R.string.report_l008,
        "S000" to R.string.report_s000,
        "S001" to R.string.report_s001,
        "S002" to R.string.report_s002,
        "S003" to R.string.report_s003,
        "S004" to R.string.report_s004,
        "S005" to R.string.report_s005,
        "S006" to R.string.report_s006,
        "S007" to R.string.report_s007,
        "S008" to R.string.report_s008,
        "S009" to R.string.report_s009,
        "S010" to R.string.report_s010,
        "S011" to R.string.report_s011,
        "S012" to R.string.report_s012,
        "W001" to R.string.report_w001,
        "W002" to R.string.report_w002,
        "W003" to R.string.report_w003,
        "W004" to R.string.report_w004,
        "W005" to R.string.report_w005,
        "W006" to R.string.report_w006,
        "W007" to R.string.report_w007,
        "W008" to R.string.report_w008,
        "W009" to R.string.report_w009,
        "W010" to R.string.report_w010,
        "W011" to R.string.report_w011,
        "W012" to R.string.report_w012,
        "A001" to R.string.report_a001,
        "A002" to R.string.report_a002,
        "A003" to R.string.report_a003,
        "A004" to R.string.report_a004,
        "A005" to R.string.report_a005,
        "A006" to R.string.report_a006,
        "A007" to R.string.report_a007,
        "A008" to R.string.report_a008,
        "A009" to R.string.report_a009,
        "A010" to R.string.report_a010,
        "A011" to R.string.report_a011,
        "A012" to R.string.report_a012,
        "A013" to R.string.report_a013,
        "A101" to R.string.report_a101,
        "A102" to R.string.report_a102,
        "A103" to R.string.report_a103,
        "A104" to R.string.report_a104,
        "SL001" to R.string.report_sl001,
        "SL002" to R.string.report_sl002,
        "SL003" to R.string.report_sl003,
        "SL004" to R.string.report_sl004,
        "SL005" to R.string.report_sl005,
        "SL101" to R.string.report_sl101,
        "SL102" to R.string.report_sl102,
        "SL103" to R.string.report_sl103,
        "SL104" to R.string.report_sl104,
        // Library packages: the library manager's rule codes and its messages (FDS §6, §9; guiDesign D-88), and W013.
        "PKG-01" to R.string.report_pkg_01,
        "PKG-02" to R.string.report_pkg_02,
        "PKG-03" to R.string.report_pkg_03,
        "PKG-04" to R.string.report_pkg_04,
        "PKG-05" to R.string.report_pkg_05,
        "PKG-06" to R.string.report_pkg_06,
        "PKG-07" to R.string.report_pkg_07,
        "STR-01" to R.string.report_str_01,
        "STR-05" to R.string.report_str_05,
        "STR-06" to R.string.report_str_06,
        "STR-07" to R.string.report_str_07,
        "STR-08" to R.string.report_str_08,
        "STR-10" to R.string.report_str_10,
        "STR-11" to R.string.report_str_11,
        "MAN-01" to R.string.report_man_01,
        "MAN-02" to R.string.report_man_02,
        "MAN-03" to R.string.report_man_03,
        "MAN-04" to R.string.report_man_04,
        "MAN-05" to R.string.report_man_05,
        "MAN-06" to R.string.report_man_06,
        "MAN-07" to R.string.report_man_07,
        "MAN-08" to R.string.report_man_08,
        "SIG-01" to R.string.report_sig_01,
        "SIG-02" to R.string.report_sig_02,
        "SIG-03" to R.string.report_sig_03,
        "SET-01" to R.string.report_set_01,
        "SET-02" to R.string.report_set_02,
        // A package's song errors carry FDS codes (PackageImport.sngCode) and the profile's sentences.
        "SNG-01" to R.string.report_s001,
        "SNG-02" to R.string.report_s003,
        "SNG-03" to R.string.report_s006,
        "SNG-04" to R.string.report_s011,
        "SNG-06" to R.string.report_s007,
        "SNG-09" to R.string.report_s010,
        "SNG-11" to R.string.report_s003,
        "SNG-12" to R.string.report_s004,
        "SNG-13" to R.string.report_s008,
        "SNG-14" to R.string.report_s009,
        "SNG-15" to R.string.report_s012,
        "SNG-18" to R.string.report_sng_18,
        "VIEW-01" to R.string.report_view_01,
        "VIEW-02" to R.string.report_view_02,
        "VIEW-03" to R.string.report_view_03,
        "W013" to R.string.report_w013,
    )

    /** Null for a code this app does not know; the screen then shows the code alone. */
    fun of(code: String): Int? = text[code]

    /** What fills the `%s` of a sentence that names a file or a reason (guiDesign D-88, the FDS messages). */
    enum class Argument { PATH, DETAIL }

    private val arguments = mapOf(
        "PKG-06" to Argument.PATH, "STR-01" to Argument.PATH, "STR-05" to Argument.PATH, "STR-06" to Argument.PATH,
        "STR-07" to Argument.PATH, "STR-08" to Argument.PATH, "STR-10" to Argument.PATH, "STR-11" to Argument.PATH,
        "MAN-04" to Argument.PATH, "MAN-05" to Argument.PATH, "MAN-06" to Argument.PATH, "VIEW-02" to Argument.PATH,
        "VIEW-01" to Argument.DETAIL, "VIEW-03" to Argument.DETAIL,
    )

    fun argument(code: String): Argument? = arguments[code]
}
