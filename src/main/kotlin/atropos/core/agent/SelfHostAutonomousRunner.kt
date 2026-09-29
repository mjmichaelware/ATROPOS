package atropos.core.agent

import atropos.core.dag.DagExecutionService
import atropos.core.planning.InternalBatchDefiner
import atropos.core.planning.InternalReadinessCalculator
import atropos.core.verification.GovernedCompileGate
import atropos.core.verification.GovernedCompileGateResult

class SelfHostAutonomousRunner(
    private val service: SelfHostGoalService,
    private val jarLocator: SelfHostRuntimeJarLocator,
    private val jarBuilder: SelfHostCandidateJarBuilder? = null,
    /**
     * The compile gate between "source was mutated" and "a jar may be promoted".
     * Null leaves [SelfHostRunPredicate.COMPILE_GATE_PASSED] unmet, so a caller
     * that omits it can never reach a VERIFIED proof.
     */
    private val compileGate: GovernedCompileGate? = null,
    private val proofBuilder: SelfHostRunProofBuilder? = null,
    private val gitStatusEvidence: SelfHostGitStatusEvidence? = null,
    private val dagService: DagExecutionService = DagExecutionService(),
    private val batchDefiner: InternalBatchDefiner = InternalBatchDefiner(),
    private val readinessCalculator: InternalReadinessCalculator = InternalReadinessCalculator(),
    private val repairExecutor: SelfHostRepairExecutor = SelfHostRepairExecutor(),
    private val maxRepairAttempts: Int = 3
) {
    /**
     * Runs the chain, then attaches the operator-facing proof.
     *
     * The proof is built after the chain settles so that every exit — refusal,
     * compile failure, stop-before-promotion, or promotion — carries the same
     * evidence about what is actually on disk.
     */
    /**
     * @param maxAdvances null derives the budget from the DAG once it exists,
     *   which is the only point at which the amount of work is known. A caller
     *   that passes a number keeps it.
     */
    fun run(
        prompt: String,
        phase: String = "11",
        maxAdvances: Int? = null,
        lifecycleEmitter: (String) -> Unit = ::println
    ): SelfHostAutonomousRunResult =
        attachProof(runChain(prompt, phase, maxAdvances, lifecycleEmitter))

    private fun attachProof(result: SelfHostAutonomousRunResult): SelfHostAutonomousRunResult {
        val builder = proofBuilder ?: return result
        val goalId = result.goal?.record?.id ?: return result
        val proof = builder.build(
            goalId = goalId,
            dag = result.goal.dag,
            compileGate = result.compileGate,
            evidenceMarkdownPath = result.evidenceBundle?.markdownPath?.toString(),
            evidenceJsonPath = result.evidenceBundle?.jsonPath?.toString()
        )
        service.addEvidence(goalId, proof.evidenceLine())
        return result.copy(proof = proof)
    }

    private fun runChain(
        prompt: String,
        phase: String,
        maxAdvances: Int?,
        lifecycleEmitter: (String) -> Unit
    ): SelfHostAutonomousRunResult {
        // Narrated rather than merely collected. Every `steps +=` below now
        // reaches a watching operator as it happens instead of arriving as a
        // block after the run has already decided everything.
        val steps = atropos.core.thinking.NarratedSteps()
        steps.outline("starting self-host phase $phase")
        val started = service.startGoal(prompt, phase)
        steps += started.message
        if (!started.ok) return stopped(started, null, null, steps)

        val goalId = started.goal?.record?.id
            ?: return stopped(started.copy(message = "self-host goal start returned no goal"), null, null, steps)
        lifecycleEmitter("ATROPOS_SELF_HOST_RUN_STARTED goal=$goalId")
        steps.outline("ATROPOS_SELF_HOST_RUN_STARTED goal=$goalId")

        // The budget, measured against the graph rather than assumed.
        //
        // It has to be resolved here and not at the call site: `startGoal` is
        // what builds the DAG, so before this line there is no node count to
        // derive from. A run against a three-node bootstrap graph gets fifteen
        // advances; one against a four-hundred-node document graph gets what it
        // needs instead of stopping, silently and successfully, at 25.
        val nodeCount = started.goal?.dag?.nodes?.size ?: 0
        val budget = maxAdvances ?: SelfHostRuntimeRunLimits.forNodeCount(nodeCount)
        steps.outline(
            "run budget $budget advances for $nodeCount DAG " +
                (if (nodeCount == 1) "node" else "nodes")
        )

        // Batch planning: plan all batches upfront for breadth.
        // This gives the self-host chain visibility into the full execution plan.
        val dag = started.goal?.dag ?: run {
            steps += "no DAG attached to started goal"
            return stopped(started.copy(message = "no DAG"), null, null, steps)
        }
        val allBatches = planAllBatches(dag)
        val totalBatches = allBatches.size
        val totalNodesInBatches = allBatches.sumOf { it.size }
        steps.outline("batch planning: $totalBatches batches, $totalNodesInBatches nodes")
        for ((index, batch) in allBatches.withIndex()) {
            steps.outline("  batch ${index + 1}: ${batch.joinToString(", ")}")
        }

        var latest = started
        var advances = 0
        var automaticRecoveries = 0
        val recoveryBudget = 2
        var currentBatchIndex = 0
        var currentBatchNodes = allBatches.getOrNull(currentBatchIndex) ?: emptyList()
        var currentNodeIndex = 0
        while (advances < budget.coerceAtLeast(1) && currentBatchIndex < allBatches.size) {
            // If we've exhausted the current batch, move to the next batch
            if (currentNodeIndex >= currentBatchNodes.size) {
                currentBatchIndex++
                currentBatchNodes = allBatches.getOrNull(currentBatchIndex) ?: emptyList()
                currentNodeIndex = 0
                if (currentBatchNodes.isEmpty()) continue
                steps.outline("starting batch ${currentBatchIndex + 1} of $totalBatches: ${currentBatchNodes.joinToString(", ")}")
            }

            val nodeId = currentBatchNodes[currentNodeIndex]
            currentNodeIndex++
            advances += 1
            // The one line that tells a watching operator the run is alive and
            // where it is. Without it, a long advance is indistinguishable from
            // a hang, and the operator's only move is to kill it.
            // Named for what it is. "advance 3 of 25" reads as "3 of 25 nodes",
            // and an operator who had just attached a 400-atom document
            // reasonably concluded the atomizer had found 25 of them. It is the
            // continuation budget: how many times this loop may iterate before
            // it stops on its own.
            steps.outline("advance $advances of at most $budget (continuation budget, not node count) [node: $nodeId]")
            val advanced = service.advanceNextResumableGoal(
                goalId = goalId,
                compactState = "self-host natural-language continuation: $nodeId"
            )
            steps += advanced.message
            latest = advanced
            val record = advanced.goal?.record
            if (!advanced.ok) {
                // Repair loop: attempt to repair the failed node before giving up
                var repairAttempts = 0
                var repairSuccess = false
                while (repairAttempts < maxRepairAttempts && !repairSuccess) {
                    repairAttempts++
                    steps += "repair attempt #$repairAttempts for node $nodeId"
                    val repairResult = repairExecutor.repair(nodeId, goalId, service)
                    if (repairResult.ok) {
                        steps += "repair succeeded: ${repairResult.message}"
                        repairSuccess = true
                        // Re-advance the same node after repair
                        val reAdvanced = service.advanceNextResumableGoal(
                            goalId = goalId,
                            compactState = "self-host repair continuation: $nodeId"
                        )
                        steps += "re-advance after repair: ${reAdvanced.message}"
                        latest = reAdvanced
                        record = reAdvanced.goal?.record
                        if (reAdvanced.ok) {
                            break // Repair succeeded, continue with next node
                        }
                    } else {
                        steps += "repair attempt #$repairAttempts failed: ${repairResult.message}"
                    }
                }
                if (!repairSuccess) {
                    steps += "all $maxRepairAttempts repair attempts failed for node $nodeId"
                    val persisted = service.resolveStatusGoal(goalId).goal?.record
                    if (persisted?.status == GoalRunStatus.RECOVERY_REQUIRED && automaticRecoveries < recoveryBudget) {
                        automaticRecoveries += 1
                        val recovery = service.recoverAndContinue(
                            goalId,
                            compactState = "self-host automatic recovery #$automaticRecoveries"
                        )
                        steps += "automatic recovery #$automaticRecoveries: ${recovery.message}"
                        latest = recovery
                        if (!recovery.ok || recovery.goal?.record?.isTerminal() == true) break
                        continue
                    }
                    break
                }
            if (record?.isTerminal() == true) break
        }

        val record = service.resolveStatusGoal(goalId).goal?.record ?: latest.goal?.record
        if (record == null) {
            return stopped(SelfHostResult(false, "self-host goal disappeared: $goalId"), null, null, steps)
        }
        gitStatusEvidence?.capture()?.let { statusLine ->
            service.addEvidence(goalId, statusLine)
            steps += statusLine
        }
        if (record.terminalCondition != GoalTerminalCondition.VERIFIED_COMPLETE) {
            service.addEvidence(goalId, service.planNextAction(goalId).evidenceLine())
            val bundle = service.exportEvidenceBundle(goalId)
            val refreshed = service.resolveStatusGoal(goalId).goal ?: SelfHostGoal(record, latest.goal?.dag)
            steps += bundle.message
            return SelfHostAutonomousRunResult(
                ok = false,
                message = "self-host stopped before promotion: ${record.terminalCondition ?: record.status}",
                goal = refreshed,
                promotion = null,
                evidenceBundle = bundle,
                steps = steps
            )
        }

        // The compile gate sits between a real source mutation and any jar
        // promotion. A nonzero exit stops the chain here: nothing downstream may
        // treat an uncompilable tree as verified.
        var compileResult: GovernedCompileGateResult? = null
        compileGate?.let { gate ->
            val compiled = gate.verify(goalId)
            compileResult = compiled
            service.addEvidence(goalId, compiled.evidenceLine())
            steps += "compile gate: passed=${compiled.passed} exit=${compiled.exitCode ?: "none"} command=${compiled.commandLine()}"
            if (!compiled.passed) {
                val stopped = service.stopForExternalInput(goalId, compiled.message)
                service.addEvidence(goalId, service.planNextAction(goalId).evidenceLine())
                val bundle = service.exportEvidenceBundle(goalId)
                steps += stopped.message
                steps += bundle.message
                return SelfHostAutonomousRunResult(
                    ok = false,
                    message = "self-host mutated source but the compile gate refused promotion: ${compiled.message}",
                    goal = service.resolveStatusGoal(goalId).goal ?: stopped.goal,
                    promotion = null,
                    evidenceBundle = bundle,
                    steps = steps,
                    compileGate = compiled
                )
            }
        }

        var builtCandidateJar: java.nio.file.Path? = null
        jarBuilder?.let { builder ->
            val built = builder.build(goalId)
            service.addEvidence(goalId, built.evidenceLine())
            steps += built.message
            if (!built.ok) {
                val stopped = service.stopForExternalInput(goalId, built.message)
                service.addEvidence(goalId, service.planNextAction(goalId).evidenceLine())
                val bundle = service.exportEvidenceBundle(goalId)
                steps += stopped.message
                steps += bundle.message
                return SelfHostAutonomousRunResult(
                    ok = false,
                    message = "self-host verified source changes but stopped before jar promotion: ${built.message}",
                    goal = service.resolveStatusGoal(goalId).goal ?: stopped.goal,
                    promotion = null,
                    evidenceBundle = bundle,
                    steps = steps,
                    compileGate = compileResult
                )
            }
            builtCandidateJar = built.candidateJar
        }

        val jarPaths = jarLocator.resolve()
        if (!jarPaths.ok || jarPaths.paths == null) {
            service.addEvidence(goalId, "jar_promotion_stop reason=${jarPaths.message}")
            val stopped = service.stopForExternalInput(goalId, jarPaths.message)
            service.addEvidence(goalId, service.planNextAction(goalId).evidenceLine())
            steps += stopped.message
            val bundle = service.exportEvidenceBundle(goalId)
            val refreshed = service.resolveStatusGoal(goalId).goal ?: stopped.goal
            steps += jarPaths.message
            steps += bundle.message
            return SelfHostAutonomousRunResult(
                ok = false,
                message = "self-host verified source changes but stopped before jar promotion: ${jarPaths.message}",
                goal = refreshed,
                promotion = null,
                evidenceBundle = bundle,
                steps = steps,
                compileGate = compileResult
            )
        }
        val paths = jarPaths.paths ?: run {
            val stopped = service.stopForExternalInput(goalId, "jar paths missing after resolution")
            val bundle = service.exportEvidenceBundle(goalId)
            return SelfHostAutonomousRunResult(
                ok = false,
                message = "self-host stopped before jar promotion: jar paths missing after resolution",
                goal = stopped.goal ?: service.resolveStatusGoal(goalId).goal,
                promotion = null,
                evidenceBundle = bundle,
                steps = steps + stopped.message + bundle.message,
                compileGate = compileResult
            )
        }
        val candidateJar = builtCandidateJar ?: paths.candidateJar

        val prePromotionBundle = service.exportEvidenceBundle(goalId)
        steps += prePromotionBundle.message
        if (!prePromotionBundle.ok) {
            val stopped = service.stopForExternalInput(goalId, prePromotionBundle.message)
            steps += stopped.message
            return SelfHostAutonomousRunResult(
                ok = false,
                message = "self-host stopped before jar promotion: ${prePromotionBundle.message}",
                goal = stopped.goal ?: service.resolveStatusGoal(goalId).goal,
                promotion = null,
                evidenceBundle = prePromotionBundle,
                steps = steps,
                compileGate = compileResult
            )
        }
        val promotion = service.promoteVerifiedJar(
            goalId = goalId,
            candidateJar = candidateJar,
            targetJar = paths.targetJar
        )
        steps += promotion.message
        val stopped = if (!promotion.promoted) {
            service.addEvidence(goalId, "jar_promotion_stop reason=${promotion.message}")
            service.stopForExternalInput(goalId, promotion.message).also { steps += it.message }
        } else {
            null
        }
        service.addEvidence(goalId, service.planNextAction(goalId).evidenceLine())
        val bundle = service.exportEvidenceBundle(goalId)
        val refreshed = service.resolveStatusGoal(goalId).goal
        if (bundle.message != prePromotionBundle.message) steps += bundle.message
        if (!bundle.ok) {
            return SelfHostAutonomousRunResult(
                ok = false,
                message = "self-host stopped after promotion outcome=${promotion.promoted}; evidence export failed: ${bundle.message}",
                goal = refreshed ?: promotion.goal,
                promotion = promotion,
                evidenceBundle = bundle,
                steps = steps
            )
        }
        return SelfHostAutonomousRunResult(
            ok = promotion.promoted,
            message = if (promotion.promoted) "self-host run promoted verified jar" else "self-host promotion refused: ${promotion.message}",
            goal = refreshed ?: stopped?.goal ?: promotion.goal,
            promotion = promotion,
            evidenceBundle = bundle,
            steps = steps,
            compileGate = compileResult
        return SelfHostAutonomousRunResult(
            ok = promotion.promoted,
            message = if (promotion.promoted) "self-host run promoted verified jar" else "self-host promotion refused: ${promotion.message}",
            goal = refreshed ?: stopped?.goal ?: promotion.goal,
            promotion = promotion,
            evidenceBundle = bundle,
            steps = steps,
            compileGate = compileResult
        )
    }

    /**
     * Plans all batches for the given DAG using the internal batch definer.
     * Returns a list of batches, where each batch is a list of node IDs that can
     * execute in parallel (non-overlapping territories).
     */
    private fun planAllBatches(dag: DagDefinition): List<List<String>> {
        val batches = batchDefiner.define(dag)
        return batches.map { it.map { it.id } }
    }

    private fun stopped(
        result: SelfHostResult,
        promotion: SelfHostPromotionResult?,
        bundle: SelfHostEvidenceBundleResult?,
        steps: List<String>
    ): SelfHostAutonomousRunResult =
        SelfHostAutonomousRunResult(
            ok = false,
            message = result.message,
            goal = result.goal,
            promotion = promotion,
            evidenceBundle = bundle,
            steps = steps
        )
}
