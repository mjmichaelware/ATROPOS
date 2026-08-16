/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

data class ProjectStorageBudget(val projectId: String, val limitBytes: Long, val usedBytes: Long) {
    init { require(projectId.isNotBlank() && limitBytes >= 0 && usedBytes >= 0) }
    val remainingBytes: Long get() = (limitBytes - usedBytes).coerceAtLeast(0)
    fun admits(requestedBytes: Long): Boolean = requestedBytes >= 0 && requestedBytes <= remainingBytes
}

class ProjectStorageBudgetStore {
    private val budgets = linkedMapOf<String, ProjectStorageBudget>()

    fun put(budget: ProjectStorageBudget) { budgets[budget.projectId] = budget }

    fun find(projectId: String): ProjectStorageBudget? = budgets[projectId]

    fun snapshot(): List<ProjectStorageBudget> = budgets.values.toList()
}
