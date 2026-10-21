/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.security

import atropos.core.AtroposRepoRootLocator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths

class ProtectedPathPolicyTest {

    private val tempDir = Files.createTempDirectory("atropos-protected-path-test-")
    private val repoRoot = tempDir.toAbsolutePath().normalize()

    @Test
    fun `direct writes to protected ATROPOS source files are rejected`() {
        val policy = createPolicy(repoRoot)

        val result = policy.checkPath("src/main/kotlin/atropos/core/agent/AgentService.kt")
        assertTrue(result.isProtected)
        assertTrue(result.reason.contains("ATROPOS source file"))
    }

    @Test
    fun `direct writes to protected build configuration files are rejected`() {
        val policy = createPolicy(repoRoot)

        val result = policy.checkPath("build.gradle.kts")
        assertTrue(result.isProtected)
        assertTrue(result.reason.contains("build configuration file"))
    }

    @Test
    fun `direct writes to protected directories are rejected`() {
        val policy = createPolicy(repoRoot)

        val result = policy.checkPath(".git/config")
        assertTrue(result.isProtected)
        assertTrue(result.reason.contains("protected directory"))
    }

    @Test
    fun `path traversal attempts are rejected`() {
        val policy = createPolicy(repoRoot)

        val result = policy.checkPath("../../../etc/passwd")
        assertTrue(result.isProtected)
        assertTrue(result.reason.contains("escapes repo root") || result.reason.contains("could not be normalized"))
    }

    @Test
    fun `path traversal with encoded slashes is rejected`() {
        val policy = createPolicy(repoRoot)

        val result = policy.checkPath("..%2F..%2Fetc%2Fpasswd")
        assertTrue(result.isProtected)
    }

    @Test
    fun `symlink escapes to protected roots are rejected`() {
        // Create a symlink inside the repo that points to a protected directory
        val targetDir = repoRoot.resolve("src/main/kotlin/atropos")
        Files.createDirectories(targetDir)

        val linkDir = repoRoot.resolve("my-project")
        Files.createDirectories(linkDir)

        val symlink = linkDir.resolve("atropos-link")
        try {
            Files.createSymbolicLink(symlink, targetDir)
        } catch (e: IOException) {
            // Symlinks may not be supported on all filesystems (e.g., Windows without admin)
            return
        }

        val policy = createPolicy(repoRoot)

        // Check via string path
        val result = policy.checkPath("my-project/atropos-link/core/agent/AgentService.kt")
        assertTrue(result.isProtected, "Symlink escape should be detected: ${result.reason}")

        // Check via real path method
        val realResult = policy.checkPathReal(symlink.resolve("core/agent/AgentService.kt"))
        assertTrue(realResult.isProtected, "Real path check should detect symlink escape: ${realResult.reason}")
    }

    @Test
    fun `symlink escapes to .git directory are rejected`() {
        val gitDir = repoRoot.resolve(".git")
        Files.createDirectories(gitDir.resolve("objects"))

        val linkDir = repoRoot.resolve("my-project")
        Files.createDirectories(linkDir)

        val symlink = linkDir.resolve("git-link")
        try {
            Files.createSymbolicLink(symlink, gitDir)
        } catch (e: IOException) {
            return
        }

        val policy = createPolicy(repoRoot)

        val result = policy.checkPathReal(symlink.resolve("objects/pack"))
        assertTrue(result.isProtected, "Symlink to .git should be rejected: ${result.reason}")
    }

    @Test
    fun `symlink escapes to .atropos/secrets are rejected`() {
        val secretsDir = repoRoot.resolve(".atropos/secrets")
        Files.createDirectories(secretsDir)

        val linkDir = repoRoot.resolve("my-project")
        Files.createDirectories(linkDir)

        val symlink = linkDir.resolve("secrets-link")
        try {
            Files.createSymbolicLink(symlink, secretsDir)
        } catch (e: IOException) {
            return
        }

        val policy = createPolicy(repoRoot)

        val result = policy.checkPathReal(symlink.resolve("owner.shadow"))
        assertTrue(result.isProtected, "Symlink to .atropos/secrets should be rejected: ${result.reason}")
    }

