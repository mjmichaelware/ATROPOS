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
 * The bounds exist because this listener is reachable from a browser page. An
 * unbounded header loop is a memory exhaustion primitive against the operator's
 * own machine, which is the one machine ATROPOS is supposed to protect.
 */
class HttpRequestParser(
    private val maxRequestLineChars: Int = 8 * 1024,
    private val maxHeaderCount: Int = 64,
    private val maxHeaderChars: Int = 8 * 1024,
    private val maxBodyBytes: Int = 1 * 1024 * 1024
) {
    fun parse(reader: BufferedReader): HttpRequest? {
        val requestLine = reader.readLine() ?: return null
        if (requestLine.length > maxRequestLineChars) return null

        val parts = requestLine.split(' ')
        if (parts.size != 3 || parts.any(String::isEmpty)) return null
        val method = parts[0].uppercase()
        val target = parts[1]

        val headers = readHeaders(reader) ?: return null

        val declaredLength = headers["content-length"]
        val length = declaredLength?.toIntOrNull() ?: if (declaredLength == null) 0 else return null
        if (length < 0 || length > maxBodyBytes) return null
        val body = if (length == 0) "" else readBody(reader, length) ?: return null

        return HttpRequest(
            method = method,
            path = decode(target.substringBefore('?')),
            query = parseQuery(target.substringAfter('?', "")),
            headers = headers,
            body = body
        )
    }

    private fun readHeaders(reader: BufferedReader): Map<String, String>? {
        val headers = LinkedHashMap<String, String>()
        while (true) {
            val line = reader.readLine() ?: return null
            if (line.isEmpty()) return headers
            if (line.length > maxHeaderChars) return null
            if (headers.size >= maxHeaderCount) return null
            val name = line.substringBefore(':', "").trim().lowercase()
            if (name.isEmpty()) return null
            if (headers.containsKey(name)) return null
            headers[name] = line.substringAfter(':', "").trim()
        }
    }

    private fun readBody(reader: BufferedReader, length: Int): String? {
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
