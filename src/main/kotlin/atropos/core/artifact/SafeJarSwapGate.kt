package atropos.core.artifact

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant

data class JarSwapEvidence(
    val passed: Boolean,
    val kind: String,
    val detail: String
)

data class JarSwapResult(
    val promoted: Boolean,
    val candidateJar: Path,
    val targetJar: Path,
    val backupJar: Path?,
    val evidence: List<JarSwapEvidence>,
    val message: String,
    val promotedAt: Instant? = null
)

/**
 * Promotes an already-built ATROPOS jar only after verification evidence passes.
 *
 * This gate does not build jars and does not replace ArtifactPipeline. It is the
 * final file-system swap guard: verify facts in, atomic promote out, previous
 * jar preserved when one existed.
 */
class SafeJarSwapGate(
    private val clock: () -> Instant = { Instant.now() }
) {
    fun promote(
        candidateJar: Path,
        targetJar: Path,
        evidence: List<JarSwapEvidence>
    ): JarSwapResult {
        val normalizedCandidate = candidateJar.toAbsolutePath().normalize()
        val normalizedTarget = targetJar.toAbsolutePath().normalize()
        val localEvidence = evidence.toMutableList()

        if (!Files.isRegularFile(normalizedCandidate)) {
            localEvidence += JarSwapEvidence(false, "candidate_exists", "candidate jar missing: $normalizedCandidate")
        } else if (Files.size(normalizedCandidate) <= 0L) {
            localEvidence += JarSwapEvidence(false, "candidate_size", "candidate jar is empty: $normalizedCandidate")
        } else {
            localEvidence += JarSwapEvidence(true, "candidate_exists", "candidate jar exists and is non-empty")
        }

        if (normalizedCandidate == normalizedTarget) {
            localEvidence += JarSwapEvidence(false, "target_distinct", "candidate and target jar are the same path")
        }

        val failed = localEvidence.filterNot { it.passed }
        if (failed.isNotEmpty()) {
            return JarSwapResult(
                promoted = false,
                candidateJar = normalizedCandidate,
                targetJar = normalizedTarget,
                backupJar = null,
                evidence = localEvidence,
                message = "jar promote refused: ${failed.joinToString("; ") { "${it.kind}: ${it.detail}" }}"
            )
        }

        val backup = if (Files.exists(normalizedTarget)) {
            normalizedTarget.resolveSibling("${normalizedTarget.fileName}.backup-${clock().toEpochMilli()}")
        } else {
            null
        }

        Files.createDirectories(normalizedTarget.parent)
        backup?.let { Files.copy(normalizedTarget, it, StandardCopyOption.REPLACE_EXISTING) }
        return try {
            Files.copy(normalizedCandidate, normalizedTarget, StandardCopyOption.REPLACE_EXISTING)
            JarSwapResult(
                promoted = true,
                candidateJar = normalizedCandidate,
                targetJar = normalizedTarget,
                backupJar = backup,
                evidence = localEvidence,
                message = "jar promoted",
                promotedAt = clock()
            )
        } catch (failure: Exception) {
            backup?.takeIf { Files.isRegularFile(it) }?.let {
                Files.copy(it, normalizedTarget, StandardCopyOption.REPLACE_EXISTING)
            }
            JarSwapResult(
                promoted = false,
                candidateJar = normalizedCandidate,
                targetJar = normalizedTarget,
                backupJar = backup,
                evidence = localEvidence + JarSwapEvidence(false, "promote_copy", failure.message ?: "copy failed"),
                message = "jar promote failed and previous jar was preserved"
            )
        }
    }
}
