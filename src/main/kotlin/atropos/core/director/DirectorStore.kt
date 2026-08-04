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
    private val storePath = root.resolve(".atropos/director/observations.jsonl")
    private val activeObservations = mutableMapOf<String, DirectorObservation>()

    fun appendObservation(obs: DirectorObservation) {
        Files.createDirectories(storePath.parent)
        val tmp = storePath.resolveSibling("observations.${System.nanoTime()}.tmp")
        val existing = if (Files.isRegularFile(storePath)) Files.readString(storePath, StandardCharsets.UTF_8) else ""
        Files.writeString(tmp, existing + obs.redactedForPersistence().toJsonLine() + "\n", StandardCharsets.UTF_8)
        activeObservations[obs.id] = obs
        try {
            Files.move(tmp, storePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp, storePath, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun readAll(): List<DirectorObservation> {
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
            territoryId.orEmpty()
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
                dismissed = parts[9].toBoolean()
            )
        } catch (_: Exception) { null }
    }
}
