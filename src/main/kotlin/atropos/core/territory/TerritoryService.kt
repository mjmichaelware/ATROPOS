package atropos.core.territory

import atropos.core.director.DirectorService

class TerritoryService(
    private val store: TerritoryStore = TerritoryStore(),
    private val director: DirectorService? = null
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

    /**
     * Records a grant delegated from [parent] and bound to one work item.
     *
     * Kept here so all assignment persistence stays with the one owner;
     * [atropos.core.territory.TerritoryGrantService] decides *whether* to
     * delegate, this only writes the result.
     */
    fun assignChild(
        ownerId: String,
        ownerRole: String,
        allowedPrefix: String,
        parent: TerritoryAssignment,
        boundActorIdentity: String
    ): TerritoryAssignment {
        val assignment = TerritoryAssignment(
            ownerId = ownerId,
            ownerRole = ownerRole,
            allowedPrefix = allowedPrefix,
            // A child inherits its parent's denials: narrowing may not widen.
            deniedPatterns = parent.deniedPatterns,
            parentTerritoryId = parent.id,
            expiresAt = parent.expiresAt,
            readOnly = parent.readOnly,
            boundActorIdentity = boundActorIdentity
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
