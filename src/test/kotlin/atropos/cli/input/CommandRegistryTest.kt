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
        assertTrue(helpMatches.contains("/commands"), helpMatches.joinToString(", "))

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
        val orientSection = CommandRegistry.helpSections().first { it.category == "Orient" }
        val helpEntry = orientSection.entries.first { it.command == "/help" }

        assertTrue(helpEntry.aliases.contains("/usage"), helpEntry.aliases.joinToString(", "))
        assertTrue(helpEntry.aliases.contains("/?"), helpEntry.aliases.joinToString(", "))
        assertTrue(helpEntry.aliases.contains("/commands"), helpEntry.aliases.joinToString(", "))

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

    @Test
    fun commands_alias_opens_the_same_grouped_palette() {
        val state = PromptSuggestionState { true }

        assertTrue(state.isGroupLevel("/commands"))
        state.moveSelectionDown("/commands")
        assertTrue(state.level("/commands") == CommandPaletteLevel.GROUPS)
        assertTrue(state.expand("/commands"))
        assertTrue(state.level("/commands") == CommandPaletteLevel.COMMANDS)
        assertTrue(state.selectedCommand("/commands") != null)
    }

    @Test
    fun categories_are_normalized_and_keywords_resolve_to_canonical_commands() {
        val categories = CommandRegistry.helpSections().map { it.category }
        assertTrue(categories.all {
            it in setOf("Orient", "Models", "Build", "Agent", "Self-host", "Authority", "Governance", "Shell", "Keys/Paid", "Observe", "Autonomous", "Session")
        }, categories.joinToString())
        assertTrue(CommandRegistry.search("app").any { it.command == "/factory" })
        assertTrue(CommandRegistry.search("phase11").any { it.command == "/self-host" })
        assertTrue(CommandRegistry.search("provider").any { it.command == "/providers" })
    }

    @Test
    fun palette_navigator_expands_without_execution_and_enters_only_at_command_levels() {
        val navigator = CommandPaletteNavigator()
        assertTrue(navigator.enter() is CommandPaletteAction.Stay)
        navigator.right()
        assertTrue(navigator.selection.level == CommandPaletteLevel.COMMANDS)
        val action = navigator.enter()
        assertTrue(action is CommandPaletteAction.Execute, action.toString())
        navigator.right()
        assertTrue(navigator.enter() is CommandPaletteAction.Execute)
    }
}
