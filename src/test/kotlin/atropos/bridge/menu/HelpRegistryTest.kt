package atropos.bridge.menu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HelpRegistryTest {
    @Test
    fun command_and_bridge_surfaces_share_help_registry_without_shell_exposure() {
        val commands = HelpRegistry.commandEntries().map { it.command }
        val actions = HelpRegistry.actions()

        assertTrue(commands.contains("/help"))
        assertTrue(HelpRegistry.commandSections().isNotEmpty())
        assertEquals(actions, BridgeMenuCatalog.actions())
        assertFalse(actions.any { it.label.contains("shell", ignoreCase = true) })
        assertFalse(actions.any { it.label.contains("change shell", ignoreCase = true) })
    }
}
