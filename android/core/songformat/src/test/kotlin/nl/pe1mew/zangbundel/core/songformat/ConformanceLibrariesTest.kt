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
import kotlin.test.assertTrue

/**
 * Runs every library-file vector of the conformance corpus (test plan 3.2): the verdict and report
 * codes of `zangbundel.json` for the app version each `.expected.json` names. The library
 * repository's `scripts/conformance.py` runs the same vectors against its validator.
 */
class ConformanceLibrariesTest {
    private fun resource(name: String): ByteArray =
        assertNotNull(javaClass.classLoader.getResourceAsStream("conformance/libraries/$name"), "missing vector $name").use { it.readBytes() }

    @Test
    fun `every library vector gives the expected verdict and codes`() {
        val names = String(resource("index.txt"), Charsets.UTF_8).lines().filter { it.isNotBlank() }
        assertTrue(names.size >= 10, "vectors: $names")
        for (name in names) {
            val bytes = resource("$name.json")
            val expected = Json.parseToJsonElement(String(resource("$name.expected.json"), Charsets.UTF_8)) as JsonObject
            assertEquals(expected.getValue("sha256").jsonPrimitive.content, MessageDigest.getInstance("SHA-256").digest(bytes).toHex(), "$name: vector bytes changed")
            val result = LibraryFileParser.parse(bytes, expected.getValue("app_version").jsonPrimitive.content)
            assertEquals(expected.getValue("codes").jsonArray.map { it.jsonPrimitive.content }, result.report.map { it.code }.distinct().sorted(), "$name: ${result.report}")
            assertEquals(expected.getValue("accepted").jsonPrimitive.boolean, result.library != null, "$name: ${result.report}")
        }
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
