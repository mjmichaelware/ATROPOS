/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.territory

import atropos.core.director.DirectorService
import atropos.core.policy.ActionActor

/** Outcome of asking for a territory grant. */
sealed interface GrantResult {
    data class Granted(val assignments: List<TerritoryAssignment>) : GrantResult
    data class Refused(val reason: String) : GrantResult
}

/**
 * Issues territory at dispatch.
 *
 * Territory is delegated downward, never claimed: a dispatcher may only hand a
 * node a subset of what the dispatcher itself holds. That is what stops a node
 * from declaring its own bounds — [grantToNode] reads the *dispatcher's*
 * holdings, so a caller with nothing to give cannot give anything.
 *
 * This owns granting only. Storage, prefix matching and violation recording stay
 * with [TerritoryService] and [TerritoryStore]; nothing here duplicates them.
 */
class TerritoryGrantService(
    private val service: TerritoryService = TerritoryService(director = DirectorService()),
    /**
     * The scope the human owner holds. Empty string means the whole repository:
     * [TerritoryAssignment.allows] tests `path.startsWith(prefix)`, and every
     * relative path starts with `""`.
     */
    private val rootPrefix: String = ""
) {
    /**
     * The durable grant the human owner holds over the repository.
     *
     * Created on first use and persisted, so it is visible to
     * `/hierarchy territory list` and can be revoked like any other. It is not
     * a bypass: it is the root of the delegation chain, and every node grant
     * must still be narrowed from it and bound to that node.
     */
    fun rootGrant(): TerritoryAssignment {
        val identity = ActionActor.HumanOwner.identity
        service.getForOwner(identity).firstOrNull { it.boundActorIdentity == null }?.let { return it }
        return service.assign(
            ownerId = identity,
            ownerRole = "owner",
            allowedPrefix = rootPrefix
        )
    }

    /** The grant an actor currently holds, or `null` if it holds none. */
    fun grantsFor(actor: ActionActor): List<TerritoryAssignment> = when (actor) {
        is ActionActor.HumanOwner -> listOf(rootGrant())
        else -> service.getForOwner(actor.identity).filter { it.isLive() }
    }

    /**
     * Narrows the dispatcher's territory onto a dispatched node.
     *
     * @param dispatcher who is delegating. Must already hold a grant.
     * @param node the work item the grant is bound to.
     * @param requestedPrefixes the paths the node says it needs. Each must lie
     *   within something the dispatcher holds.
     */
    fun grantToNode(
        dispatcher: ActionActor,
        node: ActionActor.HierarchyNode,
        requestedPrefixes: List<String>
    ): GrantResult {
        val clean = requestedPrefixes.map { it.trim() }.filter { it.isNotBlank() }
        if (clean.isEmpty()) {
            return GrantResult.Refused("node ${node.identity} declared no territory to be granted")
        }

        val held = grantsFor(dispatcher)
        if (held.isEmpty()) {
            return GrantResult.Refused(
                "dispatcher ${dispatcher.identity} holds no territory and cannot grant any"
            )
        }

        val issued = mutableListOf<TerritoryAssignment>()
        for (prefix in clean) {
            val parent = held.firstOrNull { it.contains(prefix) }
                ?: return GrantResult.Refused(
                    "requested territory '$prefix' is outside the territory ${dispatcher.identity} holds"
                )

            issued += service.assignChild(
                ownerId = node.identity,
                ownerRole = node.role,
                allowedPrefix = prefix,
                parent = parent,
                boundActorIdentity = node.identity
            )
        }
        return GrantResult.Granted(issued)
    }

    /**
     * Whether [actor] may touch every one of [paths].
     *
     * @return `null` when permitted, otherwise the path that was refused.
     */
    fun firstPathOutsideTerritory(actor: ActionActor, paths: List<String>): String? {
        val held = grantsFor(actor)
        if (held.isEmpty()) return paths.firstOrNull()
        return paths.firstOrNull { path -> held.none { it.allows(path) } }
    }

    /** Records a refusal so it reaches the violation log and the Director. */
    fun recordViolation(actor: ActionActor, path: String, reason: String) {
        val assignmentId = grantsFor(actor).firstOrNull()?.id.orEmpty()
        service.checkViolation(assignmentId, path, reason)
    }
}

/** A grant is live when it has not expired. */
internal fun TerritoryAssignment.isLive(): Boolean =
    expiresAt == null || java.time.Instant.now().isBefore(expiresAt)

/**
 * Whether this grant fully contains [prefix] — the narrowing test.
 *
 * A child may be equal to or deeper than its parent, never broader.
 */
internal fun TerritoryAssignment.contains(prefix: String): Boolean =
    isLive() && prefix.startsWith(allowedPrefix) && deniedPatterns.none { prefix.contains(it) }
