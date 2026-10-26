// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.library

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.pe1mew.zangbundel.platform.net.HttpsFetcher

/** [Downloads] on the app's one HTTPS client (architecture A-14), off the main thread. */
class HttpsDownloads(private val fetcher: HttpsFetcher) : Downloads {
    override suspend fun fetch(url: String, maxBytes: Long, ifNoneMatch: String?): Downloads.Fetched = withContext(Dispatchers.IO) {
        fetcher.open(url, ifNoneMatch, maxBytes).use { r -> Downloads.Fetched(r.code, r.etag, if (r.code == 200) r.body.readBytes() else ByteArray(0)) }
    }
}
