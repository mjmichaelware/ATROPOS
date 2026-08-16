package atropos.core.evaluation

import atropos.core.AtroposRepoRootLocator
import atropos.core.artifact.ArtifactPipeline
import atropos.core.artifact.ArtifactState
import atropos.core.auditor.AuditorService
import atropos.core.director.DirectorService
import atropos.core.director.DirectorStore
import atropos.core.journal.EventCategory
import atropos.core.journal.EventJournalService
import atropos.core.memory.LocalMemoryStore
import atropos.core.security.RedactionFilter
import atropos.core.territory.TerritoryService
import atropos.core.territory.TerritoryStore
import atropos.core.phase20.ReproducibilityGate
import atropos.core.phase20.ReproducibilityInput
import java.nio.file.Path

class EvaluationEngine(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val artifactPipeline: ArtifactPipeline = ArtifactPipeline(),
    private val journal: EventJournalService = EventJournalService(repoRoot),
    private val memory: LocalMemoryStore = LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile()),
    private val territoryService: TerritoryService = TerritoryService(TerritoryStore(repoRoot)),
    private val auditor: AuditorService = AuditorService(repoRoot),
    private val directorService: DirectorService = DirectorService(DirectorStore(repoRoot), repoRoot),
    private val history: EvaluationHistoryStore = EvaluationHistoryStore(repoRoot),
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val reproducibilityGate: ReproducibilityGate = ReproducibilityGate(),
    private val releaseGateEvaluator: ReleaseGateEvaluator = ReleaseGateEvaluator(),
    private val benchmarkRunner: BenchmarkRunner = BenchmarkRunner(repoRoot)
) {
    private val metricCatalog = AtroposMetrics()
    private val dashboard = EvaluationDashboard()
    fun evaluateRelease(
        subjectId: String,
        runId: String? = null,
        artifactIds: List<String> = emptyList(),
        changedFiles: List<String> = emptyList(),
        goalId: String? = null,
        claimedBy: String? = null,
        verifiedBy: String? = null,
        reproducibilityExpectedFiles: Map<String, String>? = null
    ): ReleaseGateDecision {
        val report = buildReport(subjectId, runId, artifactIds, changedFiles, goalId, claimedBy, verifiedBy, reproducibilityExpectedFiles = reproducibilityExpectedFiles)
        return releaseGateEvaluator.evaluate(report).also {
            history.append(it.report)
            dashboard.renderJson(emptyList(), benchmarkRunner.report())
        }
    }

    fun evaluatePromotionRelease(
        subjectId: String,
        runId: String,
        artifactIds: List<String>,
        changedFiles: List<String>,
        goalId: String,
        claimedBy: String,
        verifiedBy: String,
        reproducibilityExpectedFiles: Map<String, String>? = null
    ): ReleaseGateDecision {
        val report = buildReport(
            subjectId = subjectId,
            runId = runId,
            artifactIds = artifactIds,
            changedFiles = changedFiles,
            goalId = goalId,
            claimedBy = claimedBy,
            verifiedBy = verifiedBy,
            reproducibilityExpectedFiles = reproducibilityExpectedFiles,
            requirePromotionScope = true
        )
        return releaseGateEvaluator.evaluate(report).also {
            history.append(it.report)
            dashboard.renderJson(emptyList(), benchmarkRunner.report())
        }
    }

    private fun buildReport(
        subjectId: String,
        runId: String?,
        artifactIds: List<String>,
        changedFiles: List<String>,
        goalId: String?,
        claimedBy: String?,
        verifiedBy: String?,
        reproducibilityExpectedFiles: Map<String, String>? = null,
        requirePromotionScope: Boolean = false
    ): EvaluationReport {
        val artifacts = artifactPipeline.report().artifacts.filter { artifactIds.isEmpty() || it.id in artifactIds }
        val verifications = artifactPipeline.report().verifications.filter { artifactIds.isEmpty() || it.artifactId in artifactIds }
        val proofs = artifactPipeline.report().installProofs.filter { artifactIds.isEmpty() || it.artifactId in artifactIds }
        val metrics = mutableListOf<EvaluationMetric>()

        metrics += metric(
            EvaluationMetricKind.ARTIFACT_READY,
            artifacts.isNotEmpty() && artifacts.all { it.state == ArtifactState.READY },
            "ready=${artifacts.count { it.state == ArtifactState.READY }} total=${artifacts.size}"
        )
        metrics += metric(
            EvaluationMetricKind.VERIFICATION_PASSED,
            verifications.isNotEmpty() && verifications.all { it.passed },
            "passed=${verifications.count { it.passed }} total=${verifications.size}"
        )
        metrics += metric(
            EvaluationMetricKind.INSTALL_OR_RUN_PROOF,
            proofs.any { it.verified },
            "verifiedProofs=${proofs.count { it.verified }} total=${proofs.size}"
        )

        val runEvents = runId?.let { journal.readEvents(it, limit = 5000) } ?: emptyList()
        metrics += metric(
            EvaluationMetricKind.JOURNAL_EVIDENCE,
            runId == null || runEvents.any { it.category in setOf(EventCategory.VERIFICATION, EventCategory.TEST, EventCategory.COMPLETION) },
            "run=${runId ?: "none"} evidenceEvents=${runEvents.count { it.category in setOf(EventCategory.VERIFICATION, EventCategory.TEST, EventCategory.COMPLETION) }} " +
                "uncoveredMetricDefinitions=${metricCatalog.uncovered().size}"
        )

        val memoryEvidence = memory.findBySubjectTypes(setOf("verification", "tool", "reward", "recovery"), limit = 200)
        metrics += metric(
            EvaluationMetricKind.MEMORY_EVIDENCE,
            memoryEvidence.isNotEmpty(),
            "records=${memoryEvidence.size}"
        )

        val policyEvents = runEvents.filter { it.category == EventCategory.POLICY }
        metrics += metric(
            EvaluationMetricKind.POLICY_EVIDENCE,
            runId == null || policyEvents.isNotEmpty(),
            "policyEvents=${policyEvents.size}"
        )

        metrics += metric(
            EvaluationMetricKind.PROMOTION_SCOPE_EVIDENCE,
            !requirePromotionScope || (goalId != null && changedFiles.isNotEmpty()),
            "required=$requirePromotionScope goal=${goalId ?: "none"} changedFiles=${changedFiles.size}"
        )

        val territoryViolations = territoryService.getViolations().filter { violation ->
            changedFiles.isEmpty() || changedFiles.any { file -> violation.filePath == file || file.startsWith(violation.filePath) }
        }
        metrics += metric(
            EvaluationMetricKind.TERRITORY_EVIDENCE,
            territoryViolations.isEmpty(),
            "violations=${territoryViolations.size}"
        )
        val directorAdvisory = if (goalId != null || changedFiles.isNotEmpty()) {
            directorService.advisoryBeforePromotion(goalId = goalId, files = changedFiles)
        } else {
            null
        }
        metrics += metric(
            EvaluationMetricKind.DIRECTOR_PROMOTION_ADVISORY,
            directorAdvisory?.allowed ?: true,
            directorAdvisory?.message ?: "director advisory: no scoped promotion context"
        )

        val visibleEvidence = buildString {
            artifacts.forEach { appendLine(it.name); appendLine(it.filePath); appendLine(it.metadata.values.joinToString(" ")) }
            verifications.forEach { appendLine(it.evidence) }
            proofs.forEach { appendLine(it.runOutput) }
            runEvents.forEach { appendLine(it.payload) }
        }
        // A release with no scoped files still needs an explicit audit record;
        // otherwise the fail-closed auditor quite correctly treats an empty
        // report as an unaudited promotion.
        auditor.auditSecretText("evaluation-visible-evidence", visibleEvidence)
        val auditorDecision = auditor.blockPromotion(claimedBy = claimedBy, auditedBy = verifiedBy ?: "auditor")
        metrics += metric(
            EvaluationMetricKind.AUDITOR_PROMOTION_GATE,
            auditorDecision.allowed,
            auditorDecision.message
        )
        metrics += metric(
            EvaluationMetricKind.SECRET_SAFETY,
            redactionFilter.redact(visibleEvidence) == visibleEvidence,
            "redactionDelta=${redactionFilter.redact(visibleEvidence) != visibleEvidence}"
        )
        metrics += metric(
            EvaluationMetricKind.SELF_APPROVAL_GUARD,
            claimedBy == null || verifiedBy == null || claimedBy != verifiedBy,
            "claimedBy=${claimedBy ?: "unknown"} verifiedBy=${verifiedBy ?: "unknown"}"
        )
        val evaluationSpecResult = atropos.core.acceptance.EvaluationSpecIntegration()
            .runSpec(metrics.map { it.passed })
        metrics += metric(
            EvaluationMetricKind.FAKE_SUCCESS_GUARD,
            noFakeSuccess(visibleEvidence) && evaluationSpecResult.passed,
            "fakeSuccessPattern=${!noFakeSuccess(visibleEvidence)} specCoverage=${evaluationSpecResult.metrics["coverage"]}"
        )
        metrics += metric(
            EvaluationMetricKind.MYTHOLOGY_GUARD,
            noMythology(visibleEvidence),
            "mythologyPattern=${!noMythology(visibleEvidence)}"
        )

        reproducibilityExpectedFiles?.let { expectedFiles ->
            val result = reproducibilityGate.evaluate(
                ReproducibilityInput(repoRoot, expectedFiles)
            )
            metrics += EvaluationMetric(
                kind = EvaluationMetricKind.REPRODUCIBILITY,
                passed = result.passed,
                severity = EvaluationSeverity.BLOCKER,
                evidence = "${result.reason} files=${result.comparedFileCount}/${result.expectedFileCount} " +
                    "snapshot=${result.snapshotSha256}"
            )
        }

        return EvaluationReport(
            subjectId = subjectId,
            runId = runId,
            artifactIds = artifactIds,
            metrics = metrics
        )
    }

    private fun noFakeSuccess(text: String): Boolean {
        val lower = text.lowercase()
        return listOf("simulated success", "fake success", "placeholder green", "constant true").none { it in lower }
    }

    private fun noMythology(text: String): Boolean {
        val lower = text.lowercase()
        return listOf("mythology", "self-aware", "sentient", "conscious").none { it in lower }
    }

    private fun metric(kind: EvaluationMetricKind, passed: Boolean, evidence: String): EvaluationMetric =
        EvaluationMetric(kind, passed, EvaluationSeverity.BLOCKER, evidence)
}
