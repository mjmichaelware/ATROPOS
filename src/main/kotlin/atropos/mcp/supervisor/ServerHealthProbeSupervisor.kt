/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.mcp.supervisor

import atropos.core.integration.GenericMcpHostManager
import atropos.core.integration.McpHealthProbeSupervisor
import java.nio.file.Path

/**
 * ServerHealthProbeSupervisor alias in atropos.mcp.supervisor namespace.
 */
class ServerHealthProbeSupervisor(configRoot: Path) {
    private val delegate = McpHealthProbeSupervisor(GenericMcpHostManager(configRoot))

    fun probeAll(): Map<String, atropos.core.integration.McpHealthStatus> = delegate.probeAllConfigured()
}
