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
    val readOnly: Boolean = false,
    /**
     * The actor identity this grant authorises, when it was issued at dispatch.
     *
     * A child grant is bound to one dispatched work item, so it cannot be
     * reused by a different node. `null` for durable operator-assigned
     * territory, which is held by an owner rather than a work item.
     */
    val boundActorIdentity: String? = null
) {
    fun allows(path: String): Boolean {
        val normalizedPath = normalizeTerritoryPath(path) ?: return false
        if (!territoryPathWithin(normalizedPath, allowedPrefix)) return false
        if (deniedPatterns.any { normalizedPath.contains(it.replace('\\', '/')) }) return false
        if (expiresAt != null && !Instant.now().isBefore(expiresAt)) return false
        return true
    }
}

internal fun normalizeTerritoryPath(path: String): String? {
    val normalized = path.replace('\\', '/').trim().trimStart('/')
    if (normalized.isBlank()) return null
    val segments = normalized.split('/')
    if (segments.any { it.isBlank() || it == "." || it == ".." }) return null
    return segments.joinToString("/")
}

internal fun territoryPathWithin(path: String, prefix: String): Boolean {
    val normalizedPath = normalizeTerritoryPath(path) ?: return false
    val normalizedPrefix = prefix.replace('\\', '/').trim().trim('/').trimEnd('/')
    return normalizedPrefix.isBlank() ||
        normalizedPath == normalizedPrefix ||
        normalizedPath.startsWith("$normalizedPrefix/")
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
