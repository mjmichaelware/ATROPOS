package atropos.cli.input

import kotlin.test.Test
import kotlin.test.assertTrue

class CommandRegistryTest {
    @Test
    fun help_and_self_host_aliases_stay_grouped_under_the_registry() {
        val helpMatches = CommandRegistry.search("/help").map { it.command }
        assertTrue(helpMatches.contains("/help"), helpMatches.joinToString(", "))
        assertTrue(helpMatches.contains("/usage"), helpMatches.joinToString(", "))
        assertTrue(helpMatches.contains("/?"), helpMatches.joinToString(", "))

        val selfHostMatches = CommandRegistry.search("/self-host").map { it.command }
        assertTrue(selfHostMatches.contains("/self-host"), selfHostMatches.joinToString(", "))
        assertTrue(selfHostMatches.contains("/agent self-host"), selfHostMatches.joinToString(", "))
        assertTrue(selfHostMatches.contains("/self-host run"), selfHostMatches.joinToString(", "))
        assertTrue(selfHostMatches.contains("/agent self-host run"), selfHostMatches.joinToString(", "))

        val quickAccess = CommandRegistry.quickAccessCommands()
        assertTrue(quickAccess.containsAll(listOf("/help", "/usage", "/?", "/self-host")), quickAccess.joinToString(", "))
    }

    @Test
    fun help_sections_expose_alias_metadata_once_per_canonical_command() {
        val systemSection = CommandRegistry.helpSections().first { it.category == "System" }
        val helpEntry = systemSection.entries.first { it.command == "/help" }

        assertTrue(helpEntry.aliases.contains("/usage"), helpEntry.aliases.joinToString(", "))
        assertTrue(helpEntry.aliases.contains("/?"), helpEntry.aliases.joinToString(", "))

        val selfHostSection = CommandRegistry.helpSections().first { it.category == "Self-host" }
        val root = selfHostSection.entries.first { it.command == "/self-host" }

        assertTrue(root.aliases.contains("/agent self-host"), root.aliases.joinToString(", "))
    }

    @Test
    fun self_host_control_surface_is_discoverable_from_registry() {
        val commands = CommandRegistry.commands().toSet()
        assertTrue(
            commands.containsAll(
                listOf(
                    "/self-host recover",
                    "/self-host next",
                    "/self-host promote",
                    "/self-host export-evidence"
                )
            ),
            commands.filter { it.startsWith("/self-host") }.joinToString(", ")
        )
    }
}
