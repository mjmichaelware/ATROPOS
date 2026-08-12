package atropos.core.director

import atropos.core.AtroposRepoRootLocator
import atropos.core.policy.BoundedProcessRunner
import atropos.core.territory.TerritoryAssignment
import java.io.BufferedReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

private const val MAX_GIT_STATUS_BYTES = 256 * 1024

class DirectorService(
    private val store: DirectorStore = DirectorStore(),
    private val repoRoot: Path = AtroposRepoRootLocator.resolve()
) {
    private val processRunner = BoundedProcessRunner()
    fun observe(
        kind: ObservationKind,
        severity: DriftSeverity,
        source: String,
        details: String,
        files: List<String> = emptyList(),
        symbols: List<String> = emptyList(),
        goalId: String? = null,
        territoryId: String? = null,
        claimId: String? = null,
        worktreePath: String? = null,
        sourceCoordinates: List<String> = emptyList(),
        evidencePaths: List<String> = emptyList()
    ): DirectorObservation {
        require(source.isNotBlank()) { "director observation source is required" }
        require(details.isNotBlank()) { "director observation details are required" }
        val obs = DirectorObservation(
            kind = kind,
            severity = severity,
            source = source,
            details = details,
            goalId = goalId,
            territoryId = territoryId,
            claimId = claimId,
            worktreePath = worktreePath,
            sourceCoordinates = sourceCoordinates,
            evidencePaths = evidencePaths,
            filePaths = files,
            symbols = symbols
        )
        store.appendObservation(obs)
        return obs
    }

    fun scanDiffForDrift(
        territories: List<TerritoryAssignment> = emptyList(),
        goalId: String? = null,
        territoryId: String? = null
    ): List<DirectorObservation> {
        val observations = mutableListOf<DirectorObservation>()
        val status = runGitStatus()
        val diff = status.output

        status.failure?.let { failure ->
            observations += DirectorObservation(
                kind = ObservationKind.MISSING_GATE,
                severity = DriftSeverity.CRITICAL,
                source = "director/diff-scan",
                details = failure,
                goalId = goalId,
                territoryId = territoryId
            )
        }

        if (diff != null) {
            val changedFiles = extractChangedFiles(diff)
            if (changedFiles.isNotEmpty()) {
                observations += DirectorObservation(
                    kind = ObservationKind.DIFF_DRIFT,
                    severity = if (changedFiles.size > 5) DriftSeverity.WARNING else DriftSeverity.ADVISORY,
                    source = "director/diff-scan",
                    details = "${changedFiles.size} files changed in working tree",
                    goalId = goalId,
                    territoryId = territoryId,
                    filePaths = changedFiles
                )
            }

            val currentDiffHash = diffHash(diff)
            val priorHash = readPriorHash()
            if (priorHash != null && currentDiffHash != priorHash) {
                observations += DirectorObservation(
                    kind = ObservationKind.DIFF_DRIFT,
                    severity = DriftSeverity.ADVISORY,
                    source = "director/diff-drift",
                    details = "diff hash changed from ${priorHash.take(12)} to ${currentDiffHash.take(12)}",
                    goalId = goalId,
                    territoryId = territoryId
                )
            }

            for (t in territories) {
                for (f in changedFiles) {
                    if (!isWithinTerritory(f, t.allowedPrefix)) {
                        observations += DirectorObservation(
                            kind = ObservationKind.TERRITORY_VIOLATION,
                            severity = DriftSeverity.WARNING,
                            source = "director/territory-enforcement",
                            details = "file $f outside territory ${t.allowedPrefix}",
                            filePaths = listOf(f),
                            goalId = goalId,
                            territoryId = territoryId ?: t.id
                        )
                    }
                }
            }
        }

        observations.forEach { store.appendObservation(it) }
        val chFiles = if (diff != null) extractChangedFiles(diff) else emptyList()
        val snapshotInput = diff ?: "status-unavailable:${status.failure ?: "unknown"}"
        saveSnapshot(diffHash(snapshotInput), chFiles)
        return observations
    }

    fun advisoryReport(): AdvisoryReport {
        val observations = store.unacknowledged()
        val violations = observations.count { it.kind == ObservationKind.TERRITORY_VIOLATION }
        return AdvisoryReport(
            observations = observations,
            summary = "${observations.size} active observations, $violations territory violations",
            territoryViolations = violations
        )
    }

    fun advisoryBeforePromotion(goalId: String? = null, territoryIds: List<String> = emptyList(), files: List<String> = emptyList()): DirectorPromotionAdvisory {
        val observations = store.unacknowledged().filter { observation ->
            val goalMatches = goalId == null || observation.goalId == null || observation.goalId == goalId
            val territoryMatches = territoryIds.isEmpty() || observation.territoryId == null || observation.territoryId in territoryIds
            val fileMatches = files.isEmpty() ||
                observation.filePaths.isEmpty() ||
                observation.filePaths.any { observed -> files.any { requested -> pathsOverlap(observed, requested) } }
            goalMatches && territoryMatches && fileMatches
        }
        val blocking = observations.filter {
            it.severity == DriftSeverity.CRITICAL ||
                it.kind in setOf(ObservationKind.TERRITORY_VIOLATION, ObservationKind.POLICY_VIOLATION, ObservationKind.MISSING_GATE)
        }
        return DirectorPromotionAdvisory(
            allowed = blocking.isEmpty(),
            blockingObservations = blocking,
            message = if (blocking.isEmpty()) {
                "director advisory: no blocking drift"
            } else {
                "director advisory: ${blocking.size} blocking observations before promotion"
            }
        )
    }

    fun acknowledge(obsId: String): Boolean = updateObservation(obsId) { it.copy(acknowledged = true) }

    fun dismiss(obsId: String): Boolean = updateObservation(obsId) { it.copy(dismissed = true) }

    private fun updateObservation(obsId: String, transform: (DirectorObservation) -> DirectorObservation): Boolean {
        val all = store.readAll().toMutableList()
        val idx = all.indexOfFirst { it.id == obsId }
        if (idx < 0) return false
        all[idx] = transform(all[idx])
        rewriteAll(all)
        return true
    }

    private fun pathsOverlap(left: String, right: String): Boolean {
        val a = left.trim().trimEnd('/')
        val b = right.trim().trimEnd('/')
        if (a.isEmpty() || b.isEmpty()) return true
        return a == b || a.startsWith("$b/") || b.startsWith("$a/")
    }

    private fun isWithinTerritory(file: String, prefix: String): Boolean {
        val normalizedFile = file.replace('\\', '/').trim().trim('/')
        val normalizedPrefix = prefix.replace('\\', '/').trim().trim('/')
        if (normalizedPrefix.isBlank() || normalizedPrefix == "*" || normalizedPrefix == "root") return true
        return TerritoryAssignment(
            ownerId = "director",
            ownerRole = "DIRECTOR",
            allowedPrefix = normalizedPrefix
        ).allows(normalizedFile)
    }

    private fun rewriteAll(observations: List<DirectorObservation>) {
        store.replaceAll(observations)
    }

    private data class GitStatusSnapshot(
        val output: String?,
        val failure: String?
    )

    private fun runGitStatus(): GitStatusSnapshot {
        val result = runCatching {
            processRunner.run(
                command = listOf("git", "status", "--short", "--untracked-files=all"),
                directory = repoRoot,
                timeoutMillis = 15_000L,
                maxOutputBytes = MAX_GIT_STATUS_BYTES,
                maxOutputLines = 4_000
            )
        }.getOrNull() ?: return GitStatusSnapshot(null, "git status unavailable")
        if (result.timedOut) return GitStatusSnapshot(null, "git status timed out")
        if (result.launchError != null) return GitStatusSnapshot(null, "git status unavailable")
        if (result.outputTruncated) {
            return GitStatusSnapshot(null, "git status output exceeded $MAX_GIT_STATUS_BYTES bytes")
        }
        val output = result.stdout + result.stderr
        return if (result.exitCode == 0) {
            GitStatusSnapshot(output.trim(), null)
        } else {
            GitStatusSnapshot(null, "git status exited with code ${result.exitCode}")
        }
    }

    private fun extractChangedFiles(diff: String): List<String> {
        return diff.lineSequence().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank()) return@mapNotNull null
            if (trimmed.length >= 3 &&
                trimmed[0] in " MADRCUT?!" &&
                trimmed[1] in " MADRCUT?!"
            ) {
                val statusPath = trimmed.substring(2).trim()
                statusPath.substringAfterLast(" -> ", statusPath).takeIf { it.isNotBlank() }
            } else {
                trimmed.substringBefore("|").trim().substringBefore(" ")
            }
        }.filter { it.isNotBlank() }.toList()
    }

    private fun diffHash(diff: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(diff.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun readPriorHash(): String? {
        val path = repoRoot.resolve(".atropos/director/diff-snapshot.txt")
        if (!Files.isRegularFile(path)) return null
        return try {
            Files.readAllLines(path, StandardCharsets.UTF_8).firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
    }

    private fun saveSnapshot(hash: String, files: List<String>) {
        val path = repoRoot.resolve(".atropos/director/diff-snapshot.txt")
        Files.createDirectories(path.parent)
        val content = buildString {
            appendLine(hash)
            appendLine(files.joinToString("|"))
            appendLine(java.time.Instant.now().toString())
        }
        Files.writeString(path, content, StandardCharsets.UTF_8)
    }
}