    @Test
    fun `ordinary target workspace files remain editable`() {
        val policy = createPolicy(repoRoot)

        val result = policy.checkPath("my-project/src/main/java/com/example/Main.java")
        assertTrue(result.isAllowed, "Ordinary workspace files should be allowed: ${result.reason}")
    }

    @Test
    fun `target workspace files with similar names to protected paths are allowed`() {
        val policy = createPolicy(repoRoot)

        // File named like a protected file but in workspace
        val result = policy.checkPath("my-project/build.gradle.kts")
        assertTrue(result.isAllowed, "Workspace build.gradle.kts should be allowed: ${result.reason}")
    }

    @Test
    fun `protected file patterns anywhere in repo are rejected`() {
        val policy = createPolicy(repoRoot)

        val result = policy.checkPath("my-project/ATROPOS.jar")
        assertTrue(result.isProtected, "ATROPOS.jar pattern should be rejected: ${result.reason}")
    }

    @Test
    fun `Phase 20 authorization allows specific scoped paths`() {
        val policy = createPolicy(repoRoot)
        val auth = ProtectedPathAuthorization.Phase20Authorized(
            scope = "test-scope",
            allowedPaths = listOf("src/main/kotlin/atropos/core/agent/NewFeature.kt")
        )
        val authorizedPolicy = policy.withAuthorization(auth)

        // Authorized path should be allowed
        val allowedResult = authorizedPolicy.checkPath("src/main/kotlin/atropos/core/agent/NewFeature.kt")
        assertTrue(allowedResult.isAllowed, "Authorized path should be allowed: ${allowedResult.reason}")

        // Other protected paths should still be protected
        val protectedResult = authorizedPolicy.checkPath("src/main/kotlin/atropos/core/provider/ProviderService.kt")
        assertTrue(protectedResult.isProtected, "Non-authorized path should be protected: ${protectedResult.reason}")
    }

    @Test
    fun `Phase 20 authorization with directory scope allows subpaths`() {
        val policy = createPolicy(repoRoot)
        val auth = ProtectedPathAuthorization.Phase20Authorized(
            scope = "test-scope",
            allowedPaths = listOf("src/main/kotlin/atropos/core/agent")
        )
        val authorizedPolicy = policy.withAuthorization(auth)

        val allowedResult = authorizedPolicy.checkPath("src/main/kotlin/atropos/core/agent/NewFeature.kt")
        assertTrue(allowedResult.isAllowed, "Subpath of authorized directory should be allowed: ${allowedResult.reason}")

        val allowedResult2 = authorizedPolicy.checkPath("src/main/kotlin/atropos/core/agent/sub/NewFile.kt")
        assertTrue(allowedResult2.isAllowed, "Deep subpath of authorized directory should be allowed: ${allowedResult2.reason}")
    }

    @Test
    fun `unauthorized Phase 20 invocations fail closed`() {
        val policy = createPolicy(repoRoot)
        // No authorization provided

        val result = policy.checkPath("src/main/kotlin/atropos/core/agent/AgentService.kt")
        assertTrue(result.isProtected, "Without authorization, protected paths should be rejected: ${result.reason}")
    }

    @Test
    fun `authorized Phase 20 actions restricted to approved path scope`() {
        val policy = createPolicy(repoRoot)
        val auth = ProtectedPathAuthorization.Phase20Authorized(
            scope = "test-scope",
            allowedPaths = listOf("src/main/kotlin/atropos/core/agent/AllowedFile.kt")
        )
        val authorizedPolicy = policy.withAuthorization(auth)

        // Only the exact authorized path should be allowed
        assertTrue(authorizedPolicy.checkPath("src/main/kotlin/atropos/core/agent/AllowedFile.kt").isAllowed)
        assertTrue(authorizedPolicy.checkPath("src/main/kotlin/atropos/core/agent/OtherFile.kt").isProtected)
        assertTrue(authorizedPolicy.checkPath("src/main/kotlin/atropos/core/provider/ProviderService.kt").isProtected)
    }

