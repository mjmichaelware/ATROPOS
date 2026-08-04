/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.http

/**
 * A route that holds the connection open and pushes frames.
 *
 * Kept distinct from [HttpRoute] because the two have genuinely different
 * lifetimes: a request route computes one answer and the server closes the
 * socket, while a stream route owns the socket until the client leaves or the
 * server stops. Collapsing them would force every ordinary handler to reason
 * about a connection it does not hold.
 *
 * The handler is given a [StreamSink] rather than the socket. Nothing that
 * pushes frames should be able to write a header, reopen a response, or decide
 * the connection is finished — the server owns all three.
 */
data class HttpStreamRoute(
    val method: String,
    val path: String,
    val summary: String,
    val handler: (HttpRequest, StreamSink) -> Unit
)

/**
 * The only thing a stream handler may do: emit a named frame, and ask whether
 * the client is still there.
 */
interface StreamSink {
    /** True while the connection is open and the server is running. */
    fun isOpen(): Boolean

    /**
     * Pushes one event. Returns false once the client has gone, so a producer
     * loop terminates by checking a return value rather than by an exception
     * thrown from inside a write.
     */
    fun emit(event: String, data: String): Boolean
}
