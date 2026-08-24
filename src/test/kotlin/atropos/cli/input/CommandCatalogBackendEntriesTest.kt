package atropos.cli.input

import kotlin.test.Test
import kotlin.test.assertTrue

class CommandCatalogBackendEntriesTest {
    @Test
    fun factory_resume_is_advertised_by_the_shared_command_registry() {
        assertTrue(CommandCatalog.catalog.any { it.command == "/factory resume <run-id>" })
    }

    @Test
    fun provider_mcp_keys_and_context_import_are_advertised_by_the_same_registry() {
        val commands = CommandCatalog.catalog.map { it.command }.toSet()
        assertTrue("/providers connect" in commands)
        assertTrue("/providers prefer" in commands)
        assertTrue("/providers disable" in commands)
        assertTrue("/mcp search <query>" in commands)
        assertTrue("/mcp call <server> <tool>" in commands)
        assertTrue("/mcp ingest <path>" in commands)
        assertTrue("/agent context import <path>" in commands)
        assertTrue("/keys status" in commands)
    }
}
