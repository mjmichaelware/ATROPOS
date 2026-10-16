/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.synthesis

import atropos.core.agent.CleanRoomPromptContract
import atropos.core.contract.ContractLattice
import atropos.core.planning.PartitionCluster
import atropos.core.provider.ProviderCascadeRouter
import atropos.core.toolchain.NativeToolchainRegistry
import atropos.core.toolchain.ToolchainDiagnostic
import atropos.core.worktree.IsolatedWorktreeService
import java.nio.file.Files
import java.nio.file.Path

data class SynthesisResult(
    val clusterId: String,
    val success: Boolean,
    val worktreePath: Path,
    val diagnostics: List<ToolchainDiagnostic>
)

class DecoupledWorkerSynthesizer(
    private val worktreeService: IsolatedWorktreeService,
    private val cascadeRouter: ProviderCascadeRouter,
    private val toolchains: NativeToolchainRegistry = NativeToolchainRegistry
) {
    private val repairLoop: CompilerDiagnosticRepairLoop = CompilerDiagnosticRepairLoop(cascadeRouter)

    fun synthesizeCluster(
        cluster: PartitionCluster,
        lattice: ContractLattice,
        h0: String
    ): SynthesisResult {
        val wtResult = runCatching {
            worktreeService.createWorktree(cluster.clusterId, cluster.assignedTerritory.toList())
        }.getOrNull()

        val worktreePath = wtResult?.record?.worktreePath
            ?: Files.createTempDirectory("worktree-${cluster.clusterId}")

        for ((relPath, content) in lattice.interfaceFiles) {
            val target = worktreePath.resolve(relPath)
            Files.createDirectories(target.parent)
            Files.writeString(target, content)
        }

        for (node in cluster.nodes) {
            val prompt = CleanRoomPromptContract.buildPrompt(node, lattice, h0)
            val cascadeResult = cascadeRouter.completeWithCascade(
                requestedProvider = "default",
                prompt = prompt,
                context = ""
            )

            var responseCode = cascadeResult.response.trim()
            if (responseCode.startsWith("```")) {
                responseCode = responseCode.substringAfter("\n").substringBeforeLast("```").trim()
            }

            val targetFile = node.territory.firstOrNull() ?: "src/main/kotlin/atropos/generated/${node.id}.kt"
            val targetPath = worktreePath.resolve(targetFile)
            Files.createDirectories(targetPath.parent)
            Files.writeString(targetPath, responseCode)
        }

        val driver = toolchains.getDriver("kotlin") ?: toolchains.requireDriver("kotlin")
        var compileResult = driver.compile(worktreePath)

        if (!compileResult.passed || compileResult.exitCode != 0) {
            val basePrompt = "Cluster ${cluster.clusterId} root H0=$h0"
            val repairResult = repairLoop.repair(worktreePath, driver, compileResult, basePrompt)
            compileResult = repairResult
        }

        // Neurosymbolic verification suite
        atropos.core.neurosymbolic.ActorTerritoryMessageChecker().verifyNoSharedMemory(worktreePath)
        atropos.core.neurosymbolic.HoareLogicAnnotationScanner().checkCoverage(worktreePath)
        atropos.core.neurosymbolic.SmtEquivalenceProver().dischargeContracts(worktreePath)
        atropos.core.neurosymbolic.TriModelDisputationArbitrator().arbitrate(listOf(h0))

        return SynthesisResult(
            clusterId = cluster.clusterId,
            success = compileResult.passed && compileResult.exitCode == 0,
            worktreePath = worktreePath,
            diagnostics = compileResult.diagnostics
        )
    }
}
