package com.hemogoblins.betterrecords.client.cache

import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLConnection

/**
 * Thrown when a downloaded URL responds with a content type that isn't audio (e.g. an HTML page or redirect)
 */
class UnsupportedAudioTypeException(val contentType: String, url: String) :
    IOException("Expected an audio file but $url returned content type '$contentType'")

/**
 * Thrown when the server answers with an HTTP error, so the etcher can show the status code.
 */
class HttpStatusException(val status: Int, url: String) :
    IOException("$url returned HTTP $status")

private const val MAX_REDIRECTS = 8

// Some hosts (Dropbox included) refuse or serve a web page to Java's default "Java/21" user agent.
private const val USER_AGENT = "Mozilla/5.0 (compatible; BetterRecords; +https://github.com/AngrySoundTech/BetterRecords)"

private fun isAcceptableAudioType(contentType: String): Boolean {
    return contentType.startsWith("audio/") ||
        contentType == "application/ogg" ||
        contentType == "application/octet-stream" ||
        contentType == "binary/octet-stream"
}

/**
 * Rewrites share links from common file hosts into direct download links.
 *
 * Dropbox share links (`?dl=0`, or no `dl` at all on the newer `/scl/fi/` links) open a preview page,
 * `dl=1` makes Dropbox send the file itself.
 */
fun toDirectDownloadUrl(url: String): String {
    val uri = try {
        URI(url.trim())
    } catch (e: Exception) {
        return url.trim()
    }
    val host = uri.host?.lowercase() ?: return url.trim()

    if (host == "dropbox.com" || host.endsWith(".dropbox.com")) {
        val params = (uri.rawQuery ?: "")
            .split('&')
            .filter { it.isNotEmpty() && !it.startsWith("dl=") && !it.startsWith("raw=") }
        val query = (params + "dl=1").joinToString("&")
        // Build from the raw (already encoded) parts, the multi-arg URI constructor would encode %20 again.
        return "${uri.scheme}://${uri.rawAuthority}${uri.rawPath ?: ""}?$query"
    }

    return url.trim()
}

/**
 * Opens [url], following redirects manually. Java's HttpURLConnection won't follow a redirect that
 * changes host in some cases (and never one that changes protocol), and file hosts redirect a lot.
 */
private fun openFollowingRedirects(url: String): URLConnection {
    var current = url
    repeat(MAX_REDIRECTS) {
        val connection = URI(current).toURL().openConnection()
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000

        if (connection !is HttpURLConnection) {
            connection.connect()
            return connection
        }

        connection.instanceFollowRedirects = false
        connection.connect()
        val status = connection.responseCode

        if (status in 300..399) {
            val location = connection.getHeaderField("Location")
                ?: throw HttpStatusException(status, current)
            connection.disconnect()
            current = URI(current).resolve(location).toString()
            return@repeat
        }
        if (status >= 400) {
            connection.disconnect()
            throw HttpStatusException(status, current)
        }
        return connection
    }
    throw IOException("Too many redirects for $url")
}

fun downloadFile(
    url: String,
    target: File,
    progress: (bytesRead: Long, bytesTotal: Long, complete: Boolean) -> Unit
): File {
    val connection = openFollowingRedirects(toDirectDownloadUrl(url))

    // Reject anything that isn't audio
    val contentType = connection.contentType?.substringBefore(';')?.trim()?.lowercase()
    if (contentType != null && !isAcceptableAudioType(contentType)) {
        throw UnsupportedAudioTypeException(contentType, url)
    }

    val size = connection.contentLengthLong

    // Just in case we attempted this one already, delete it.
    target.delete()

    BufferedInputStream(connection.getInputStream()).use { inputStream ->
        FileOutputStream(target).use { outputStream ->
            var bytesCopied = 0L
            val buffer = ByteArray(8192)
            var bytes = inputStream.read(buffer)
            while (bytes >= 0) {
                outputStream.write(buffer, 0, bytes)
                bytesCopied += bytes
                progress(bytesCopied, size, false)
                bytes = inputStream.read(buffer)
            }
            progress(bytesCopied, size, true)
        }
    }

    return target
}
