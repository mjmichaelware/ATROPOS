/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.taxonomy

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HierarchicalCommandTaxonomyTest {

    private val taxonomy = HierarchicalCommandTaxonomy()

    @Test
    fun `default taxonomy registers 7 domains`() {
        val rootNodes = taxonomy.stepInto(emptyList())
        val domains = rootNodes.map { it.token }.toSet()
        assertTrue(domains.contains("repo"))
        assertTrue(domains.contains("factory"))
        assertTrue(domains.contains("agent"))
        assertTrue(domains.contains("memory"))
        assertTrue(domains.contains("governance"))
        assertTrue(domains.contains("toolchain"))
        assertTrue(domains.contains("system"))
    }

    @Test
    fun `stepping into repo returns actions`() {
        val repoActions = taxonomy.stepInto(listOf("repo"))
        val actionTokens = repoActions.map { it.token }
        assertTrue(actionTokens.contains("rollback"))
        assertTrue(actionTokens.contains("status"))
        assertTrue(actionTokens.contains("diff"))
    }

    @Test
    fun `repo rollback resolves to deterministic explanation string`() {
        val resolution = taxonomy.resolveLeaf(listOf("repo", "rollback", "<hash>"))
        assertNotNull(resolution)
        assertTrue(resolution.contains("Rolls back repository workspace"))
        assertTrue(resolution.contains("CAS"))
    }

    @Test
    fun `cheatsheet modal renders formatted table with all domains`() {
        val cheatsheet = taxonomy.cheatsheet()
        assertTrue(cheatsheet.contains("COMMAND TAXONOMY CHEATSHEET"))
        assertTrue(cheatsheet.contains("DOMAIN: /repo"))
        assertTrue(cheatsheet.contains("DOMAIN: /system"))
    }

    @Test
    fun `invalid path returns empty node list`() {
        val nodes = taxonomy.stepInto(listOf("nonexistent_domain", "action"))
        assertTrue(nodes.isEmpty())
    }
}
