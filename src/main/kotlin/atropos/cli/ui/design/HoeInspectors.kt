/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.design

import atropos.core.phase20.ImprovementProposal
import atropos.core.phase20.RuntimeObservation

object RuntimeInspector {
    fun inspectRuntime(obs: RuntimeObservation): String {
        return "Runtime: [id=${obs.id} runtimeId=${obs.runtimeId} exitCode=${obs.exitCode}]"
    }
}

object AgentInspector {
    fun inspectAgent(agentId: String, currentGoal: String?): String {
        return "Agent: [id=$agentId currentGoal=${currentGoal ?: "idle"}]"
    }
}

object ProviderInspector {
    fun inspectProvider(providerId: String, latency: Long, successRate: Double): String {
        return "Provider: [id=$providerId latency=${latency}ms successRate=${successRate * 100}%]"
    }
}

object PolicyInspector {
    fun inspectPolicy(proposal: ImprovementProposal, policyVerdict: String): String {
        return "Policy: [proposal=${proposal.id} verdict=$policyVerdict]"
    }
}

object SourceAuthorityInspector {
    fun inspectAuthority(docPath: String, sha256: String): String {
        return "SourceAuthority: [doc=$docPath sha256=$sha256]"
    }
}

object RecoveryInspector {
    fun inspectRecovery(restartCount: Int, lastSuccess: Boolean): String {
        return "Recovery: [restarts=$restartCount lastSuccess=$lastSuccess]"
    }
}
