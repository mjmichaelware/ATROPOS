/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.commands

import atropos.core.hierarchy.AgentRecord
import atropos.core.hierarchy.HierarchyRegistry
import atropos.core.hierarchy.HierarchyRole

/**
 * `/hierarchy` — Phase 16 agent registration and escalation paths.
 *
 * Named for the registry rather than for the command so it is not mistaken for
 * the dispatcher in [HierarchyCommand], which routes all twelve subsystems and
 * owns none of them.
 */
class HierarchyRegistryCommandHandler(
    private val hierarchy: HierarchyRegistry = HierarchyRegistry()
) {
    fun handle(args: List<String>): String = when (args.firstOrNull()) {
        "register" -> register(args)
        "snapshot" -> snapshot()
        "escalate" -> escalate(args)
        else -> "${hierarchy.snapshot().agents.size} agents registered"
    }

    private fun register(args: List<String>): String {
        if (args.size < 3) return "usage: /hierarchy register <name> <role>"
        val role = runCatching { HierarchyRole.valueOf(args[2].uppercase()) }.getOrNull()
            ?: return "unknown role: ${args[2]}; valid: ${HierarchyRole.entries.joinToString(", ")}"
        val agent = AgentRecord(name = args[1], role = role)
        hierarchy.register(agent)
        return "agent registered: ${agent.id} name=${agent.name} role=${agent.role}"
    }

    private fun snapshot(): String {
        val snapshot = hierarchy.snapshot()
        if (snapshot.agents.isEmpty()) return "no agents registered"
        return snapshot.agents.joinToString("\n") {
            "  ${it.id}: ${it.name} (${it.role}) status=${it.status}"
        }
    }

    /**
     * Renders the chain of parents above an agent.
     *
     * Ids are resolved to names where possible but fall back to the raw id — a
     * path that silently dropped an unresolvable link would misrepresent the
     * chain of authority as shorter than it is.
     */
    private fun escalate(args: List<String>): String {
        if (args.size < 2) return "usage: /hierarchy escalate <agent-id>"
        val path = hierarchy.escalationPath(args[1])
        if (path.isEmpty()) return "agent not found: ${args[1]}"
        return path.joinToString(" -> ") { id -> hierarchy.get(id)?.name ?: id }
    }
}
