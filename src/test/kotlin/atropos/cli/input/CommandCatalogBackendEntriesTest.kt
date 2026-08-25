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
        assertTrue("/providers enable" in commands)
        assertTrue("/providers test <id>" in commands)
        assertTrue("/mcp search <query>" in commands)
        assertTrue("/mcp call <server> <tool>" in commands)
        assertTrue("/mcp ingest <path>" in commands)
        assertTrue("/github issues" in commands)
        assertTrue("/github issue" in commands)
        assertTrue("/github prs" in commands)
        assertTrue("/github pr-files" in commands)
        assertTrue("/github checks" in commands)
        assertTrue("/github branch-protection" in commands)
        assertTrue("/github blame" in commands)
        assertTrue("/github create-issue" in commands)
        assertTrue("/github comment-issue" in commands)
        assertTrue("/github create-pr" in commands)
        assertTrue("/github comment-pr" in commands)
        assertTrue("/github request-review" in commands)
        assertTrue("/github create-check" in commands)
        assertTrue("/github update-check" in commands)
        assertTrue("/git add" in commands)
        assertTrue("/git commit" in commands)
        assertTrue("/git rebase-continue" in commands)
        assertTrue("/agent context import <path>" in commands)
        assertTrue("/keys status" in commands)
        assertTrue("/auth github" in commands)
    }
}
