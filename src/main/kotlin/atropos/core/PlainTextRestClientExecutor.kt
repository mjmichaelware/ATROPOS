/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * ATOM EX11: PLAIN-TEXT REST CLIENT API QUERY EXECUTOR
 * Path: src/main/kotlin/atropos/core/PlainTextRestClientExecutor.kt
 *
 * Authors and executes raw HTTP and REST queries from .http or .rest plaintext
 * files directly inside the workspace, capturing status, latency, headers, and bodies.
 */
data class ParsedHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String? = null
)

data class RestClientExecutionResult(
    val statusCode: Int,
    val responseBody: String,
    val responseHeaders: Map<String, List<String>>,
    val durationMs: Long,
    val error: String? = null
)

class PlainTextRestClientExecutor(
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
) {
    fun parseHttpFileSnippet(rawHttpText: String): ParsedHttpRequest {
        val lines = rawHttpText.lines()
        var method = "GET"
        var url = ""
        val headers = mutableMapOf<String, String>()
        val bodyLines = mutableListOf<String>()
        var readingBody = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#") || trimmed.startsWith("//")) continue

            if (!readingBody) {
                if (url.isEmpty()) {
                    val parts = trimmed.split(' ', limit = 2)
                    if (parts.size == 2) {
                        method = parts[0].uppercase()
                        url = parts[1]
                    } else if (parts.size == 1 && parts[0].startsWith("http")) {
                        url = parts[0]
                    }
                } else if (trimmed.isEmpty()) {
                    readingBody = true
                } else {
                    val headerParts = trimmed.split(':', limit = 2)
                    if (headerParts.size == 2) {
                        headers[headerParts[0].trim()] = headerParts[1].trim()
                    }
                }
            } else {
                bodyLines.add(line)
            }
        }

        return ParsedHttpRequest(
            method = method,
            url = url,
            headers = headers,
            body = if (bodyLines.isNotEmpty()) bodyLines.joinToString("\n") else null
        )
    }

    fun execute(parsed: ParsedHttpRequest): RestClientExecutionResult {
        val startTime = System.currentTimeMillis()
        return try {
            val builder = HttpRequest.newBuilder()
                .uri(URI.create(parsed.url))
                .timeout(Duration.ofSeconds(30))

            parsed.headers.forEach { (k, v) ->
                builder.header(k, v)
            }

            val bodyPublisher = if (!parsed.body.isNullOrBlank()) {
                HttpRequest.BodyPublishers.ofString(parsed.body)
            } else {
                HttpRequest.BodyPublishers.noBody()
            }

            builder.method(parsed.method, bodyPublisher)
            val response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
            val elapsed = System.currentTimeMillis() - startTime

            RestClientExecutionResult(
                statusCode = response.statusCode(),
                responseBody = response.body(),
                responseHeaders = response.headers().map(),
                durationMs = elapsed
            )
        } catch (ex: Exception) {
            RestClientExecutionResult(
                statusCode = -1,
                responseBody = "",
                responseHeaders = emptyMap(),
                durationMs = System.currentTimeMillis() - startTime,
                error = ex.message
            )
        }
    }

    companion object {
        val defaultExecutor = PlainTextRestClientExecutor()
    }
}
