/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.policy

/**
 * Who is asking for a side effect.
 *
 * Territory resolves by actor, so "whose territory applies?" has to have an
 * answer before an action can be judged. There is deliberately no ambient or
 * default actor: [ActionProposal] requires one, which makes an unattributed
 * action unrepresentable rather than merely discouraged.
 *
 * [identity] is the stable string territory assignments and audit records key
 * on. Every implementation guarantees it is non-blank: this interface is sealed
 * and each variant rejects blank input at construction, so an unidentifiable
 * actor cannot be built at all. That is deliberately stronger than refusing one
 * later — there is no runtime branch to forget.
 */
sealed interface ActionActor {
    val kind: ActorKind
    val identity: String

    /**
     * A human operator acting directly — a typed shell command, or a command
     * they invoked whose work is performed on their behalf.
     *
     * The distinction that matters is authorship, not execution: when an
     * operator runs `/agent ask`, the provider performs the work but the
     * operator is the actor.
     */
    data object HumanOwner : ActionActor {
        override val kind: ActorKind = ActorKind.HUMAN_OWNER
        override val identity: String = "human-owner"
    }

    /**
     * A node in the hierarchy doing dispatched, model-authored work.
     *
     * Both fields are required to be non-blank: a node that cannot say which
     * node it is cannot have a territory, and silently accepting an empty id
     * would make territory lookup match nothing while looking like it worked.
     */
    data class HierarchyNode(val role: String, val nodeId: String) : ActionActor {
        init {
            require(role.isNotBlank()) { "hierarchy actor requires a role" }
            require(nodeId.isNotBlank()) { "hierarchy actor requires a node id" }
        }

        override val kind: ActorKind = ActorKind.HIERARCHY_NODE
        override val identity: String = "$role:$nodeId"
    }

    /** An internal lifecycle action — daemon control, queue transitions. */
    data class SystemService(val service: String) : ActionActor {
        init {
            require(service.isNotBlank()) { "system actor requires a service name" }
        }

        override val kind: ActorKind = ActorKind.SYSTEM_SERVICE
        override val identity: String = "system:$service"
    }
}

enum class ActorKind {
    HUMAN_OWNER,
    HIERARCHY_NODE,
    SYSTEM_SERVICE
}
