package io.github.jagadeeshm007.pin.models

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * [RangeSource] over the platform HTTP client: HTTPS only, redirects followed (GitHub
 * release assets redirect to a CDN), no cookies or identifiers sent (spec PIN-20 AC-7).
 */
class HttpUrlSource(private val timeoutMs: Int = 20_000) : RangeSource {
    override suspend fun open(url: String, fromByte: Long): RangeResponse = withContext(Dispatchers.IO) {
        require(url.startsWith("https://")) { "only HTTPS is allowed: $url" }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Accept-Encoding", "identity")
            if (fromByte > 0) setRequestProperty("Range", "bytes=$fromByte-")
        }
        val code = connection.responseCode
        if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
            connection.disconnect()
            throw IOException("HTTP $code for $url")
        }
        if (!connection.url.protocol.equals("https", ignoreCase = true)) {
            connection.disconnect()
            throw IOException("redirected away from HTTPS")
        }
        RangeResponse(code, connection.contentLengthLong, connection.inputStream)
    }
}
