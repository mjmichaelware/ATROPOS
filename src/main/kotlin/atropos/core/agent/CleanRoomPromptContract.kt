/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.agent

import atropos.core.contract.ContractLattice
import atropos.core.dag.DagNode

object CleanRoomPromptContract {
    fun buildPrompt(node: DagNode, lattice: ContractLattice, h0: String): String {
        val interfacesBuffer = StringBuilder()
        for ((path, content) in lattice.interfaceFiles) {
            interfacesBuffer.append("// Contract: ").append(path).append("\n")
            interfacesBuffer.append(content.trim()).append("\n\n")
        }

        val territoryScope = node.territory.joinToString(", ")

        return """[SYSTEM ROLE]
Clean-room implementation agent.
Strict contextual disjointness active (§7).
Implement ONLY the requested contract and task. Do not cross-couple with sibling components.

[CRYPTOGRAPHIC ROOT]
H0 = $h0

[WAVE-0 IMMUTABLE INTERFACES]
${interfacesBuffer.toString().trim()}

[ASSIGNED TASK]
Node ID: ${node.id}
Label: ${node.label}
Territory Scope: $territoryScope
Task Description:
${node.actionPayload ?: "Implement component conforming to the Wave-0 contract interfaces."}

[CONSTRAINTS]
1. Conform strictly to the interface contracts.
2. Zero template strings, zero stubs, zero TODO comments.
3. Keep implementation scoped to the declared territory ($territoryScope).
"""
    }
}
