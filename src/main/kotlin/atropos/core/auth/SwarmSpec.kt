/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.auth

/**
 * The declared shape of a multi-agent run, before any agent exists.
 *
 * `SUP.AUTH.SWARM-MD`: "Coordination topology becomes a verified input rather
 * than emergent; P(unbounded nesting)=0 by structural enforcement at load.
 * Competitors allow runtime spawn without attested topology."
 *
 * The distinction that matters is *when*. A depth limit checked at spawn time
 * is checked by the code doing the spawning, which is the code that wanted to
 * spawn. A depth limit read from an attested document at load time is a bound
 * the spawner was handed and cannot argue with.
 *
 * @param maxDepth how deep delegation may nest. Zero means no sub-agents at
 *   all, which is a legitimate and useful declaration.
 * @param coordinationCostBound the ceiling on monitoring cost the operator will
 *   accept, in the units [atropos.core.territory.TerritoryMonitorCost] reports.
 *   Present so the hierarchical-versus-flat claim of
 *   `SUP.VERIF.TERRITORY-MONITOR-COST` is something the run is held to rather
 *   than something asserted about it.
 */
data class SwarmSpec(
    val nodes: List<SwarmNode>,
    val maxDepth: Int,
    val escalationPath: List<String>,
    val coordinationCostBound: Long?
) {
    /**
     * Why this spec cannot be used, or empty when it can.
     *
     * Returned rather than thrown: `SUP.AUTH.SWARM-MD` requires "explicit
     * refusal with recovery hint", and a refusal that names one problem when
     * there are four makes the operator fix them one boot at a time.
     */
    fun defects(): List<String> = buildList {
        if (maxDepth < 0) add("maxDepth must not be negative")
        if (nodes.isEmpty()) add("no nodes declared")
        val duplicates = nodes.groupBy { it.name }.filterValues { it.size > 1 }.keys
        if (duplicates.isNotEmpty()) add("duplicate node names: ${duplicates.sorted().joinToString(", ")}")
        nodes.filter { it.territoryGrants.isEmpty() }.forEach {
            // A node with no declared territory is the unbounded sub-agent this
            // whole atom exists to prevent. Refusing here means the spawn path
            // never has to decide what "no territory" should mean.
            add("node '${it.name}' declares no territory grant")
        }
    }

    val usable: Boolean get() = defects().isEmpty()
}

/**
 * One declared participant.
 *
 * @param territoryGrants the paths this node may be given. A grant issued at
 *   dispatch must be within one of these, so a node cannot be handed territory
 *   the document never anticipated.
 */
data class SwarmNode(
    val name: String,
    val role: String,
    val territoryGrants: List<String>
)
