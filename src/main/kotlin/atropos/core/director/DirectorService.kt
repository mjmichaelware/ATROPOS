package atropos.core.director

import atropos.core.territory.TerritoryAssignment
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class DirectorService(
    private val store: DirectorStore = DirectorStore(),
    private val repoRoot: Path = Path.of(System.getProperty("user.dir"))
) {
    fun observe(kind: ObservationKind, severity: DriftSeverity, source: String, details: String, files: List<String> = emptyList(), symbols: List<String> = emptyList()): DirectorObservation {
        val obs = DirectorObservation(kind = kind, severity = severity, source = source, details = details, filePaths = files, symbols = symbols)
        store.appendObservation(obs)
        return obs
    }

    fun scanDiffForDrift(territories: List<TerritoryAssignment> = emptyList()): List<DirectorObservation> {
        val observations = mutableListOf<DirectorObservation>()
        val diff = runGitDiff()

        if (diff != null) {
            val changedFiles = extractChangedFiles(diff)
            if (changedFiles.isNotEmpty()) {
                observations += DirectorObservation(
                    kind = ObservationKind.DIFF_DRIFT,
                    severity = if (changedFiles.size > 5) DriftSeverity.WARNING else DriftSeverity.ADVISORY,
                    source = "director/diff-scan",
                    details = "${changedFiles.size} files changed in working tree",
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
                    details = "diff hash changed from ${priorHash.take(12)} to ${currentDiffHash.take(12)}"
                )
            }

            for (t in territories) {
                for (f in changedFiles) {
                    if (!f.startsWith(t.allowedPrefix)) {
                        observations += DirectorObservation(
                            kind = ObservationKind.TERRITORY_VIOLATION,
                            severity = DriftSeverity.WARNING,
                            source = "director/territory-enforcement",
                            details = "file $f outside territory ${t.allowedPrefix}",
                            filePaths = listOf(f)
                        )
                    }
                }
            }
        }

        observations.forEach { store.appendObservation(it) }
        val chFiles = if (diff != null) extractChangedFiles(diff) else emptyList()
        saveSnapshot(diffHash(diff ?: ""), chFiles)
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

    private fun rewriteAll(observations: List<DirectorObservation>) {
        val path = repoRoot.resolve(".atropos/director/observations.jsonl")
        Files.createDirectories(path.parent)
        val tmp = path.resolveSibling("observations.${System.nanoTime()}.tmp")
        val lines = observations.joinToString("\n") { it.toStoreLine() }
        Files.writeString(tmp, lines + "\n", StandardCharsets.UTF_8)
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun runGitDiff(): String? {
        return try {
            val process = ProcessBuilder("git", "diff", "--stat")
                .directory(repoRoot.toFile())
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.readAllBytes().toString(StandardCharsets.UTF_8).trim()
            process.waitFor()
            if (process.exitValue() == 0) output else null
        } catch (_: Exception) { null }
    }

    private fun extractChangedFiles(diff: String): List<String> {
        return diff.lineSequence().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank()) return@mapNotNull null
            trimmed.substringBefore("|").trim().substringBefore(" ")
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

internal fun DirectorObservation.toStoreLine(): String {
    val fp = filePaths.joinToString("|") { it.replace("|", "%7C") }
    val sym = symbols.joinToString("|") { it.replace("|", "%7C") }
    return listOf(id, kind.name, severity.name, source, details.replace('\n', ' '), fp, sym, timestamp.toString(), acknowledged.toString(), dismissed.toString()).joinToString("\t")
}
