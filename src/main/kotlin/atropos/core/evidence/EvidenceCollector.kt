package atropos.core.evidence

import atropos.core.AtroposRepoRootLocator
import atropos.core.artifact.ArtifactPipeline
import atropos.core.evaluation.EvaluationHistoryStore
import atropos.core.journal.EventJournalService
import atropos.core.security.RedactionFilter
import java.nio.file.Path
import java.time.Instant

enum class EvidenceSource {
    ARTIFACT,
    VERIFICATION,
    INSTALL_PROOF,
    JOURNAL,
    EVALUATION
}

data class CollectedEvidence(
    val source: EvidenceSource,
    val id: String,
    val subjectId: String? = null,
    val runId: String? = null,
    val artifactId: String? = null,
    val kind: String,
    val payload: String
)

data class EvidenceBundle(
    val records: List<CollectedEvidence>,
    val collectedAt: Instant = Instant.now()
) {
    val summary: String
        get() = "evidence records=${records.size} sources=${records.map { it.source }.distinct().joinToString(",")}"

    fun exportMarkdown(): String = buildString {
        appendLine("# Evidence Bundle")
        appendLine()
        appendLine(summary)
        appendLine()
        records.forEach { record ->
            appendLine("## ${record.source}:${record.id}")
            appendLine()
            appendLine(record.payload)
            appendLine()
        }
    }.trimEnd()
}

class EvidenceCollector(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val artifactPipeline: ArtifactPipeline = ArtifactPipeline(),
    private val journal: EventJournalService = EventJournalService(repoRoot),
    private val evaluationHistory: EvaluationHistoryStore = EvaluationHistoryStore(repoRoot),
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    fun collect(
        subjectId: String? = null,
        runId: String? = null,
        artifactIds: List<String> = emptyList(),
        limit: Int = 500
    ): EvidenceBundle {
        val records = mutableListOf<CollectedEvidence>()
        val artifactReport = artifactPipeline.report()
        val selectedArtifacts = artifactReport.artifacts.filter { artifactIds.isEmpty() || it.id in artifactIds }

        selectedArtifacts.forEach { artifact ->
            records += CollectedEvidence(
                source = EvidenceSource.ARTIFACT,
                id = artifact.id,
                subjectId = subjectId,
                artifactId = artifact.id,
                kind = artifact.kind.name,
                payload = redact("state=${artifact.state} path=${artifact.filePath} hash=${artifact.sha256} bytes=${artifact.byteSize}")
            )
        }
        artifactReport.verifications
            .filter { artifactIds.isEmpty() || it.artifactId in artifactIds }
            .forEach { evidence ->
                records += CollectedEvidence(
                    source = EvidenceSource.VERIFICATION,
                    id = evidence.id,
                    subjectId = subjectId,
                    artifactId = evidence.artifactId,
                    kind = evidence.kind.name,
                    payload = redact("passed=${evidence.passed} evidence=${evidence.evidence}")
                )
            }
        artifactReport.installProofs
            .filter { artifactIds.isEmpty() || it.artifactId in artifactIds }
            .forEach { proof ->
                records += CollectedEvidence(
                    source = EvidenceSource.INSTALL_PROOF,
                    id = proof.id,
                    subjectId = subjectId,
                    artifactId = proof.artifactId,
                    kind = "INSTALL_PROOF",
                    payload = redact("verified=${proof.verified} target=${proof.targetPath} output=${proof.runOutput}")
                )
            }
        runId?.let { id ->
            journal.readEvents(id, limit = limit).forEach { event ->
                records += CollectedEvidence(
                    source = EvidenceSource.JOURNAL,
                    id = "${id}:${event.sequence}",
                    subjectId = subjectId,
                    runId = id,
                    kind = event.category.name,
                    payload = redact(event.payload)
                )
            }
        }
        subjectId?.let { subject ->
            evaluationHistory.latestFor(subject)?.let { report ->
                records += CollectedEvidence(
                    source = EvidenceSource.EVALUATION,
                    id = report.id,
                    subjectId = subject,
                    runId = report.runId,
                    kind = "RELEASE_GATE",
                    payload = redact(report.summary)
                )
            }
        }

        return EvidenceBundle(records.takeLast(limit.coerceIn(1, 5000)))
    }

    /**
     * SD7-ADV-002: Content-addressed evidence Merkle tree.
     * All evidence is an append-only CAS Merkle tree of hashes allowing
     * third-party verifiers to validate the Zero-Entropy Proof Certificate in seconds.
     */
    fun collect(runId: String, targetDir: Path): CollectedEvidence {
        val files = targetDir.toFile().walkTopDown().filter { it.isFile }.toList()
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val hashes = files.map { file ->
            val bytes = file.readBytes()
            java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
        }.sorted()

        hashes.forEach { h -> md.update(h.toByteArray(Charsets.UTF_8)) }
        val merkleRoot = if (hashes.isEmpty()) {
            "0000000000000000000000000000000000000000000000000000000000000000"
        } else {
            md.digest().joinToString("") { "%02x".format(it) }
        }

        return CollectedEvidence(
            source = EvidenceSource.VERIFICATION,
            id = "merkle-$merkleRoot",
            subjectId = runId,
            runId = runId,
            artifactId = null,
            kind = "CAS_MERKLE_TREE",
            payload = "merkle_root=$merkleRoot leaf_hashes=${hashes.size} target_dir=$targetDir"
        )
    }

    private fun redact(value: String): String = redactionFilter.compact(redactionFilter.redact(value), 4000)
}
