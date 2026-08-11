package atropos.core.director

import atropos.core.AtroposRepoRootLocator
import atropos.core.security.RedactionFilter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class DirectorStore(
    private val root: Path = AtroposRepoRootLocator.resolve(),
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    private val normalizedRoot = root.toAbsolutePath().normalize()
    private val storePath = normalizedRoot.resolve(".atropos/director/observations.jsonl")
    private val activeObservations = mutableMapOf<String, DirectorObservation>()

    fun appendObservation(obs: DirectorObservation) {
        require(!hasSymlinkBoundary(storePath)) { "director observation store crosses a symbolic link" }
        Files.createDirectories(storePath.parent)
        val existing = if (Files.isRegularFile(storePath)) Files.readString(storePath, StandardCharsets.UTF_8) else ""
        writeStore(existing + obs.redactedForPersistence().toJsonLine() + "\n")
        activeObservations[obs.id] = obs
    }

    /** Rewrites acknowledgement state through the same redaction boundary as append. */
    fun replaceAll(observations: List<DirectorObservation>) {
        val content = observations.joinToString(separator = "") {
            it.redactedForPersistence().toJsonLine() + "\n"
        }
        writeStore(content)
        activeObservations.clear()
        observations.forEach { activeObservations[it.id] = it }
    }

    private fun writeStore(content: String) {
        require(!hasSymlinkBoundary(storePath)) { "director observation store crosses a symbolic link" }
        Files.createDirectories(storePath.parent)
        val tmp = storePath.resolveSibling("observations.${System.nanoTime()}.tmp")
        Files.writeString(tmp, content, StandardCharsets.UTF_8)
        try {
            Files.move(tmp, storePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp, storePath, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun readAll(): List<DirectorObservation> {
        if (hasSymlinkBoundary(storePath)) return emptyList()
        if (!Files.isRegularFile(storePath)) return emptyList()
        return Files.readAllLines(storePath, StandardCharsets.UTF_8).mapNotNull { line ->
            parseJsonLine(line.trim())
        }
    }

    fun unacknowledged(): List<DirectorObservation> = readAll()
        .filter { !it.acknowledged && !it.dismissed }
        .map { persisted -> activeObservations[persisted.id] ?: persisted }

    private fun DirectorObservation.redactedForPersistence(): DirectorObservation = copy(
        details = redactedDetail(details),
        worktreePath = worktreePath?.let { redactedReference("worktree", it) },
        sourceCoordinates = sourceCoordinates.map { redactedReference("source", it) },
        evidencePaths = evidencePaths.map { redactedReference("evidence", it) },
        filePaths = filePaths.map { redactedReference("path", it) },
        symbols = symbols.map { redactedReference("symbol", it) }
    )

    private fun redactedDetail(value: String): String {
        val sanitized = redactionFilter.redact(value)
        return "<redacted:details:${fingerprint(sanitized)}>"
    }

    private fun redactedReference(kind: String, value: String): String =
        "<redacted:$kind:${fingerprint(redactionFilter.redact(value))}>"

    private fun fingerprint(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun hasSymlinkBoundary(path: Path): Boolean {
        var cursor: Path? = path
        while (cursor != null && cursor != normalizedRoot) {
            if (Files.isSymbolicLink(cursor)) return true
            cursor = cursor.parent
        }
        return false
    }

    private fun DirectorObservation.toJsonLine(): String {
        val fp = filePaths.joinToString("|") { it.replace("|", "%7C") }
        val sym = symbols.joinToString("|") { it.replace("|", "%7C") }
        return listOf(
            id,
            kind.name,
            severity.name,
            source,
            details.replace('\n', ' '),
            fp,
            sym,
            timestamp.toString(),
            acknowledged.toString(),
            dismissed.toString(),
            goalId.orEmpty(),
            territoryId.orEmpty(),
            driftScore.toString(),
            claimId.orEmpty(),
            worktreePath.orEmpty(),
            sourceCoordinates.joinToString("|") { it.replace("|", "%7C") },
            evidencePaths.joinToString("|") { it.replace("|", "%7C") }
        ).joinToString("\t")
    }

    private fun parseJsonLine(line: String): DirectorObservation? {
        if (line.isBlank() || line.startsWith("#")) return null
        val parts = line.split("\t")
        if (parts.size < 10) return null
        return try {
            DirectorObservation(
                id = parts[0],
                kind = ObservationKind.valueOf(parts[1]),
                severity = DriftSeverity.valueOf(parts[2]),
                source = parts[3],
                details = parts[4],
                goalId = parts.getOrNull(10)?.takeIf { it.isNotBlank() },
                territoryId = parts.getOrNull(11)?.takeIf { it.isNotBlank() },
                filePaths = if (parts[5].isBlank()) emptyList() else parts[5].split("|").map { it.replace("%7C", "|") },
                symbols = if (parts[6].isBlank()) emptyList() else parts[6].split("|").map { it.replace("%7C", "|") },
                timestamp = java.time.Instant.parse(parts[7]),
                acknowledged = parts[8].toBoolean(),
                dismissed = parts[9].toBoolean(),
                driftScore = parts.getOrNull(12)?.toIntOrNull()
                    ?: DirectorDriftScorer.score(ObservationKind.valueOf(parts[1]), DriftSeverity.valueOf(parts[2])),
                claimId = parts.getOrNull(13)?.takeIf { it.isNotBlank() },
                worktreePath = parts.getOrNull(14)?.takeIf { it.isNotBlank() },
                sourceCoordinates = decodeList(parts.getOrNull(15)),
                evidencePaths = decodeList(parts.getOrNull(16))
            )
        } catch (_: Exception) { null }
    }

    private fun decodeList(value: String?): List<String> = value.orEmpty()
        .takeIf { it.isNotBlank() }
        ?.split("|")
        ?.map { it.replace("%7C", "|") }
        .orEmpty()
}
