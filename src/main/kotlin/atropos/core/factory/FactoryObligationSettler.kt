/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import atropos.core.project.ProjectRecord
import atropos.core.project.ProjectRegistry
import atropos.core.project.ProjectStatus
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

/**
 * Executes the post-generation obligation loop, repair re-entry, economics calculation,
 * and final status transitions for [FactoryRunOrchestrator].
 */
class FactoryObligationSettler(
    private val repoRoot: Path,
    private val projectRegistry: ProjectRegistry,
    private val obligationLoop: FactoryObligationLoop,
    private val recorder: FactoryRunEventRecorder,
    private val repairVerificationFailure: ((FactoryPlan, Path, Throwable, FactoryAcceptanceFreeze) -> FactoryAcceptanceFreeze.RepairEvidence)?
) {
    fun settle(
        plan: FactoryPlan,
        lineage: FactoryLineage,
        planningDagId: String,
        acceptanceFreeze: FactoryAcceptanceFreeze,
        generatedProject: GeneratedAppProject,
        generatedRecord: ProjectRecord,
        startedAt: Instant,
        softFailures: List<String>,
        onRepairExecuted: () -> Unit
    ): Pair<FactoryRunEconomics, String> {
        fun repairAfterVerificationFailure(failure: Throwable): FactoryLoopResult {
            val repairAction = repairVerificationFailure
                ?: throw failure
            return try {
                onRepairExecuted()
                val evidence = repairAction(
                    plan,
                    Path.of(generatedProject.path),
                    failure,
                    acceptanceFreeze
                )
                val handoff = FactoryRunHandoff.read(repoRoot, plan.id)
                val result = FactoryRepairExecutor(obligationLoop).repairAndResume(
                    handoff = handoff,
                    freeze = acceptanceFreeze,
                    repair = { evidence },
                    executeWave = FactoryEvidenceWaveExecutor(
                        Path.of(generatedProject.evidencePath),
                        acceptanceFreeze
                    )::execute
                )
                recorder.recordRepair(
                    runId = plan.id,
                    state = "REENTERED_OBLIGATION_LOOP",
                    verification = result.evidence,
                    dagId = planningDagId,
                    promptFingerprint = lineage.promptFingerprint
                )
                result.loop
            } catch (repairFailure: Throwable) {
                failure.addSuppressed(repairFailure)
                throw failure
            }
        }

        var loopResult = try {
            obligationLoop.executeUntilSettled(
                planningDagId,
                acceptanceFreeze,
                FactoryEvidenceWaveExecutor(
                    Path.of(generatedProject.evidencePath),
                    acceptanceFreeze
                )::execute
            )
        } catch (failure: Throwable) {
            try {
                repairAfterVerificationFailure(failure)
            } catch (terminalFailure: Throwable) {
                projectRegistry.setStatus(generatedRecord, ProjectStatus.FAILED, actor = "factory")
                recorder.recordCompletionFailure(
                    runId = plan.id,
                    failureType = terminalFailure.javaClass.simpleName,
                    dagId = planningDagId,
                    promptFingerprint = lineage.promptFingerprint
                )
                throw terminalFailure
            }
        }
        if (!loopResult.snapshot.canComplete) {
            loopResult = try {
                repairAfterVerificationFailure(
                    IllegalStateException(
                        "factory verification left obligations unresolved: " +
                            "open_work=${loopResult.snapshot.openWork} " +
                            "blocked=${loopResult.snapshot.blockedAtomIds} " +
                            "failed=${loopResult.snapshot.failedAtomIds}"
                    )
                )
            } catch (failure: Throwable) {
                projectRegistry.setStatus(generatedRecord, ProjectStatus.FAILED, actor = "factory")
                recorder.recordCompletionFailure(
                    runId = plan.id,
                    failureType = failure.javaClass.simpleName,
                    dagId = planningDagId,
                    promptFingerprint = lineage.promptFingerprint
                )
                throw failure
            }
        }
        val finalObligations = loopResult.snapshot
        recorder.recordObligationLoop(
            runId = plan.id,
            snapshot = finalObligations,
            dagId = planningDagId,
            promptFingerprint = lineage.promptFingerprint
        )
        check(finalObligations.canComplete) {
            "factory completion refused: open_work=${finalObligations.openWork} " +
                "blocked=${finalObligations.blockedAtomIds} failed=${finalObligations.failedAtomIds}"
        }
        check(loopResult.terminationReason == "open_work=0" && finalObligations.canComplete) {
            "factory completion refused: termination=${loopResult.terminationReason} open_work=${finalObligations.openWork} " +
                "blocked=${finalObligations.blockedAtomIds} failed=${finalObligations.failedAtomIds}"
        }
        val terminationReason = "open_work=0 waves=${loopResult.wavesExecuted} acceptance_freeze_green completion_gate_green evidence_complete"
        val economics = FactoryRunEconomics(
            wallTimeMillis = Duration.between(startedAt, Instant.now()).toMillis(),
            providerCalls = null,
            tokens = null,
            atomsDone = finalObligations.doneAtomIds.size,
            atomsFailed = finalObligations.failedAtomIds.size,
            atomsBlocked = finalObligations.blockedAtomIds.size,
            softSkips = softFailures.size,
            gateDecision = "PASS",
            terminationReason = terminationReason
        )
        recorder.recordEconomics(
            runId = plan.id,
            economics = economics,
            dagId = planningDagId,
            promptFingerprint = lineage.promptFingerprint
        )
        FactoryRunHandoff.write(
            repoRoot = repoRoot,
            runId = plan.id,
            dagId = planningDagId,
            snapshot = finalObligations,
            freeze = acceptanceFreeze,
            lastGoodCommit = generatedProject.commitId,
            evidencePath = generatedProject.evidencePath
        )
        return economics to terminationReason
    }

    fun completeProject(
        plan: FactoryPlan,
        lineage: FactoryLineage,
        planningDagId: String,
        generatedRecord: ProjectRecord,
        generatedProject: GeneratedAppProject
    ): ProjectRecord {
        val project = try {
            projectRegistry.update(
                generatedRecord.copy(
                    status = ProjectStatus.COMPLETED,
                    evidenceIds = (generatedRecord.evidenceIds + generatedProject.evidencePath).distinct()
                ),
                event = "completed",
                actor = "factory",
                message = "factory completion evidence linked: ${generatedProject.evidencePath}"
            )
        } catch (failure: Throwable) {
            projectRegistry.setStatus(generatedRecord, ProjectStatus.FAILED, actor = "factory")
            recorder.recordCompletionFailure(
                runId = plan.id,
                failureType = failure.javaClass.simpleName,
                dagId = planningDagId,
                promptFingerprint = lineage.promptFingerprint
            )
            throw failure
        }
        try {
            recorder.recordCompletion(
                runId = plan.id,
                projectId = project.id,
                evidencePath = generatedProject.evidencePath,
                dagId = planningDagId,
                promptFingerprint = lineage.promptFingerprint
            )
        } catch (failure: Throwable) {
            projectRegistry.setStatus(project, ProjectStatus.FAILED, actor = "factory")
            throw failure
        }
        return project
    }
}
