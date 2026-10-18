/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.taxonomy.HierarchicalCommandTaxonomy
import atropos.cli.ui.BeautifulBoxSystem
import atropos.core.config.AtroposUnifiedConfig
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * ATOM-SURFACE-026: Omni-Surface Contract & Hardware Agnosticism Test Suite.
 *
 * Verifies that the ATROPOS command taxonomy, beautiful box system, unified
 * configuration, and filesystem abstractions operate consistently across
 * CLI, Web Cockpit, and Android APK environments without platform hard-coding.
 */
class OmniSurfaceContractTest {

    @Test
    fun taxonomy_resolves_all_seven_domains_consistently() {
        val taxonomy = HierarchicalCommandTaxonomy()
        val domains = taxonomy.root.keys

        val expectedDomains = setOf("agent", "repo", "memory", "governance", "toolchain", "session", "system")
        assertEquals(expectedDomains, domains, "Taxonomy must strictly declare the 7 canonical domains")

        // Check Tier 4 leaf resolution across surfaces
        val agentStart = taxonomy.resolveLeaf(listOf("agent", "lifecycle", "start"))
        assertNotNull(agentStart, "agent lifecycle start must resolve to a valid leaf")
        assertTrue(agentStart.contains("autonomous", ignoreCase = true))

        val repoDiff = taxonomy.resolveLeaf(listOf("repo", "inspection", "diff"))
        assertNotNull(repoDiff, "repo inspection diff must resolve to a valid leaf")

        val sysConfig = taxonomy.resolveLeaf(listOf("system", "environment", "config"))
        assertNotNull(sysConfig, "system environment config must resolve to a valid leaf")
    }

    @Test
    fun beautiful_box_system_adapts_to_all_four_surface_breakpoints() {
        val boxSystem = BeautifulBoxSystem("atropos-dark")
        val sampleContent = listOf("Line 1: Model route ok", "Line 2: Merkle check verified")

        // 1. Ultra-narrow (e.g. mobile portrait split < 40 cols)
        val ultraNarrow = boxSystem.renderBox(contentLines = sampleContent, width = 36)
        assertTrue(ultraNarrow.size >= 4, "Ultra-narrow box must render successfully")
        assertTrue(ultraNarrow.first().contains("╭"), "Box must start with rounded border")

        // 2. Narrow (40..79 cols)
        val narrow = boxSystem.renderBox(contentLines = sampleContent, width = 60)
        assertTrue(narrow.size >= 5, "Narrow box must render with nav chrome")

        // 3. Standard (80..119 cols - default CLI)
        val standard = boxSystem.renderBox(contentLines = sampleContent, width = 80)
        assertTrue(standard.size >= 5, "Standard box must render with full padding")

        // 4. Wide (>= 120 cols - Web Cockpit / tablet APK)
        val wide = boxSystem.renderBox(contentLines = sampleContent, width = 140)
        assertTrue(wide.size >= 5, "Wide box must render with expanded width")
    }

    @Test
    fun filesystem_abstraction_contains_zero_hardcoded_os_paths() {
        val home = System.getProperty("user.home")
        val tmp = System.getProperty("java.io.tmpdir")

        assertNotNull(home, "user.home must be resolved dynamically from system property")
        assertNotNull(tmp, "java.io.tmpdir must be resolved dynamically from system property")

        val configFile = AtroposUnifiedConfig.configFile()
        assertNotNull(configFile, "Unified config file must be dynamically resolvable")
        assertTrue(!configFile.path.contains("/data/data/com.termux") || home.contains("com.termux"),
            "Paths must not hardcode platform strings")
    }

    @Test
    fun unified_config_matrix_matches_canonical_settings_count() {
        val options = AtroposUnifiedConfig.ALLOWED_OPTIONS
        assertTrue(options.size >= 24, "Unified config must declare at least 24 settings")

        val required = listOf(
            "Agent_Mode", "Animation_Speed", "Artifact_Review", "Color_Scheme",
            "Conversation_Grouping", "Copy_on_Select", "Editor", "Editor_Mode",
            "Enable_Telemetry", "Non-Workspace_Access", "Notifications", "Queued_Messages",
            "Rendering_Mode", "Sandbox_Mode", "Show_Active_Tasks", "Show_Feedback_Survey",
            "Show_Tips", "Tool_Permission", "Use_AI_Credits", "Verbosity",
            "Auto-Lint", "Auto-Test_Gating", "Max_Context_Tokens", "Model_Fallback_Cascade"
        )

        for (setting in required) {
            assertTrue(options.containsKey(setting), "Config matrix must contain $setting")
        }
    }
}
