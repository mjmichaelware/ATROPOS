package atropos.core.territory

import java.nio.file.Path

class TerritoryService(
    private val store: TerritoryStore = TerritoryStore(),
    private val director: atropos.core.director.DirectorService? = null
) {
    fun assign(ownerId: String, ownerRole: String, allowedPrefix: String, parentId: String? = null, expiresInMinutes: Long? = null): TerritoryAssignment {
        val assignment = TerritoryAssignment(
            ownerId = ownerId,
            ownerRole = ownerRole,
            allowedPrefix = allowedPrefix,
            parentTerritoryId = parentId,
            expiresAt = expiresInMinutes?.let { java.time.Instant.now().plus(java.time.Duration.ofMinutes(it)) }
        )
        store.saveAssignment(assignment)
        return assignment
    }

    fun revoke(id: String) {
        store.removeAssignment(id)
    }

    fun getAll(): List<TerritoryAssignment> = store.loadAssignments()

    fun getForOwner(ownerId: String): List<TerritoryAssignment> = store.loadAssignments().filter { it.ownerId == ownerId }

    fun allows(id: String, path: String): Boolean {
        val assignment = store.loadAssignments().firstOrNull { it.id == id } ?: return false
        return assignment.allows(path)
    }

    fun checkViolation(assignmentId: String, filePath: String, reason: String): TerritoryViolation {
        val violation = TerritoryViolation(assignmentId = assignmentId, ownerId = "", filePath = filePath, reason = reason)
        store.recordViolation(violation)
        director?.observe(atropos.core.director.ObservationKind.TERRITORY_VIOLATION, atropos.core.director.DriftSeverity.WARNING, "territory/enforcement", reason, files = listOf(filePath))
        return violation
    }

    fun getViolations(): List<TerritoryViolation> = store.loadViolations()

    fun resolveViolation(id: String) = store.resolveViolation(id)
}
