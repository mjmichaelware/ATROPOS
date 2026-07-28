package atropos.core.evaluation

import atropos.core.artifact.Artifact
import atropos.core.artifact.ArtifactKind
import atropos.core.artifact.ArtifactPipeline
import atropos.core.artifact.ArtifactState
import atropos.core.artifact.ArtifactStore
import atropos.core.artifact.InstallProof
import atropos.core.artifact.VerificationEvidence
import atropos.core.artifact.VerificationKind
import atropos.core.auditor.AuditorService
import atropos.core.director.DirectorService
import atropos.core.director.DirectorStore
import atropos.core.director.DriftSeverity
import atropos.core.director.ObservationKind
import atropos.core.journal.EventCategory
import atropos.core.journal.EventJournalService
import atropos.core.memory.LocalMemoryStore
import atropos.core.territory.TerritoryAssignment
import atropos.core.territory.TerritoryService
import atropos.core.territory.TerritoryStore
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvaluationEngineTest {
    @Test
    fun releaseGatePassesOnlyWithLinkedArtifactJournalMemoryAndIndependentVerifier() {
        val root = Files.createTempDirectory("atropos-evaluation-pass-")
        val store = ArtifactStore(root)
        val artifact = Artifact(
            id = "art-pass",
            kind = ArtifactKind.BINARY_JAR,
            name = "atropos.jar",
            filePath = root.resolve("atropos.jar").toString(),
            sha256 = "abc",
            byteSize = 3,
            state = ArtifactState.READY
        )
        store.saveArtifacts(listOf(artifact))
        store.saveVerifications(listOf(VerificationEvidence(artifactId = artifact.id, kind = VerificationKind.TEST_PASS, passed = true, evidence = "tests passed")))
        store.saveInstallProofs(listOf(InstallProof(artifactId = artifact.id, targetPath = artifact.filePath, verified = true, runOutput = "ATROPOS runtime state")))
        val memory = LocalMemoryStore(root.resolve(".atropos/memory").toFile(), env = emptyMap())
        memory.rememberVerification("verify-pass", "verification", "tests passed", tags = listOf("evaluation"))
        val journal = EventJournalService(root)
        journal.record("run-pass", EventCategory.POLICY, "policy allowed bounded verification")
        journal.record("run-pass", EventCategory.VERIFICATION, "verification evidence")
        val engine = EvaluationEngine(
            repoRoot = root,
            artifactPipeline = ArtifactPipeline(store = store),
            journal = journal,
            memory = memory,
            territoryService = TerritoryService(TerritoryStore(root)),
            history = EvaluationHistoryStore(root)
        )

        val decision = engine.evaluateRelease(
            subjectId = "phase-20",
            runId = "run-pass",
            artifactIds = listOf(artifact.id),
            claimedBy = "worker",
            verifiedBy = "auditor"
        )

        assertTrue(decision.accepted, decision.reason)
        assertTrue(decision.report.metrics.all { it.passed })
    }

    @Test
    fun releaseGateBlocksFakeSuccessAndSelfApproval() {
        val root = Files.createTempDirectory("atropos-evaluation-block-")
        val store = ArtifactStore(root)
        val artifact = Artifact(
            id = "art-block",
            kind = ArtifactKind.BINARY_JAR,
            name = "atropos.jar",
            filePath = root.resolve("atropos.jar").toString(),
            sha256 = "abc",
            byteSize = 3,
            state = ArtifactState.READY
        )
        store.saveArtifacts(listOf(artifact))
        store.saveVerifications(listOf(VerificationEvidence(artifactId = artifact.id, kind = VerificationKind.TEST_PASS, passed = true, evidence = "simulated success")))
        store.saveInstallProofs(listOf(InstallProof(artifactId = artifact.id, targetPath = artifact.filePath, verified = true, runOutput = "ok")))
        val memory = LocalMemoryStore(root.resolve(".atropos/memory").toFile(), env = emptyMap())
        memory.rememberVerification("verify-block", "verification", "simulated success", tags = listOf("evaluation"))
        val journal = EventJournalService(root)
        journal.record("run-block", EventCategory.POLICY, "policy allowed bounded verification")
        journal.record("run-block", EventCategory.VERIFICATION, "simulated success")
        val engine = EvaluationEngine(
            repoRoot = root,
            artifactPipeline = ArtifactPipeline(store = store),
            journal = journal,
            memory = memory,
            territoryService = TerritoryService(TerritoryStore(root)),
            history = EvaluationHistoryStore(root)
        )

        val decision = engine.evaluateRelease(
            subjectId = "phase-20",
            runId = "run-block",
            artifactIds = listOf(artifact.id),
            claimedBy = "worker",
            verifiedBy = "worker"
        )

        assertFalse(decision.accepted)
        assertTrue(decision.reason.contains(EvaluationMetricKind.FAKE_SUCCESS_GUARD.name), decision.reason)
        assertTrue(decision.reason.contains(EvaluationMetricKind.SELF_APPROVAL_GUARD.name), decision.reason)
    }

    @Test
    fun releaseGateBlocksWhenAuditorBlocksPromotion() {
        val root = Files.createTempDirectory("atropos-evaluation-auditor-block-")
        val fixture = passingReleaseFixture(root)
        val auditor = AuditorService(root)
        auditor.auditTerritories(listOf(TerritoryAssignment(ownerId = "worker", ownerRole = "WORKER", allowedPrefix = "")))
        val engine = EvaluationEngine(
            repoRoot = root,
            artifactPipeline = ArtifactPipeline(store = fixture.store),
            journal = fixture.journal,
            memory = fixture.memory,
            territoryService = TerritoryService(TerritoryStore(root)),
            auditor = auditor,
            history = EvaluationHistoryStore(root)
        )

        val decision = engine.evaluateRelease(
            subjectId = "phase-20",
            runId = fixture.runId,
            artifactIds = listOf(fixture.artifact.id),
            claimedBy = "worker",
            verifiedBy = "auditor"
        )

        assertFalse(decision.accepted)
        assertTrue(decision.reason.contains(EvaluationMetricKind.AUDITOR_PROMOTION_GATE.name), decision.reason)
    }

    @Test
    fun releaseGateBlocksDirectorPrePromoteAdvisoryForScopedGoal() {
        val root = Files.createTempDirectory("atropos-evaluation-director-block-")
        val fixture = passingReleaseFixture(root)
        val director = DirectorService(DirectorStore(root), root)
        director.observe(
            kind = ObservationKind.MISSING_GATE,
            severity = DriftSeverity.WARNING,
            source = "test",
            details = "verification gate missing",
            goalId = "goal-1",
            files = listOf("src/main/kotlin/atropos/core/example.kt")
        )
        val engine = EvaluationEngine(
            repoRoot = root,
            artifactPipeline = ArtifactPipeline(store = fixture.store),
            journal = fixture.journal,
            memory = fixture.memory,
            territoryService = TerritoryService(TerritoryStore(root)),
            directorService = director,
            history = EvaluationHistoryStore(root)
        )

        val decision = engine.evaluateRelease(
            subjectId = "phase-20",
            runId = fixture.runId,
            artifactIds = listOf(fixture.artifact.id),
            changedFiles = listOf("src/main/kotlin/atropos/core/example.kt"),
            goalId = "goal-1",
            claimedBy = "worker",
            verifiedBy = "auditor"
        )

        assertFalse(decision.accepted)
        assertTrue(decision.reason.contains(EvaluationMetricKind.DIRECTOR_PROMOTION_ADVISORY.name), decision.reason)
    }

    private data class ReleaseFixture(
        val store: ArtifactStore,
        val artifact: Artifact,
        val journal: EventJournalService,
        val memory: LocalMemoryStore,
        val runId: String
    )

    private fun passingReleaseFixture(root: Path): ReleaseFixture {
        val store = ArtifactStore(root)
        val artifact = Artifact(
            id = "art-pass",
            kind = ArtifactKind.BINARY_JAR,
            name = "atropos.jar",
            filePath = root.resolve("atropos.jar").toString(),
            sha256 = "abc",
            byteSize = 3,
            state = ArtifactState.READY
        )
        store.saveArtifacts(listOf(artifact))
        store.saveVerifications(listOf(VerificationEvidence(artifactId = artifact.id, kind = VerificationKind.TEST_PASS, passed = true, evidence = "tests passed")))
        store.saveInstallProofs(listOf(InstallProof(artifactId = artifact.id, targetPath = artifact.filePath, verified = true, runOutput = "ATROPOS runtime state")))
        val memory = LocalMemoryStore(root.resolve(".atropos/memory").toFile(), env = emptyMap())
        memory.rememberVerification("verify-pass", "verification", "tests passed", tags = listOf("evaluation"))
        val journal = EventJournalService(root)
        journal.record("run-pass", EventCategory.POLICY, "policy allowed bounded verification")
        journal.record("run-pass", EventCategory.VERIFICATION, "verification evidence")
        return ReleaseFixture(store, artifact, journal, memory, "run-pass")
    }
}
