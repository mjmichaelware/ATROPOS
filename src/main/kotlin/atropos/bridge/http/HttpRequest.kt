/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.http

/**
 * One parsed HTTP request.
 *
 * The engine already streams events on a loopback socket, but that stream never
 * reads what the client asked for: it writes an SSE header to whatever connects
 * and pushes the same payload forever. A surface that has to ask different
 * questions ("what are the six answers", "what commands exist") needs the
 * request itself, so this type exists to carry it.
 *
 * Header names are lower-cased at parse time. HTTP treats them case-insensitively
 * and a map that does not would let `Accept` and `accept` disagree.
 */
data class HttpRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,
    val body: String
) {
    fun header(name: String): String? = headers[name.lowercase()]

    /** True when the caller asked for an event stream rather than one response. */
    fun wantsEventStream(): Boolean =
        header("accept")?.contains("text/event-stream", ignoreCase = true) == true
}
