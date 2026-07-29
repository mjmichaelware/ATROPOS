package atropos.core.planning

import atropos.core.AtroposRepoRootLocator
import atropos.core.dag.DagStore
import java.nio.file.Path

data class PlanningGraphPluginRegistration(
    val id: String,
    val plugin: PlanningGraphPlugin,
    val priority: Int = 0,
    val local: Boolean = false
)

data class PlanningGraphPluginRegistryReport(
    val selectedId: String,
    val registrations: List<PlanningGraphPluginRegistration>,
    val fallbackUsed: Boolean,
    val message: String
)

class PlanningGraphPluginRegistry(
    registrations: List<PlanningGraphPluginRegistration> = emptyList(),
    private val fallback: PlanningGraphPluginRegistration = internalFallback()
) {
    private val registered = registrations.sortedRegistrations()

    fun resolve(preferredId: String? = null): PlanningGraphPluginRegistration {
        preferredId?.let { preferred ->
            registered.firstOrNull { it.id == preferred }?.let { return it }
        }
        return registered.firstOrNull() ?: fallback
    }

    fun report(preferredId: String? = null): PlanningGraphPluginRegistryReport {
        val selected = resolve(preferredId)
        val fallbackUsed = selected.id == fallback.id && registered.none { it.id == selected.id }
        return PlanningGraphPluginRegistryReport(
            selectedId = selected.id,
            registrations = registered,
            fallbackUsed = fallbackUsed,
            message = if (fallbackUsed) {
                "planning graph registry using local internal fallback"
            } else {
                "planning graph registry selected ${selected.id}"
            }
        )
    }

    private fun List<PlanningGraphPluginRegistration>.sortedRegistrations(): List<PlanningGraphPluginRegistration> {
        val duplicates = groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        require(duplicates.isEmpty()) { "duplicate planning graph plugin ids: ${duplicates.joinToString(",")}" }
        return sortedWith(compareByDescending<PlanningGraphPluginRegistration> { it.priority }.thenBy { it.id })
    }

    companion object {
        fun internalFallback(
            repoRoot: Path = AtroposRepoRootLocator.resolve(),
            store: DagStore = DagStore(repoRoot)
        ): PlanningGraphPluginRegistration =
            PlanningGraphPluginRegistration(
                id = "internal-dag",
                plugin = InternalPlanningGraphPlugin(repoRoot, store),
                priority = Int.MIN_VALUE,
                local = true
            )
    }
}
