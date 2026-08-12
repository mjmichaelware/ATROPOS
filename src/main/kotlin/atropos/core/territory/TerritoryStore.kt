package atropos.core.territory

import atropos.core.AtroposRepoRootLocator
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class TerritoryStore(private val root: Path = AtroposRepoRootLocator.resolve()) {
    private val assignmentsPath = root.resolve(".atropos/territory/assignments.jsonl")
    private val violationsPath = root.resolve(".atropos/territory/violations.jsonl")

    fun saveAssignment(assignment: TerritoryAssignment) {
        Files.createDirectories(assignmentsPath.parent)
        val existing = loadAssignments().toMutableList()
        val idx = existing.indexOfFirst { it.id == assignment.id }
        if (idx >= 0) existing[idx] = assignment else existing += assignment
        writeLines(assignmentsPath, existing.map { it.toStoreLine() })
    }

    fun loadAssignments(): List<TerritoryAssignment> {
        return readLines(assignmentsPath).mapNotNull { parseAssignmentLine(it) }
    }

    fun removeAssignment(id: String) {
        val remaining = loadAssignments().filter { it.id != id }
        writeLines(assignmentsPath, remaining.map { it.toStoreLine() })
    }

    fun recordViolation(violation: TerritoryViolation) {
        Files.createDirectories(violationsPath.parent)
        appendLine(violationsPath, violation.toStoreLine())
    }

    fun loadViolations(): List<TerritoryViolation> {
        return readLines(violationsPath).mapNotNull { parseViolationLine(it) }
    }

    fun resolveViolation(id: String) {
        val all = loadViolations().toMutableList()
        val idx = all.indexOfFirst { it.id == id }
        if (idx >= 0) {
            all[idx] = all[idx].copy(resolved = true)
            writeLines(violationsPath, all.map { it.toStoreLine() })
        }
    }

    private fun readLines(path: Path): List<String> {
        if (!Files.isRegularFile(path)) return emptyList()
        return Files.readAllLines(path, StandardCharsets.UTF_8).map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("#") }
    }

    private fun writeLines(path: Path, lines: List<String>) {
        Files.createDirectories(path.parent)
        val tmp = path.resolveSibling("${path.fileName}.${System.nanoTime()}.tmp")
        Files.writeString(tmp, lines.joinToString("\n") + "\n", StandardCharsets.UTF_8)
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun appendLine(path: Path, line: String) {
        Files.createDirectories(path.parent)
        val existing = if (Files.isRegularFile(path)) Files.readString(path, StandardCharsets.UTF_8) else ""
        val tmp = path.resolveSibling("${path.fileName}.${System.nanoTime()}.tmp")
        Files.writeString(tmp, existing + line + "\n", StandardCharsets.UTF_8)
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

internal fun TerritoryAssignment.toStoreLine(): String {
    val pats = allowedFilePatterns.joinToString(",")
    val den = deniedPatterns.joinToString(",")
    return listOf(id, ownerId, ownerRole, allowedPrefix, pats, den, grantedAt.toString(), expiresAt?.toString().orEmpty(), parentTerritoryId.orEmpty(), maxFileSizeBytes.toString(), readOnly.toString(), boundActorIdentity.orEmpty()).joinToString("\t")
}

fun parseAssignmentLine(line: String): TerritoryAssignment? {
    val parts = line.split("\t")
    if (parts.size < 11) return null
    return try {
        TerritoryAssignment(
            id = parts[0], ownerId = parts[1], ownerRole = parts[2], allowedPrefix = parts[3],
            allowedFilePatterns = parts[4].split(",").filter { it.isNotBlank() },
            deniedPatterns = parts[5].split(",").filter { it.isNotBlank() },
            grantedAt = java.time.Instant.parse(parts[6]),
            expiresAt = parts[7].takeIf { it.isNotBlank() }?.let { java.time.Instant.parse(it) },
            parentTerritoryId = parts[8].takeIf { it.isNotBlank() },
            maxFileSizeBytes = parts[9].toLong(),
            readOnly = parts[10].toBoolean(),
            // Absent in lines written before grant-on-dispatch; those are
            // durable operator grants, which are bound to no work item.
            boundActorIdentity = parts.getOrNull(11)?.takeIf { it.isNotBlank() }
        )
    } catch (_: Exception) { null }
}

internal fun TerritoryViolation.toStoreLine(): String {
    return listOf(id, assignmentId, ownerId, filePath, reason.replace('\t', ' '), timestamp.toString(), resolved.toString()).joinToString("\t")
}

internal fun parseViolationLine(line: String): TerritoryViolation? {
    val parts = line.split("\t")
    if (parts.size < 7) return null
    return try {
        TerritoryViolation(id = parts[0], assignmentId = parts[1], ownerId = parts[2], filePath = parts[3], reason = parts[4], timestamp = java.time.Instant.parse(parts[5]), resolved = parts[6].toBoolean())
    } catch (_: Exception) { null }
}
