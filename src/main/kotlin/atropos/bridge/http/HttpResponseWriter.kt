/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.http

import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * The one place a response becomes bytes.
 *
 * Every byte the bridge emits passes through here, which is what makes the
 * egress surface auditable: there is a single answer to "what can this listener
 * send". The CORS header is deliberately absent — the bridge is loopback-bound
 * and same-origin, and a permissive origin on a localhost port is how a page on
 * any site reaches an operator's engine.
 */
class HttpResponseWriter {

    fun write(out: OutputStream, response: HttpResponse) {
        val bytes = response.body.toByteArray(StandardCharsets.UTF_8)
        val head = buildString {
            append("HTTP/1.1 ${response.status} ${reason(response.status)}\r\n")
            append("Content-Type: ${response.contentType}\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("X-Content-Type-Options: nosniff\r\n")
            response.headers.forEach { (name, value) -> append("$name: $value\r\n") }
            append("Connection: close\r\n\r\n")
        }
        out.write(head.toByteArray(StandardCharsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    /** Opens a stream response; the caller then pushes frames until it closes. */
    fun writeEventStreamHeader(out: OutputStream) {
        val head = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: text/event-stream; charset=utf-8\r\n")
            append("Cache-Control: no-store\r\n")
            append("X-Content-Type-Options: nosniff\r\n")
            append("Connection: keep-alive\r\n\r\n")
        }
        out.write(head.toByteArray(StandardCharsets.UTF_8))
        out.flush()
    }

    fun writeEvent(out: OutputStream, event: String, data: String) {
        val frame = buildString {
            append("event: $event\n")
            data.lineSequence().forEach { append("data: $it\n") }
            append("\n")
        }
        out.write(frame.toByteArray(StandardCharsets.UTF_8))
        out.flush()
    }

    private fun reason(status: Int): String = when (status) {
        200 -> "OK"
        400 -> "Bad Request"
        403 -> "Forbidden"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        500 -> "Internal Server Error"
        503 -> "Service Unavailable"
        else -> "Status"
    }
}
