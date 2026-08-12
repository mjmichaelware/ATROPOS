/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.bridge

/**
 * Where the engine is listening.
 *
 * Loopback only, and not configurable to a remote host. The engine binds
 * 127.0.0.1 deliberately; a client that could be pointed at an arbitrary
 * address would invite someone to expose the engine to a network it was never
 * hardened for. The port is the one variable because
 * `ATROPOS_BRIDGE_PORT` chooses it.
 */
object BridgeEndpoint {
    const val DEFAULT_PORT = 8787
    private const val HOST = "127.0.0.1"

    /** Candidate ports, most likely first, for discovery when none is known. */
    val CANDIDATE_PORTS = listOf(DEFAULT_PORT, 8080, 8888, 9090)

    fun baseUrl(port: Int): String = "http://$HOST:$port"

    fun url(port: Int, path: String): String {
        require(path.startsWith("/")) { "bridge path must be absolute: $path" }
        return baseUrl(port) + path
    }
}
