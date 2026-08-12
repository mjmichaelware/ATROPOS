/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.commands

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SystemCommandCatalogTest {

    @Test
    fun `every catalog entry names a routable command`() {
        val routed = setOf(
            "director", "territory", "hr", "auditor", "custodian", "hierarchy",
            "dag", "snapshot", "inspect", "platform", "artifact", "autonomous"
        )
        assertEquals(
            routed,
            SystemCommandCatalog.commandNames,
            "a catalog entry with no dispatch branch advertises a command that does nothing"
        )
    }

    @Test
    fun `entries carry no leading slash so the dispatcher can match them directly`() {
        SystemCommandCatalog.entries.forEach { entry ->
            assertTrue(!entry.name.startsWith("/"), "${entry.name} must be stored unprefixed")
        }
    }

    @Test
    fun `rendered help lists every command with its phase`() {
        val rendered = SystemCommandCatalog.render()
        SystemCommandCatalog.entries.forEach { entry ->
            assertTrue(
                rendered.contains("/${entry.name}"),
                "${entry.name} is routable but missing from the rendered catalog"
            )
            assertTrue(
                rendered.contains("PHASE ${entry.phase}"),
                "phase ${entry.phase} missing from the rendered catalog"
            )
        }
    }

    @Test
    fun `no command is listed twice`() {
        val names = SystemCommandCatalog.entries.map { it.name }
        assertEquals(names.size, names.distinct().size, "duplicate catalog rows render duplicate help")
    }

    @Test
    fun `entries stay in phase order`() {
        val phases = SystemCommandCatalog.entries.map { it.phase }
        assertEquals(phases.sorted(), phases, "the catalog reads as a phase walkthrough and must stay ordered")
    }
}
