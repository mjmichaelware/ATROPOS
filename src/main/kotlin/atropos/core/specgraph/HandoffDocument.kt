/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.specgraph

/**
 * A parsed `atropos_handoff.json`, schema `specgraph.atropos.handoff.v1`.
 *
 * The document SpecGraph writes *to* ATROPOS. Its `execution_contract` names
 * `specgraph-foundry` as the authority owner and `atropos` as the runtime
 * owner, which is the division this type exists to honour: the plan arrives
 * decided, and ATROPOS's job is to run it, not to re-derive it.
 *
 * Everything here is read-only. The handoff is an immutable statement about a
 * verified plan; a mutable model of it invites a caller to "fix up" a node and
 * execute something the authority side never approved.
 */
data class HandoffDocument(
    val schema: String,
    val producer: String,
    val project: HandoffProject,
    val plan: HandoffPlan,
    val execution: HandoffExecutionGraph,
    val requirements: List<HandoffRequirement>,
    val routingLaw: List<String>,
    val contract: HandoffExecutionContract
) {
    /**
     * Whether this handoff may be executed at all.
     *
     * Three independent conditions, all required. A plan that is not verified
     * has not passed `verify_plan`; a cyclic graph cannot be ordered; and a
     * graph with no ready node cannot be started. Each of the three produces a
     * different wrong behaviour if ignored, so they are reported separately
     * rather than collapsed into one boolean.
     */
    fun executability(): Executability = Executability(
        planVerified = plan.verified,
        acyclic = execution.acyclic(),
        hasReadyWork = execution.readyNodeIds.isNotEmpty(),
        contractHonoured = contract.runtimeOwner == "atropos"
    )

    companion object {
        const val SCHEMA = "specgraph.atropos.handoff.v1"
    }
}
