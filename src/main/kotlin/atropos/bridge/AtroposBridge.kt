/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.http.EngineHttpServer

/**
 * Decides whether the bridge runs at all, and on which port.
 *
 * Opt-in by construction. A listener that opens on every start is a surface the
 * operator never chose, and §6 treats anything that widens the reachable
 * surface as a decision that belongs to them. `ATROPOS_BRIDGE_PORT` is both the
 * switch and the value: absent means no listener, so there is no way to end up
 * with a port open because a default changed underneath.
 *
 * `0` is honoured as "let the OS choose", which is what makes the bridge usable
 * from a test without racing a fixed port.
 */
object AtroposBridge {

    const val PORT_VARIABLE = "ATROPOS_BRIDGE_PORT"

    fun fromEnvironment(
        environment: (String) -> String? = System::getenv,
        activeProvider: () -> String
    ): EngineHttpServer? {
        val raw = environment(PORT_VARIABLE)?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val port = raw.toIntOrNull() ?: return null
        if (port !in 0..65_535) return null
        return server(port, activeProvider)
    }

    /** Convenience for the common call shape: default environment lookup. */
    fun fromEnvironment(activeProvider: () -> String): EngineHttpServer? =
        fromEnvironment(System::getenv, activeProvider)

    fun server(port: Int, activeProvider: () -> String): EngineHttpServer =
        EngineHttpServer(
            routeTable = BridgeRoutes(activeProvider = activeProvider).table(),
            port = port
        )
}
