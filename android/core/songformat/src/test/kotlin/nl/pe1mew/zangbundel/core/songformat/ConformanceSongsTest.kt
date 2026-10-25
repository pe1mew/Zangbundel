// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.songformat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Runs every song vector of the conformance corpus (test plan 3.2): the parser's verdict, report
 * codes, canonical form, hash and line list must equal the `.expected.json` written beside each file.
 */
class ConformanceSongsTest {
    private fun resource(name: String): ByteArray =
        assertNotNull(javaClass.classLoader.getResourceAsStream("conformance/songs/$name"), "missing vector $name").use { it.readBytes() }

    @Test
    fun `every song vector gives the expected verdict, codes, hash and lines`() {
        val names = String(resource("index.txt"), Charsets.UTF_8).lines().filter { it.isNotBlank() }
        assertTrue(names.size >= 10, "vectors: $names")
        for (name in names) {
            val bytes = resource("$name.cho")
            val expected = Json.parseToJsonElement(String(resource("$name.expected.json"), Charsets.UTF_8)) as JsonObject
            val result = SongParser.parse(bytes, "songs/$name.cho", "$name.cho")
            val codes = result.report.map { it.code }.distinct().sorted()
            assertEquals(expected.getValue("codes").jsonArray.map { it.jsonPrimitive.content }, codes, "$name: codes ${result.report}")
            if (expected.getValue("accepted").jsonPrimitive.boolean) {
                val song = assertNotNull(result.song, "$name: rejected ${result.report}")
                val sha = MessageDigest.getInstance("SHA-256").digest(song.canonical.toByteArray(Charsets.UTF_8)).toHex()
                assertEquals(expected.getValue("canonical_sha256").jsonPrimitive.content, sha, "$name: canonical form")
                assertEquals(expected.getValue("hash").jsonPrimitive.content, song.hash.toHex(), "$name: hash")
                assertEquals(expected.getValue("lines").jsonArray.map { it.jsonPrimitive.content }, song.lines, "$name: line list")
            } else {
                assertNull(result.song, "$name: should be rejected")
            }
        }
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
