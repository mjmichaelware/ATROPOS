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

sealed interface BuildExecutionReport {
    val phase: String
    val success: Boolean

    data class Success(
        override val phase: String = "COMPLETE",
        val artifactPath: Path? = null,
        val details: String = "Build execution succeeded"
    ) : BuildExecutionReport {
        override val success: Boolean get() = true
    }

    sealed class Failure(
        override val phase: String,
        open val reason: String,
        open val failureCode: String
    ) : BuildExecutionReport {
        override val success: Boolean get() = false

        data class Wave0ContractSynthesisFailure(
            override val reason: String,
            val contractViolations: List<String> = emptyList()
        ) : Failure("WAVE_0_SYNTHESIS", reason, "ERR_WAVE0_CONTRACT")

        data class Wave1StubFalsificationFailure(
            override val reason: String,
            val falsificationErrors: List<String> = emptyList()
        ) : Failure("WAVE_1_TEST_FALSIFICATION", reason, "ERR_WAVE1_FALSIFICATION")

        data class Wave2ImplementationCompileFailure(
            override val reason: String,
            val compilerDiagnostics: List<String> = emptyList()
        ) : Failure("WAVE_2_COMPILE", reason, "ERR_WAVE2_COMPILE")

        data class Wave3BehavioralVerificationFailure(
            override val reason: String,
            val failedTestCount: Int = 0
        ) : Failure("WAVE_3_TEST_VERIFY", reason, "ERR_WAVE3_BEHAVIORAL")

        data class Wave4IntegrationFailure(
            override val reason: String,
            val failingEdge: String? = null
        ) : Failure("WAVE_4_INTEGRATION", reason, "ERR_WAVE4_INTEGRATION")

        data class Phase100ZeroEntropyGateFailure(
            override val reason: String,
            val residualObligationsCount: Int = 0
        ) : Failure("PHASE_100_ZERO_ENTROPY", reason, "ERR_PHASE100_GATE")

        data class BudgetExceededFailure(
            override val reason: String,
            val waveCount: Int
        ) : Failure("BUDGET_EXCEEDED", reason, "ERR_BUDGET_EXCEEDED")
    }
}

data class WaveExecutionResult(
    val requestId: String = "",
    val success: Boolean,
    val completedAtoms: List<String> = emptyList(),
    val failedAtoms: List<String> = emptyList(),
    val blockedAtoms: List<String> = emptyList(),
    val message: String = "",
    val evidencePaths: List<String> = emptyList(),
    val gitCommit: String? = null,
    val treeSha256: String? = null,
    val artifactPath: Path? = null,
    val report: BuildExecutionReport? = null
) {
    /**
     * SD7-R-064: 2-parameter constructor for WaveExecutionResult(success, artifactPath)
     */
    constructor(success: Boolean, artifactPath: Path?) : this(
        requestId = "",
        success = success,
        completedAtoms = emptyList(),
        failedAtoms = emptyList(),
        blockedAtoms = emptyList(),
        message = if (success) "success" else "failure",
        artifactPath = artifactPath,
        report = if (success) BuildExecutionReport.Success(artifactPath = artifactPath) else null
    )

    val isComplete: Boolean get() = success && failedAtoms.isEmpty() && blockedAtoms.isEmpty()
}