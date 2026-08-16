/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.design

import atropos.core.phase20.ImprovementProposal
import atropos.core.phase20.RuntimeObservation
import atropos.core.multimodal.MultimodalInspection
import atropos.core.provider.ProviderTruthRecord
import atropos.core.recovery.StateSnapshot

object RuntimeInspector {
    fun inspectRuntime(obs: RuntimeObservation): String {
        return "Runtime: [id=${obs.id} runtimeId=${obs.runtimeId} exitCode=${obs.exitCode}]"
    }

    fun inspectRuntime(inspection: MultimodalInspection): String =
        "Runtime: [inspection=${inspection.id} kind=${inspection.kind.name.lowercase()} " +
            "passed=${inspection.passed} severity=${inspection.severity.name.lowercase()} " +
            "findings=${inspection.findings.size}]"
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

    fun inspectProvider(record: ProviderTruthRecord, selected: Boolean): String {
        return "Provider: [id=${record.id} selected=$selected health=${record.health.name.lowercase()} " +
            "key=${record.keyPresent} adapter=${record.adapterPresent} executable=${record.executableSupport} " +
            "ask=${record.askEligible} patch=${record.patchEligible} paid=${record.paidLocked}]"
    }
}

object PolicyInspector {
    fun inspectPolicy(proposal: ImprovementProposal, policyVerdict: String): String {
        return "Policy: [proposal=${proposal.id} verdict=$policyVerdict]"
    }

    fun inspectPolicy(proposalId: String, policyVerdict: String): String =
        "Policy: [proposal=$proposalId verdict=$policyVerdict]"
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

    fun inspectRecovery(snapshot: StateSnapshot): String {
        val report = snapshot.recoveryReport
        return "Recovery: [snapshot=${snapshot.id} goals=${snapshot.goalRuns.size} " +
            "dags=${snapshot.dags.size} restored=${report?.interruptedRuns?.let { report.interruptedRuns - report.staleQueueEntries } ?: "unknown"} " +
            "lastSuccess=${report?.errors?.isEmpty() ?: false}]"
    }
}
