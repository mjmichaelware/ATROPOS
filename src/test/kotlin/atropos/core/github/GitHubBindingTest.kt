package atropos.core.github

import atropos.core.worktree.BoundedGitWorktreeCommandRunner
import atropos.core.worktree.GitWorktreeCommandResult
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubBindingTest {
    @Test
    fun push_requires_explicit_non_secret_authorization() {
        var called = false
        val binding = GitHubBinding(
            gitRunner = BoundedGitWorktreeCommandRunner { _, _, _ ->
                called = true
                GitWorktreeCommandResult(0, "pushed")
            }
        )

        val result = binding.push(
            GitHubPushRequest(
                repositoryRoot = Files.createTempDirectory("github-binding-auth-"),
                branch = "main",
                changedPaths = listOf("src/Main.kt"),
                declaredTerritory = listOf("src"),
                authorization = null
            )
        )

        assertFalse(result.allowed)
        assertTrue(result.message.contains("authorization"))
        assertFalse(called)
    }

    @Test
    fun push_delegates_only_after_territory_and_authorization_checks() {
        val captured = mutableListOf<List<String>>()
        val binding = GitHubBinding(
            gitRunner = BoundedGitWorktreeCommandRunner { command, _, _ ->
                captured += command
                GitWorktreeCommandResult(0, "pushed")
            }
        )

        val result = binding.push(
            GitHubPushRequest(
                repositoryRoot = Files.createTempDirectory("github-binding-push-"),
                branch = "main",
                changedPaths = listOf("src/Main.kt"),
                declaredTerritory = listOf("src"),
                authorization = GitHubPushAuthorization("operator", "confirm-1")
            )
        )

        assertTrue(result.allowed)
        assertEquals(listOf("git", "push"), captured.single())
    }

    @Test
    fun push_refuses_paths_outside_declared_territory() {
        var called = false
        val binding = GitHubBinding(
            gitRunner = BoundedGitWorktreeCommandRunner { _, _, _ ->
                called = true
                GitWorktreeCommandResult(0, "pushed")
            }
        )

        val result = binding.push(
            GitHubPushRequest(
                repositoryRoot = Files.createTempDirectory("github-binding-territory-"),
                branch = "main",
                changedPaths = listOf("src/Main.kt", "secrets.env"),
                declaredTerritory = listOf("src"),
                authorization = GitHubPushAuthorization("operator", "confirm-2")
            )
        )

        assertFalse(result.allowed)
        assertTrue(result.message.contains("outside declared territory"))
        assertFalse(called)
    }

    @Test
    fun repository_creation_delegates_to_credential_aware_provisioner() {
        var captured: GitHubRepositoryRequest? = null
        val binding = GitHubBinding(
            repositoryProvisioner = GitHubRepositoryProvisioner {
                captured = it
                GitHubBindingResult(true, "create", "created")
            }
        )

        val result = binding.createRepository(GitHubRepositoryRequest("calculator", "main"))

        assertTrue(result.allowed)
        assertEquals("calculator", captured?.repositoryName)
        assertEquals("main", captured?.defaultBranch)
    }
}
