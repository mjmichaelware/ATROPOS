/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CommandTaxonomyTest {

    @Test
    fun catalog_contains_all_eight_major_categories() {
        val categories = CommandCategory.values()
        assertEquals(8, categories.size)
        assertTrue(CommandCatalogRegistry.allCommands.size >= 30)
    }

    @Test
    fun category_partitioning_enforces_non_overload_rule() {
        // Categories with <= 8 commands display all directly
        // Categories with > 8 commands provide structured subcategories
        val agentCommands = CommandCatalogRegistry.getByCategory(CommandCategory.AGENT_EXECUTION)
        assertTrue(agentCommands.size > 8, "Agent execution category should have > 8 commands")

        val subcategories = CommandCatalogRegistry.getSubcategories(CommandCategory.AGENT_EXECUTION)
        assertTrue(subcategories.isNotEmpty(), "Agent category must declare subcategories")
        assertTrue(subcategories.contains("Trajectory & Work"))
        assertTrue(subcategories.contains("Queue & Hierarchy"))

        val providersCommands = CommandCatalogRegistry.getByCategory(CommandCategory.PROVIDERS_MODELS)
        assertTrue(providersCommands.size <= 8, "Providers category has <= 8 commands and presents cleanly")
    }

    @Test
    fun command_search_matches_query_and_synopsis() {
        val results = CommandCatalogRegistry.search("heal")
        assertTrue(results.isNotEmpty())
        assertTrue(results.any { it.name == "/heal" })

        val providerResults = CommandCatalogRegistry.search("gemini")
        assertTrue(providerResults.isNotEmpty())

        val emptyResults = CommandCatalogRegistry.search("xyznonexistent999")
        assertTrue(emptyResults.isEmpty())
    }

    @Test
    fun safety_tiers_govern_dangerous_operations() {
        val goalCmd = CommandCatalogRegistry.find("/goal")
        assertNotNull(goalCmd)
        assertEquals(SafetyTier.LOCAL_MUTATION, goalCmd.safetyTier)

        val cancelCmd = CommandCatalogRegistry.find("/cancel")
        assertNotNull(cancelCmd)
        assertEquals(SafetyTier.CRITICAL_GOVERNANCE, cancelCmd.safetyTier)
        assertTrue(cancelCmd.safetyTier.requiresApproval)

        val doctorCmd = CommandCatalogRegistry.find("/doctor")
        assertNotNull(doctorCmd)
        assertEquals(SafetyTier.READ_ONLY, doctorCmd.safetyTier)
        assertFalse(doctorCmd.safetyTier.requiresApproval)
    }
}
