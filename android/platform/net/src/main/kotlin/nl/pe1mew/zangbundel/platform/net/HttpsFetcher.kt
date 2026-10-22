// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.platform.net

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * The one network path of the app (architecture A-14): HTTPS only, platform certificate validation,
 * no redirect to http, a fixed user agent, a hard size cap while streaming. Downloads start only
 * from a user action (FR-LIB-12, FR-NET-02); the gathering engine never calls this module (FR-NET-01).
 */
class HttpsFetcher(versionName: String) {
    private val userAgent = "Zangbundel/$versionName"

    /** Opens [url] and streams at most [maxBytes]; throws [TooLargeException] beyond that. */
    fun open(url: String, ifNoneMatch: String? = null, maxBytes: Long): Response {
        val parsed = URL(url)
        require(parsed.protocol == "https") { "only https is allowed" }
        val connection = parsed.openConnection() as HttpsURLConnection
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", userAgent)
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        if (ifNoneMatch != null) connection.setRequestProperty("If-None-Match", ifNoneMatch)
        connection.connect()
        val code = connection.responseCode
        if (code in 300..399) {
            val location = connection.getHeaderField("Location") ?: ""
            connection.disconnect()
            require(location.startsWith("https://")) { "redirect to a non-https location refused" }
            return open(location, ifNoneMatch, maxBytes)
        }
        return Response(code, connection.getHeaderField("ETag"), connection.contentLengthLong, CappedStream(connection.inputStream, maxBytes), connection)
    }

    class Response(
        val code: Int,
        val etag: String?,
        val declaredLength: Long,
        val body: InputStream,
        private val connection: HttpURLConnection,
    ) : AutoCloseable {
        override fun close() { body.close(); connection.disconnect() }
    }

    class TooLargeException(limit: Long) : RuntimeException("download exceeds $limit bytes")

    private class CappedStream(private val inner: InputStream, private val limit: Long) : InputStream() {
        private var count = 0L
        override fun read(): Int = inner.read().also { if (it >= 0) bump(1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, len).also { if (it > 0) bump(it) }
        override fun close() = inner.close()
        private fun bump(n: Int) { count += n; if (count > limit) throw TooLargeException(limit) }
    }
}