    @Test
    fun `checkPathsReal uses filesystem canonicalization`() {
        val policy = createPolicy(repoRoot)

        // Create a test file structure
        val srcDir = repoRoot.resolve("src/main/kotlin/atropos/core")
        Files.createDirectories(srcDir)
        val testFile = srcDir.resolve("Test.kt")
        Files.writeString(testFile, "test")

        // Check via real path
        val result = policy.checkPathsReal(listOf(testFile))
        assertTrue(result.isProtected, "Real path check should protect ATROPOS source: ${result.reason}")
    }

    @Test
    fun `checkCommand rejects git checkout in protected repo`() {
        val policy = createPolicy(repoRoot)

        val result = policy.checkCommand(listOf("git", "checkout", "HEAD"), repoRoot)
        assertTrue(result.isProtected, "Git checkout in protected repo should be rejected: ${result.reason}")
    }

    @Test
    fun `checkCommand allows git status in protected repo`() {
        val policy = createPolicy(repoRoot)

        val result = policy.checkCommand(listOf("git", "status"), repoRoot)
        // git status is read-only, should be allowed (returns Allowed with empty string)
        assertTrue(result.isAllowed, "Git status should be allowed: ${result.reason}")
    }

    @Test
    fun `checkCommand rejects rm on protected files`() {
        val policy = createPolicy(repoRoot)

        val result = policy.checkCommand(listOf("rm", "src/main/kotlin/atropos/core/agent/AgentService.kt"), repoRoot)
        assertTrue(result.isProtected, "rm on protected file should be rejected: ${result.reason}")
    }

    @Test
    fun `normalizePath handles alternate path spellings`() {
        val policy = createPolicy(repoRoot)

        // Double slashes
        val result1 = policy.checkPath("src//main//kotlin//atropos//core//agent//AgentService.kt")
        assertTrue(result1.isProtected)

        // ./ prefix
        val result2 = policy.checkPath("./src/main/kotlin/atropos/core/agent/AgentService.kt")
        assertTrue(result2.isProtected)

        // Mixed separators (on Unix)
        val result3 = policy.checkPath("src\\main\\kotlin\\atropos\\core\\agent\\AgentService.kt")
        assertTrue(result3.isProtected)
    }

    @Test
    fun `normalization fails for paths outside repo root`() {
        val policy = createPolicy(repoRoot)

        // Absolute path outside repo
        val outsidePath = Paths.get("/tmp/outside")
        val result = policy.checkPath(outsidePath.toString())
        assertTrue(result.isProtected)
        assertTrue(result.reason.contains("escapes repo root") || result.reason.contains("could not be normalized"))
    }

    @Test
    fun `authorization None never allows any path`() {
        val auth = ProtectedPathAuthorization.None

        assertFalse(auth.allows("any/path"))
        assertFalse(auth.allows("src/main/kotlin/atropos/core/agent/AgentService.kt"))
    }

    @Test
    fun `authorization Phase20Authorized matches exact path and subpaths`() {
        val auth = ProtectedPathAuthorization.Phase20Authorized(
            scope = "test",
            allowedPaths = listOf("src/main/kotlin/atropos/core/agent")
        )

        assertTrue(auth.allows("src/main/kotlin/atropos/core/agent"))
        assertTrue(auth.allows("src/main/kotlin/atropos/core/agent/File.kt"))
        assertTrue(auth.allows("src/main/kotlin/atropos/core/agent/sub/File.kt"))
        assertFalse(auth.allows("src/main/kotlin/atropos/core/provider/File.kt"))
        assertFalse(auth.allows("other/path"))
    }

    private fun createPolicy(repoRoot: Path): ProtectedPathPolicy {
        // Create minimal ATROPOS structure for testing
        Files.createDirectories(repoRoot.resolve("src/main/kotlin/atropos/core/agent"))
        Files.createDirectories(repoRoot.resolve("src/main/kotlin/atropos/core/provider"))
        Files.createDirectories(repoRoot.resolve(".git"))
        Files.createDirectories(repoRoot.resolve(".atropos/secrets"))
        Files.writeString(repoRoot.resolve("build.gradle.kts"), "")
        Files.writeString(repoRoot.resolve("settings.gradle.kts"), "")
        Files.writeString(repoRoot.resolve("gradle.properties"), "")

        return ProtectedPathPolicy(repoRoot)
    }
}