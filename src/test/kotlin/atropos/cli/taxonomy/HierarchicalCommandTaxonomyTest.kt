/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.taxonomy

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HierarchicalCommandTaxonomyTest {

    private val taxonomy = HierarchicalCommandTaxonomy()

    @Test
    fun `default taxonomy registers all eight android apk categories`() {
        val rootNodes = taxonomy.stepInto(emptyList())
        val categories = rootNodes.map { it.token }.toSet()
        assertEquals(8, categories.size)
        assertTrue(categories.contains("agent"))
        assertTrue(categories.contains("providers"))
        assertTrue(categories.contains("code"))
        assertTrue(categories.contains("workspace"))
        assertTrue(categories.contains("git"))
        assertTrue(categories.contains("mcp"))
        assertTrue(categories.contains("diagnostics"))
        assertTrue(categories.contains("specgraph"))
    }

    @Test
    fun `stepping into agent returns subcategories`() {
        val agentSubcats = taxonomy.stepInto(listOf("agent"))
        val subcatTokens = agentSubcats.map { it.token }
        assertTrue(subcatTokens.contains("Trajectory & Work"))
        assertTrue(subcatTokens.contains("Queue & Hierarchy"))
    }

    @Test
    fun `agent goal resolves to deterministic explanation string`() {
        val resolution = taxonomy.resolveLeaf(listOf("agent", "Trajectory & Work", "/goal"))
        assertNotNull(resolution)
        assertTrue(resolution.contains("autonomous task trajectory", ignoreCase = true))
    }

    @Test
    fun `repo rollback backward compatibility resolves deterministically`() {
        val resolution = taxonomy.resolveLeaf(listOf("repo", "rollback", "<hash>"))
        assertNotNull(resolution)
        assertTrue(resolution.contains("Rolls back repository workspace"))
        assertTrue(resolution.contains("CAS"))
    }

    @Test
    fun `cheatsheet modal renders formatted table with all categories`() {
        val cheatsheet = taxonomy.cheatsheet()
        assertTrue(cheatsheet.contains("COMMAND TAXONOMY CHEATSHEET"))
        assertTrue(cheatsheet.contains("CATEGORY: ⚡ /agent"))
        assertTrue(cheatsheet.contains("CATEGORY: 🌿 /git"))
    }

    @Test
    fun `invalid path returns empty node list`() {
        val nodes = taxonomy.stepInto(listOf("nonexistent_domain", "action"))
        assertTrue(nodes.isEmpty())
    }
}
