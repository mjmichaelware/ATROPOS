/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.territory

import atropos.core.auth.SwarmSpec
import atropos.core.policy.ActionActor
import java.time.Instant

/**
 * The only way a sub-agent comes into existence.
 *
 * `SUP.TERR.SUBAGENT-SPAWN`: "P(unbounded sub-agent)=0 by construction; every
 * child is territory-bounded at birth. Competitors allow spawn without explicit
 * path/resource cut-set."
 *
 * "By construction" is doing real work in that sentence. A spawn API that
 * *checks* for a grant can be called by code that skips the check; a spawn API
 * that cannot produce a child without one leaves nothing to skip. [spawn]
 * returns a [SpawnResult], and the only path to [SpawnedAgent] runs through a
 * successful narrowing in [TerritoryGrantService] — there is no constructor
 * here that takes a child and no grant.
 *
 * Three bounds apply, and all three are read from somewhere the spawner does
 * not control:
 *
 * - **Territory** is narrowed from what the *parent* holds, so a node cannot
 *   ask for more than its dispatcher had. That is [TerritoryGrantService]'s
 *   existing rule and is not restated here.
 * - **Depth** comes from the attested [SwarmSpec]. A depth limit the spawning
 *   code carried would be a limit set by the thing that wanted to spawn.
 * - **Declared membership**: a node the topology never named cannot be created,
 *   so a run cannot grow participants that no document anticipated.
 */
class SubagentSpawnService(
    private val grants: TerritoryGrantService,
    /** Null when the repository declares no topology; then nothing may spawn. */
    private val swarm: SwarmSpec?,
    /** Every spawn is recorded, permitted or not. */
    private val log: SpawnLog = SpawnLog()
) {
    /**
     * @param parent who is delegating. Must hold territory to give.
     * @param depth how deep the child sits. The root's children are depth 1.
     */
    fun spawn(
        parent: ActionActor,
        childName: String,
        childRole: String,
        requestedPrefixes: List<String>,
        depth: Int
    ): SpawnResult {
        val refusal = refuse(childName, requestedPrefixes, depth)
        if (refusal != null) {
            log.record(SpawnAttempt(parent.identity, childName, depth, false, refusal, Instant.now()))
            return SpawnResult.Refused(refusal)
        }

        val node = ActionActor.HierarchyNode(role = childRole, nodeId = childName)
        return when (val granted = grants.grantToNode(parent, node, requestedPrefixes)) {
            is GrantResult.Refused -> {
                log.record(
                    SpawnAttempt(parent.identity, childName, depth, false, granted.reason, Instant.now())
                )
                SpawnResult.Refused(granted.reason)
            }

            is GrantResult.Granted -> {
                log.record(
                    SpawnAttempt(parent.identity, childName, depth, true, "granted", Instant.now())
                )
                SpawnResult.Spawned(
                    SpawnedAgent(
                        actor = node,
                        parentIdentity = parent.identity,
                        depth = depth,
                        // The child receives exactly what was issued. Nothing
                        // re-derives its bounds later from live config, so a
                        // grant revoked or widened after the fact cannot
                        // retroactively change what this child was allowed.
                        territory = granted.assignments,
                        spawnedAt = Instant.now()
                    )
                )
            }
        }
    }

    /** Why this spawn cannot happen, or null when it can. */
    private fun refuse(childName: String, requestedPrefixes: List<String>, depth: Int): String? {
        if (childName.isBlank()) return "a spawned agent must be named"
        if (requestedPrefixes.none { it.isNotBlank() }) {
            return "'$childName' declared no territory; a sub-agent with no bounds is refused"
        }
        if (depth < 1) return "spawn depth must be at least 1"

        val spec = swarm
            ?: return "no attested Swarm.md declares a topology, so nothing may be spawned"

        if (depth > spec.maxDepth) {
            return "depth $depth exceeds the declared maxDepth of ${spec.maxDepth}"
        }

        val declared = spec.nodes.firstOrNull { it.name == childName }
            ?: return "'$childName' is not declared in the attested topology"

        val undeclared = requestedPrefixes.filter { requested ->
            declared.territoryGrants.none { territoryPathWithin(requested, it) }
        }
        if (undeclared.isNotEmpty()) {
            return "'$childName' requested ${undeclared.joinToString(", ")}, " +
                "which the topology does not grant it"
        }
        return null
    }

    /** Every spawn attempt, for the auditor and the status matrix. */
    fun attempts(): List<SpawnAttempt> = log.all()
}

/**
 * A child that exists, with the bounds it was born with.
 *
 * There is no variant of this for a child without territory, which is the
 * structural half of `P(unbounded sub-agent)=0`.
 */
data class SpawnedAgent(
    val actor: ActionActor.HierarchyNode,
    val parentIdentity: String,
    val depth: Int,
    val territory: List<TerritoryAssignment>,
    val spawnedAt: Instant
) {
    /** The serialised grant for an evidence bundle or audit record. */
    fun territoryClaim(): String =
        territory.joinToString(",") { "${it.id}:${it.allowedPrefix}" }
}

sealed class SpawnResult {
    data class Spawned(val agent: SpawnedAgent) : SpawnResult()
    data class Refused(val reason: String) : SpawnResult()

    val permitted: Boolean get() = this is Spawned
}

data class SpawnAttempt(
    val parentIdentity: String,
    val childName: String,
    val depth: Int,
    val permitted: Boolean,
    val reason: String,
    val at: Instant
) {
    fun render(): String =
        "${if (permitted) "spawned" else "refused"} $childName depth=$depth " +
            "parent=$parentIdentity — $reason"
}

/**
 * The in-memory record of spawn attempts.
 *
 * Refusals are recorded as well as successes. A refused spawn is the more
 * interesting event: it means something tried to exceed its bounds, and a log
 * that kept only the permitted ones would show a clean history of exactly the
 * runs where that happened.
 */
class SpawnLog(private val bound: Int = 500) {
    private val entries = ArrayDeque<SpawnAttempt>()

    @Synchronized
    fun record(attempt: SpawnAttempt) {
        entries.addLast(attempt)
        while (entries.size > bound) entries.removeFirst()
    }

    @Synchronized
    fun all(): List<SpawnAttempt> = entries.toList()
}
