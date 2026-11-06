// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.platform.net

import nl.pe1mew.zangbundel.platform.net.AppUpdateCheck.Result
import nl.pe1mew.zangbundel.platform.net.AppUpdateCheck.Store
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** FR-GEN-09, architecture A-25, assessment SP-38: the answer's parser, the comparison and the store choice. */
class AppUpdateCheckTest {
    private fun answer(tag: String, url: String = "https://github.com/pe1mew/Zangbundel/releases/tag/$tag") =
        """{"url":"https://api.github.com/repos/pe1mew/Zangbundel/releases/1","html_url":"$url","tag_name":"$tag","draft":false,"prerelease":false,
           "assets":[{"name":"zangbundel.apk","uploader":{"login":"pe1mew"}}],"body":"New: \"quoted\" text with { and ["}""".toByteArray()

    @Test
    fun `GitHub's answer gives the version and the release page`() {
        val latest = AppUpdateCheck.parse(answer("v0.3.0"))!!
        assertEquals("0.3.0", latest.version)
        assertEquals("https://github.com/pe1mew/Zangbundel/releases/tag/v0.3.0", latest.page)
        assertEquals("1.10.2", AppUpdateCheck.parse(answer("1.10.2"))!!.version)
        assertEquals("1.2.3", AppUpdateCheck.parse(answer("v01.02.03"))!!.version)
    }

    @Test
    fun `a page outside the project's releases falls back to the releases page`() {
        assertEquals(AppUpdateCheck.RELEASES_LATEST, AppUpdateCheck.parse(answer("v0.4.0", "https://example.org/zangbundel.apk"))!!.page)
        assertEquals(AppUpdateCheck.RELEASES_LATEST, AppUpdateCheck.parse(answer("v0.4.0", "https://github.com/pe1mew/Other/releases/x"))!!.page)
        assertEquals(AppUpdateCheck.RELEASES_LATEST, AppUpdateCheck.parse(answer("v0.4.0", "http://github.com/pe1mew/Zangbundel/releases/x"))!!.page)
    }

    @Test
    fun `an answer that is not a usable release is refused`() {
        for (tag in listOf("0.3", "v0.3.0-beta", "latest", "v0.3.0 ", "v1234567.0.0", "")) assertNull(AppUpdateCheck.parse(answer(tag)), tag)
        assertNull(AppUpdateCheck.parse("not json".toByteArray()))
        assertNull(AppUpdateCheck.parse("""["tag_name","v0.3.0"]""".toByteArray()))
        assertNull(AppUpdateCheck.parse("""{"tag_name":3}""".toByteArray()))
        assertNull(AppUpdateCheck.parse(byteArrayOf(0xC3.toByte(), 0x28)))
        assertNull(AppUpdateCheck.parse(("[".repeat(17) + "]".repeat(17)).toByteArray()))
        assertNull(AppUpdateCheck.parse(ByteArray((AppUpdateCheck.MAX_BYTES + 1).toInt()) { ' '.code.toByte() }))
    }

    @Test
    fun `brackets inside strings do not count as nesting`() {
        val deepText = """{"tag_name":"v0.3.0","body":"${"[{".repeat(40)}"}"""
        assertEquals("0.3.0", AppUpdateCheck.parse(deepText.toByteArray())!!.version)
    }

    @Test
    fun `the installed version is compared by its three numbers`() {
        val latest = AppUpdateCheck.Latest("0.4.0", "https://github.com/pe1mew/Zangbundel/releases/tag/v0.4.0")
        assertEquals(Result.Newer("0.4.0", "0.3.0", latest.page), AppUpdateCheck.compare("0.3.0", latest))
        assertEquals(Result.Current("0.4.0"), AppUpdateCheck.compare("0.4.0", latest))
        assertEquals(Result.Ahead("0.4.0", "0.10.0"), AppUpdateCheck.compare("0.10.0", latest))
        assertEquals(Result.Ahead("0.4.0", "1.0.0"), AppUpdateCheck.compare("1.0.0", latest))
        assertEquals(Result.Newer("0.4.0", "0.3.9", latest.page), AppUpdateCheck.compare("0.3.9", latest))
    }

    @Test
    fun `a suffix such as -review is ignored and a name without numbers is offered the release`() {
        val latest = AppUpdateCheck.Latest("0.3.0", AppUpdateCheck.RELEASES_LATEST)
        assertEquals(Result.Current("0.3.0"), AppUpdateCheck.compare("0.3.0-review", latest))
        assertEquals(Result.Current("0.3.0"), AppUpdateCheck.compare("0.3.0+debug", latest))
        assertEquals(Result.Newer("0.3.0", "dev", latest.page), AppUpdateCheck.compare("dev", latest))
    }

    @Test
    fun `the installer decides the store and its page`() {
        assertEquals(Store.PLAY, AppUpdateCheck.storeFor("com.android.vending"))
        for (f in listOf("org.fdroid.fdroid", "org.fdroid.basic", "com.looker.droidify", "com.machiav3lli.fdroid")) assertEquals(Store.FDROID, AppUpdateCheck.storeFor(f))
        for (other in listOf(null, "", "com.google.android.packageinstaller", "com.android.shell", "com.huawei.appmarket")) assertEquals(Store.GITHUB, AppUpdateCheck.storeFor(other))
        val pkg = "nl.pe1mew.zangbundel.app"
        assertEquals("https://play.google.com/store/apps/details?id=$pkg", AppUpdateCheck.pageFor(Store.PLAY, pkg, "x"))
        assertEquals("https://f-droid.org/packages/$pkg/", AppUpdateCheck.pageFor(Store.FDROID, pkg, "x"))
        assertEquals("https://github.com/pe1mew/Zangbundel/releases/tag/v0.4.0", AppUpdateCheck.pageFor(Store.GITHUB, pkg, "https://github.com/pe1mew/Zangbundel/releases/tag/v0.4.0"))
    }
}
