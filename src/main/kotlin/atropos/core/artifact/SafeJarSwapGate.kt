package atropos.core.artifact

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.LinkOption
import java.security.MessageDigest
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

        if (evidence.isEmpty()) {
            localEvidence += JarSwapEvidence(false, "verification_evidence", "no independent verification evidence supplied")
        } else if (evidence.any { !it.passed }) {
            localEvidence += JarSwapEvidence(false, "verification_evidence", "independent verification evidence contains a failure")
        }

        if (!Files.isRegularFile(normalizedCandidate, LinkOption.NOFOLLOW_LINKS)) {
            localEvidence += JarSwapEvidence(false, "candidate_exists", "candidate jar missing: $normalizedCandidate")
        } else if (Files.size(normalizedCandidate) <= 0L) {
            localEvidence += JarSwapEvidence(false, "candidate_size", "candidate jar is empty: $normalizedCandidate")
        } else {
            localEvidence += JarSwapEvidence(true, "candidate_exists", "candidate jar exists and is non-empty")
        }

        if (Files.exists(normalizedTarget, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isRegularFile(normalizedTarget, LinkOption.NOFOLLOW_LINKS)) {
            localEvidence += JarSwapEvidence(false, "target_regular_file", "target jar is not a regular file")
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

        var previousHash: String? = null
        return try {
            Files.createDirectories(normalizedTarget.parent)
            val candidateHash = sha256(normalizedCandidate)
                ?: throw IllegalStateException("candidate jar hash could not be computed")
            localEvidence += JarSwapEvidence(true, "candidate_sha256", candidateHash)
            previousHash = if (Files.exists(normalizedTarget, LinkOption.NOFOLLOW_LINKS)) {
                sha256(normalizedTarget)
                    ?: throw IllegalStateException("previous jar hash could not be computed")
            } else {
                null
            }
            backup?.let { backupPath ->
                if (Files.exists(backupPath, LinkOption.NOFOLLOW_LINKS)) {
                    throw IllegalStateException("backup path already exists: $backupPath")
                }
                Files.copy(normalizedTarget, backupPath)
                if (!Files.isRegularFile(backupPath, LinkOption.NOFOLLOW_LINKS) || Files.size(backupPath) <= 0L) {
                    throw IllegalStateException("previous jar backup was not preserved: $backupPath")
                }
                val backupHash = sha256(backupPath)
                if (backupHash != previousHash) {
                    throw IllegalStateException("previous jar backup bytes do not match the active jar")
                }
                localEvidence += JarSwapEvidence(true, "backup_sha256", backupHash ?: "missing")
            }
            Files.copy(normalizedCandidate, normalizedTarget, StandardCopyOption.REPLACE_EXISTING)
            if (!Files.isRegularFile(normalizedTarget, LinkOption.NOFOLLOW_LINKS) || Files.size(normalizedTarget) <= 0L) {
                throw IllegalStateException("promoted target jar was not written: $normalizedTarget")
            }
            val targetHash = sha256(normalizedTarget)
            if (targetHash != candidateHash) {
                throw IllegalStateException("promoted target bytes do not match the candidate jar")
            }
            localEvidence += JarSwapEvidence(true, "target_sha256", targetHash ?: "missing")
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
            val rollback = backup?.let { backupPath ->
                if (Files.isRegularFile(backupPath, LinkOption.NOFOLLOW_LINKS)) {
                    runCatching {
                        Files.copy(backupPath, normalizedTarget, StandardCopyOption.REPLACE_EXISTING)
                        val restoredHash = sha256(normalizedTarget)
                        val backupHash = sha256(backupPath)
                        check(restoredHash != null && restoredHash == backupHash) {
                            "rollback bytes do not match the preserved backup"
                        }
                    }
                } else {
                    runCatching {
                        check(previousHash != null && sha256(normalizedTarget) == previousHash) {
                            "active jar changed and no usable backup exists"
                        }
                    }
                }
            }
            val rollbackEvidence = if (rollback == null) {
                JarSwapEvidence(true, "rollback", "no previous jar required restoration")
            } else if (rollback.isSuccess) {
                JarSwapEvidence(true, "rollback", "previous jar restored or remained unchanged with hash verified")
            } else {
                JarSwapEvidence(false, "rollback", "previous jar restoration failed: ${rollback.exceptionOrNull()?.message ?: "unknown failure"}")
            }
            JarSwapResult(
                promoted = false,
                candidateJar = normalizedCandidate,
                targetJar = normalizedTarget,
                backupJar = backup,
                evidence = localEvidence + JarSwapEvidence(false, "promote_copy", failure.message ?: "copy failed") + rollbackEvidence,
                message = if (rollbackEvidence.passed) {
                    "jar promote failed and previous jar was restored"
                } else {
                    "jar promote failed and previous jar restoration failed"
                }
            )
        }
    }

    private fun sha256(path: Path): String? {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return null
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
