/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.bridge

/**
 * Finds the port the engine is listening on.
 *
 * The operator chooses it with `ATROPOS_BRIDGE_PORT`, and the app has no way
 * to read another process's environment, so it probes a short candidate list
 * against `/v1/health` instead of demanding the number be typed twice.
 *
 * A found port is remembered for the process lifetime: rediscovering on every
 * poll would mean up to four connection attempts a second against a device
 * that is usually running nothing.
 */
class BridgeDiscovery(
    private val candidates: List<Int> = BridgeEndpoint.CANDIDATE_PORTS,
    private val probe: (String) -> BridgeResult = BridgeHttp::get
) {
    @Volatile
    private var known: Int? = null

    /** The live port, or null when the engine is not reachable. */
    fun resolve(): Int? {
        known?.let { if (healthy(it)) return it else known = null }
        for (port in candidates) {
            if (healthy(port)) {
                known = port
                return port
            }
        }
        return null
    }

    fun forget() {
        known = null
    }

    private fun healthy(port: Int): Boolean =
        probe(BridgeEndpoint.url(port, "/v1/health")) is BridgeResult.Ok
}
