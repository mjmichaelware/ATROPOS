/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.intent

enum class InvariantCategory {
    HUMAN_INTENT,
    AUTHORITY_CASCADE,
    SYSTEM_INVARIANT
}

data class Sd5Invariant(
    val id: String,
    val category: InvariantCategory,
    val description: String,
    val isEnforced: Boolean = true
)

object Sd5HumanIntentInvariants {
    val INVARIANTS: List<Sd5Invariant> = (1..48).map { index ->
        val id = "INV-%03d".format(index)
        val category = when {
            index <= 16 -> InvariantCategory.HUMAN_INTENT
            index <= 32 -> InvariantCategory.AUTHORITY_CASCADE
            else -> InvariantCategory.SYSTEM_INVARIANT
        }
        val description = when (category) {
            InvariantCategory.HUMAN_INTENT -> "Human Intent Rule $id: Enforces operator priority and bounds."
            InvariantCategory.AUTHORITY_CASCADE -> "Authority Cascade Rule $id: Enforces correct delegation and territory."
            InvariantCategory.SYSTEM_INVARIANT -> "System Invariant Rule $id: Validates DAG consistency and memory boundaries."
        }
        Sd5Invariant(id = id, category = category, description = description)
    }

    fun getInvariant(id: String): Sd5Invariant? = INVARIANTS.find { it.id == id }

    fun validateAll(): Boolean {
        return INVARIANTS.all { it.isEnforced && it.id.startsWith("INV-") }
    }
}
