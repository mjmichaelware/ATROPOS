/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.mcp.transport

import atropos.core.integration.McpServerConfig
import atropos.core.integration.McpTransportSecurityInterceptor
import atropos.core.integration.TransportValidationResult

/**
 * LocalOnlyRemoteTransportInterceptor alias in atropos.mcp.transport package.
 */
class LocalOnlyRemoteTransportInterceptor(
    private val delegate: McpTransportSecurityInterceptor = McpTransportSecurityInterceptor()
) {
    fun validate(config: McpServerConfig): TransportValidationResult = delegate.validateServerTransport(config)
    fun assertPermitted(config: McpServerConfig) = delegate.assertTransportPermitted(config)
}
