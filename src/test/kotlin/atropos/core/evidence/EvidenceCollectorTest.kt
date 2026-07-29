package atropos.core.evidence

import atropos.core.artifact.Artifact
import atropos.core.artifact.ArtifactKind
import atropos.core.artifact.ArtifactPipeline
import atropos.core.artifact.ArtifactState
import atropos.core.artifact.ArtifactStore
import atropos.core.artifact.InstallProof
import atropos.core.artifact.VerificationEvidence
import atropos.core.artifact.VerificationKind
import atropos.core.evaluation.EvaluationHistoryStore
import atropos.core.evaluation.EvaluationMetric
import atropos.core.evaluation.EvaluationMetricKind
import atropos.core.evaluation.EvaluationReport
import atropos.core.evaluation.EvaluationSeverity
import atropos.core.journal.EventCategory
import atropos.core.journal.EventJournalService
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvidenceCollectorTest {
    @Test
    fun collectsRedactedEvidenceAcrossExistingStores() {
        val root = Files.createTempDirectory("atropos-evidence-collector-")
        val store = ArtifactStore(root)
        val artifact = Artifact(
            id = "art-1",
            kind = ArtifactKind.BINARY_JAR,
            name = "atropos.jar",
            filePath = root.resolve("atropos.jar").toString(),
            sha256 = "abc",
            byteSize = 3,
            state = ArtifactState.READY
        )
        store.saveArtifacts(listOf(artifact))
        store.saveVerifications(
            listOf(
                VerificationEvidence(
                    id = "ev-1",
                    artifactId = artifact.id,
                    kind = VerificationKind.TEST_PASS,
                    passed = true,
                    evidence = "passed with sk-${"A".repeat(24)}"
                )
            )
        )
        store.saveInstallProofs(
            listOf(
                InstallProof(
                    id = "proof-1",
                    artifactId = artifact.id,
                    targetPath = artifact.filePath,
                    verified = true,
                    runOutput = "Authorization: Bearer ${"B".repeat(24)}"
                )
            )
        )
        val journal = EventJournalService(root)
        journal.record("run-1", EventCategory.VERIFICATION, "journal sk-${"C".repeat(24)}")
        val history = EvaluationHistoryStore(root)
        history.append(
            EvaluationReport(
                id = "eval-1",
                subjectId = "subject-1",
                runId = "run-1",
                artifactIds = listOf(artifact.id),
                metrics = listOf(
                    EvaluationMetric(EvaluationMetricKind.VERIFICATION_PASSED, true, EvaluationSeverity.BLOCKER, "ok")
                )
            )
        )
        val collector = EvidenceCollector(
            repoRoot = root,
            artifactPipeline = ArtifactPipeline(store = store),
            journal = journal,
            evaluationHistory = history
        )

        val bundle = collector.collect(subjectId = "subject-1", runId = "run-1", artifactIds = listOf(artifact.id))
        val exported = bundle.exportMarkdown()

        assertEquals(
            setOf(EvidenceSource.ARTIFACT, EvidenceSource.VERIFICATION, EvidenceSource.INSTALL_PROOF, EvidenceSource.JOURNAL, EvidenceSource.EVALUATION),
            bundle.records.map { it.source }.toSet()
        )
        assertTrue(exported.contains("Evidence Bundle"))
        assertFalse(exported.contains("A".repeat(24)), exported)
        assertFalse(exported.contains("B".repeat(24)), exported)
        assertFalse(exported.contains("C".repeat(24)), exported)
    }
}
