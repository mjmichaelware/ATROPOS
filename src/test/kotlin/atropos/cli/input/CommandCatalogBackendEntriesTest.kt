package atropos.cli.input

import kotlin.test.Test
import kotlin.test.assertTrue

class CommandCatalogBackendEntriesTest {
    @Test
    fun factory_resume_is_advertised_by_the_shared_command_registry() {
        assertTrue(CommandCatalog.catalog.any { it.command == "/factory resume <run-id>" })
    }
}
