package atropos.core.territory

import java.time.Instant
import java.util.UUID

data class TerritoryAssignment(
    val id: String = "terr-${UUID.randomUUID().toString().take(12)}",
    val ownerId: String,
    val ownerRole: String,
    val allowedPrefix: String,
    val allowedFilePatterns: List<String> = listOf("**/*.kt", "**/*.md"),
    val deniedPatterns: List<String> = emptyList(),
    val grantedAt: Instant = Instant.now(),
    val expiresAt: Instant? = null,
    val parentTerritoryId: String? = null,
    val maxFileSizeBytes: Long = 1024 * 1024,
    val readOnly: Boolean = false
) {
    fun allows(path: String): Boolean {
        if (!path.startsWith(allowedPrefix)) return false
        if (deniedPatterns.any { path.contains(it) }) return false
        if (expiresAt != null && Instant.now().isAfter(expiresAt)) return false
        return true
    }
}

data class TerritoryViolation(
    val id: String = "viol-${UUID.randomUUID().toString().take(12)}",
    val assignmentId: String,
    val ownerId: String,
    val filePath: String,
    val reason: String,
    val timestamp: Instant = Instant.now(),
    val resolved: Boolean = false
)

data class TerritorySnapshot(
    val assignments: List<TerritoryAssignment>,
    val violations: List<TerritoryViolation>,
    val timestamp: Instant = Instant.now()
)
