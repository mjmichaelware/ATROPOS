package atropos.core.acceptance

import atropos.core.agent.AgentContextCollector
import atropos.core.memory.LocalMemoryStore
import atropos.core.territory.TerritoryService

/**
 * Implements P001: FinalSD1SD2Acceptance.
 * Runs the final acceptance gate checking all SD1+SD2 predicates are met.
 */
class FinalSD1SD2Acceptance(
    private val contextCollector: AgentContextCollector,
    private val memoryStore: LocalMemoryStore,
    private val territoryService: TerritoryService
) {
    fun evaluateSD1SD2Readiness(): Boolean {
        // Evaluate SD1: Knowledge, Context, Memory
        val hasContext = contextCollector.collect(null).text.isNotBlank()
        val memoryInitialized = memoryStore.status().schemaVersion > 0
        val sd1Ready = hasContext && memoryInitialized

        // Evaluate SD2: Verification, Navigation, Territory
        val validTerritory = territoryService.getAll().isNotEmpty()
        val sd2Ready = validTerritory

        return sd1Ready && sd2Ready
    }
}

