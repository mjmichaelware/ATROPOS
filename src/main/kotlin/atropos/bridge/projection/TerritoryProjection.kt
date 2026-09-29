/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.projection

import atropos.bridge.http.JsonWriter
import atropos.core.territory.TerritoryService
import atropos.core.territory.TerritoryAssignment
import atropos.core.security.RedactionFilter

/**
 * Projects territory membership onto the wire.
 *
 * `ADD-W-006` requires territory + attestation optical focus: out-of-territory
 * desaturates; valid attestation sharpens; drift softens.
 *
 * This projection provides territory membership checks for the UI.
 */
class TerritoryProjection(
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {

    /**
     * Checks if a path is within the territory of any assignment.
     */
    fun checkMembership(service: TerritoryService, path: String): String {
        val normalized = path.replace('\\', '/').trim().trimStart('/')
        if (normalized.isBlank()) {
            return JsonWriter.obj(
                "ok" to JsonWriter.bool(false),
                "error" to JsonWriter.str("Path cannot be blank")
            )
        }

        val assignments = service.getAll()
        val results = assignments.map { assignment ->
            val allowed = assignment.allows(path)
            JsonWriter.obj(
                "assignmentId" to JsonWriter.str(assignment.id),
                "ownerId" to JsonWriter.str(assignment.ownerId),
                "ownerRole" to JsonWriter.str(assignment.ownerRole),
                "allowedPrefix" to JsonWriter.str(assignment.allowedPrefix),
                "allowed" to JsonWriter.bool(allowed),
                "expired" to JsonWriter.bool(assignment.expiresAt?.let { !java.time.Instant.now().isBefore(it) } ?: false)
            )
        }

        val anyAllowed = results.any { it.getBoolean("allowed") }
        return JsonWriter.obj(
            "ok" to JsonWriter.bool(true),
            "path" to JsonWriter.str(path),
            "anyAllowed" to JsonWriter.bool(anyAllowed),
            "assignments" to JsonWriter.arr(results)
        )
    }

    /**
     * Renders all territory assignments.
     */
    fun renderAssignments(service: TerritoryService): String {
        val assignments = service.getAll()
        return JsonWriter.obj(
            "ok" to JsonWriter.bool(true),
            "assignments" to JsonWriter.arr(assignments.map { assignment ->
                JsonWriter.obj(
                    "id" to JsonWriter.str(assignment.id),
                    "ownerId" to JsonWriter.str(redactionFilter.redact(assignment.ownerId)),
                    "ownerRole" to JsonWriter.str(assignment.ownerRole),
                    "allowedPrefix" to JsonWriter.str(assignment.allowedPrefix),
                    "allowedFilePatterns" to JsonWriter.arr(assignment.allowedFilePatterns.map { JsonWriter.str(it) }),
                    "deniedPatterns" to JsonWriter.arr(assignment.deniedPatterns.map { JsonWriter.str(it) }),
                    "grantedAt" to JsonWriter.str(assignment.grantedAt.toString()),
                    "expiresAt" to JsonWriter.nullable(assignment.expiresAt?.let { JsonWriter.str(it.toString()) }),
                    "parentTerritoryId" to JsonWriter.nullable(assignment.parentTerritoryId?.let { JsonWriter.str(it) }),
                    "maxFileSizeBytes" to JsonWriter.num(assignment.maxFileSizeBytes),
                    "readOnly" to JsonWriter.bool(assignment.readOnly),
                    "boundActorIdentity" to JsonWriter.nullable(assignment.boundActorIdentity?.let { JsonWriter.str(it) }),
                    "expired" to JsonWriter.bool(assignment.expiresAt?.let { !java.time.Instant.now().isBefore(it) } ?: false)
                )
            })
        )
    }

    /**
     * Renders territory violations.
     */
    fun renderViolations(service: TerritoryService): String {
        val violations = service.getViolations()
        return JsonWriter.obj(
            "ok" to JsonWriter.bool(true),
            "violations" to JsonWriter.arr(violations.map { v ->
                JsonWriter.obj(
                    "id" to JsonWriter.str(v.id),
                    "assignmentId" to JsonWriter.str(v.assignmentId),
                    "ownerId" to JsonWriter.str(redactionFilter.redact(v.ownerId)),
                    "filePath" to JsonWriter.str(redactionFilter.redact(v.filePath)),
                    "reason" to JsonWriter.str(redactionFilter.redact(v.reason)),
                    "timestamp" to JsonWriter.str(v.timestamp.toString()),
                    "resolved" to JsonWriter.bool(v.resolved)
                )
            })
        )
    }
}