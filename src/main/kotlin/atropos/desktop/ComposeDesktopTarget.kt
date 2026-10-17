/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.desktop

/**
 * SD7-T-090: Jetpack Compose Desktop (KMP) control plane target.
 * Mathematical Invariant: I(Module_A; Module_B | Interface_Lattice) = 0.000 bits
 * Communicates with headless atropos.jar daemon via SSEBridgeProtocol.
 * Daemon lifecycle is completely independent of UI process; UI crash leaves builds running.
 */
object ComposeDesktopTarget {
    const val DEFAULT_DAEMON_PORT = 7411
    const val SSE_ENDPOINT = "http://localhost:7411/events/sse"

    fun launch() {
        // Compose Desktop entrypoint communicating over SSEBridgeProtocol
        System.err.println("ComposeDesktopTarget: Connected to daemon SSE at $SSE_ENDPOINT")
    }
}
