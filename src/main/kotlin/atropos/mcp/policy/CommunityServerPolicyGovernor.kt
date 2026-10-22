/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.mcp.policy

import atropos.core.integration.CommunityServerPolicyGovernor as CoreGovernor
import java.nio.file.Path

/**
 * Policy governor alias in atropos.mcp.policy package.
 */
class CommunityServerPolicyGovernor(configRoot: Path) {
    private val delegate = CoreGovernor(configRoot)

    fun assertAllowed(serverId: String) = delegate.assertServerExecutionAllowed(serverId)
    fun setActivation(serverId: String, enabled: Boolean) = delegate.setServerActivation(serverId, enabled)
}
