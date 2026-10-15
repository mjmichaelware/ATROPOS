/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.orchestration

import atropos.core.dag.DagDefinition
import atropos.core.factory.AppProjectSpec
import atropos.core.factory.FactoryLineage
import java.nio.file.Path

/**
 * Typed request for a wave execution cycle.
 *
 * The orchestrator receives this instead of raw prompts or template calls.
 * All synthesis flows through the Wave-0 ContractLatticeSynthesizer.
 */
data class WaveExecutionRequest(
    val requestId: String,
    val prompt: String,
    val projectSpec: AppProjectSpec,
    val lineage: FactoryLineage,
    val targetDirectory: Path,
    val plannedAtomIds: List<String> = emptyList(),
    val acceptanceFreezeSha256: String? = null,
    val contextHash: String? = null,
    val maxWaves: Int = 64,
    val budget: WaveExecutionBudget = WaveExecutionBudget()
) {
    companion object {
        fun fromFactoryPlan(plan: atropos.core.factory.FactoryPlan, lineage: FactoryLineage, targetDir: Path): WaveExecutionRequest {
            return WaveExecutionRequest(
                requestId = plan.id,
                prompt = plan.prompt,
                projectSpec = plan.projectSpec,
                lineage = lineage,
                targetDirectory = targetDir,
                plannedAtomIds = plan.plannedAtomIds,
                acceptanceFreezeSha256 = plan.acceptanceFreezeSha256,
                contextHash = plan.contextHash
            )
        }
    }
}

data class WaveExecutionBudget(
    val maxAtomAttempts: Int = 3,
    val maxRepairWaves: Int = 3,
    val maxProviderCallsPerAtom: Int = 4,
    val maxExecutionWaves: Int = 64
) {
    init {
        require(maxAtomAttempts > 0 && maxRepairWaves > 0 && maxProviderCallsPerAtom > 0 && maxExecutionWaves > 0) {
            "wave execution budget values must be positive"
        }
    }
}

data class WaveExecutionResult(
    val requestId: String,
    val success: Boolean,
    val completedAtoms: List<String>,
    val failedAtoms: List<String>,
    val blockedAtoms: List<String>,
    val message: String,
    val evidencePaths: List<String> = emptyList(),
    val gitCommit: String? = null,
    val treeSha256: String? = null
) {
    val isComplete: Boolean get() = success && failedAtoms.isEmpty() && blockedAtoms.isEmpty()
}