/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.http

import java.io.BufferedReader
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Turns request bytes into an [HttpRequest], or refuses.
 *
 * Every limit here is a refusal rather than a truncation. A half-read request
 * line is not a request, and guessing what the caller meant is how a parser
 * becomes an attack surface; [parse] returns null and the caller answers 400.
 *
 * Enforces strict byte ceilings and rejects malicious URL traversal encodings
 * before unbounded memory allocations can take place.
 */
class HttpRequestParser(
    val maxRequestLineChars: Int = 8 * 1024,
    val maxHeaderCount: Int = 64,
    val maxHeaderChars: Int = 8 * 1024,
    val maxBodyBytes: Int = 1 * 1024 * 1024
) {
    fun parse(reader: BufferedReader): HttpRequest? {
        val requestLine = reader.readLine() ?: return null
        if (requestLine.length > maxRequestLineChars) return null

        val parts = requestLine.split(' ')
        if (parts.size != 3 || parts.any(String::isEmpty)) return null
        val method = parts[0].uppercase()
        val target = parts[1]

        val rawPath = target.substringBefore('?')
        val decodedPath = decode(rawPath)

        // Refuse malicious path traversal sequences, null bytes, or backslash injections
        if (decodedPath.contains('\u0000') ||
            decodedPath.contains('\\') ||
            decodedPath.split('/').contains("..") ||
            rawPath.contains("%2e%2e", ignoreCase = true) ||
            rawPath.contains("%252e", ignoreCase = true)
        ) {
            return null
        }

        val headers = readHeaders(reader) ?: return null

        val declaredLength = headers["content-length"]
        val length = declaredLength?.toIntOrNull() ?: if (declaredLength == null) 0 else return null
        if (length < 0 || length > maxBodyBytes) return null
        val body = if (length == 0) "" else readBody(reader, length) ?: return null

        return HttpRequest(
            method = method,
            path = decodedPath,
            query = parseQuery(target.substringAfter('?', "")),
            headers = headers,
            body = body
        )
    }

    fun readHeaders(reader: BufferedReader): Map<String, String>? {
        val headers = LinkedHashMap<String, String>()
        var totalHeaderChars = 0
        val maxTotalHeaderChars = maxHeaderChars * maxHeaderCount

        while (true) {
            val line = reader.readLine() ?: return null
            if (line.isEmpty()) return headers
            if (line.length > maxHeaderChars) return null
            totalHeaderChars += line.length
            if (totalHeaderChars > maxTotalHeaderChars) return null
            if (headers.size >= maxHeaderCount) return null

            val name = line.substringBefore(':', "").trim().lowercase()
            if (name.isEmpty()) return null
            if (headers.containsKey(name)) return null
            headers[name] = line.substringAfter(':', "").trim()
        }
    }

    fun readBody(reader: BufferedReader, length: Int): String? {
        if (length < 0 || length > maxBodyBytes) return null
        val buffer = CharArray(length)
        var read = 0
        while (read < length) {
            val count = reader.read(buffer, read, length - read)
            if (count < 0) return null
            read += count
        }
        return String(buffer, 0, read)
    }

    private fun parseQuery(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.split('&')
            .mapNotNull { pair ->
                if (pair.isBlank()) return@mapNotNull null
                val key = decode(pair.substringBefore('='))
                if (key.isBlank()) return@mapNotNull null
                key to decode(pair.substringAfter('=', ""))
            }
            .toMap()
    }

    private fun decode(value: String): String =
        try {
            URLDecoder.decode(value, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            value
        }
}
