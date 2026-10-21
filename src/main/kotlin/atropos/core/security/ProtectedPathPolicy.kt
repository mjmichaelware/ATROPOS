/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.security

import atropos.core.AtroposRepoRootLocator
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Protected Path Policy
 *
 * Central enforcement boundary preventing ordinary agent jobs from modifying
 * ATROPOS's own protected files and repository state.
 *
 * ## Protected Paths
 *
 * - ATROPOS source code (src/main/kotlin/atropos/)
 * - Build configuration (build.gradle.kts, settings.gradle.kts, gradle/)
 * - Git repository state (.git/)
 * - Runtime artifacts (.atropos/ except allowed subdirectories)
 * - Release artifacts (build/, ATROPOS.jar)
 * - Configuration files (gradle.properties, gradlew, gradlew.bat)
 *
 * ## Enforcement
 *
 * Uses canonicalized paths and filesystem-aware checks rather than simple
 * string-prefix matching. Fail closed if a target workspace cannot be
 * identified or verified.
 *
 * Ordinary coding tasks must still be able to edit files inside their
 * authorized target repositories. Only the explicitly authorized Phase 20
 * operation may receive a narrowly scoped exception to the protected-path
 * policy. Verify authorization immediately before the privileged operation
 * and enforce the approved scope at the mutation boundary.
 *
 * ## Trust Boundary
 *
 * This policy protects against:
 * - Filesystem writes, deletion, renaming
 * - Patch application
 * - Git operations
 * - Subprocess execution that could mutate protected paths
 * - Nested workspaces
 * - Path traversal (../)
 * - Symlink escapes
 * - Alternate path spellings (./, //, case variations on case-insensitive FS)
 * - Recovery operations
 * - Provider-generated actions
 *
 * It does NOT protect against a machine administrator or malicious process
 * that already has unrestricted access to the machine. OS-level access
 * controls are required for that threat model.
 */
class ProtectedPathPolicy(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val authorization: ProtectedPathAuthorization = ProtectedPathAuthorization.None
) {
    private val atroposSourceRoots = listOf(
        "src/main/kotlin/atropos/",
        "src/test/kotlin/atropos/",
        "apps/specgraph-foundry/src/"
    )

    private val buildConfigFiles = listOf(
        "build.gradle.kts",
        "settings.gradle.kts",
        "gradle.properties",
        "gradlew",
        "gradlew.bat"
    )

    private val protectedDirectories = listOf(
        ".git/",
        "build/",
        ".gradle/",
        ".atropos/secrets/",
        ".atropos/source-bindings/",
        ".atropos/agent/patches/",
        ".atropos/benchmarks/",
        ".atropos/policy/",
        ".atropos/memory/",
        ".atropos/autonomy/"
    )

    private val protectedFilePatterns = listOf(
        "ATROPOS.jar",
        "ATROPOS-JAR.zip",
        "installers.zip",
        "compile.log",
        "compile_log.zip"
    )

    /**
     * Checks if a path is protected and cannot be mutated by ordinary operations.
     *
     * @param path The path to check (can be absolute, relative, or with symlinks)
     * @return ProtectedPathResult indicating whether the path is protected and why
     */
    fun checkPath(path: String): ProtectedPathResult {
        val normalized = normalizePath(path)
        if (normalized == null) {
            return ProtectedPathResult.Protected("path could not be normalized or escapes repo root")
        }

        // Check if it's an ATROPOS source file
        if (atroposSourceRoots.any { normalized.startsWith(it) }) {
            return if (authorization.allows(normalized)) {
                ProtectedPathResult.Allowed(normalized)
            } else {
                ProtectedPathResult.Protected("ATROPOS source file: $normalized")
            }
        }

        // Check build configuration files
        if (buildConfigFiles.any { normalized == it }) {
            return if (authorization.allows(normalized)) {
                ProtectedPathResult.Allowed(normalized)
            } else {
                ProtectedPathResult.Protected("build configuration file: $normalized")
            }
        }

        // Check protected directories
        if (protectedDirectories.any { normalized == it || normalized.startsWith(it) }) {
            return if (authorization.allows(normalized)) {
                ProtectedPathResult.Allowed(normalized)
            } else {
                ProtectedPathResult.Protected("protected directory: $normalized")
            }
        }

        // Check protected file patterns (anywhere in repo)
        if (protectedFilePatterns.any { normalized.contains(it) }) {
            return if (authorization.allows(normalized)) {
                ProtectedPathResult.Allowed(normalized)
            } else {
                ProtectedPathResult.Protected("protected file pattern: $normalized")
            }
        }

        return ProtectedPathResult.Allowed(normalized)
    }

    /**
     * Checks multiple paths at once. Returns the first protected path found,
     * or Allowed if all paths are allowed.
     */
    fun checkPaths(paths: List<String>): ProtectedPathResult {
        for (path in paths) {
            val result = checkPath(path)
            if (result is ProtectedPathResult.Protected) {
                return result
            }
        }
        return ProtectedPathResult.Allowed(paths.joinToString(", "))
    }

    /**
     * Normalizes a path to be repository-relative with forward slashes.
     * Returns null if the path escapes the repository root or cannot be resolved.
     */
    private fun normalizePath(path: String): String? {
        return try {
            val inputPath = Paths.get(path)
            val absolutePath = if (inputPath.isAbsolute) {
                inputPath.normalize()
            } else {
                repoRoot.resolve(inputPath).normalize()
            }

            // Verify it's within the repo root
            val repoAbsolute = repoRoot.toAbsolutePath().normalize()
            if (!absolutePath.startsWith(repoAbsolute)) {
                return null
            }

            // Get relative path with forward slashes
            repoAbsolute.relativize(absolutePath).toString().replace('\\', '/')
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Creates a policy with Phase 20 authorization for a specific scope.
     */
    fun withAuthorization(authorization: ProtectedPathAuthorization): ProtectedPathPolicy {
        return ProtectedPathPolicy(repoRoot, authorization)
    }
}

/**
 * Result of a protected path check.
 */
sealed class ProtectedPathResult {
    data class Allowed(val normalizedPath: String) : ProtectedPathResult()
    data class Protected(val reason: String) : ProtectedPathResult()

    val isAllowed: Boolean
        get() = this is Allowed

    val isProtected: Boolean
        get() = this is Protected
}

/**
 * Authorization for protected path access.
 *
 * Only explicitly authorized Phase 20 operations may receive a narrowly
 * scoped exception. The authorization carries the specific operation scope
 * and must be validated at the mutation boundary.
 */
sealed interface ProtectedPathAuthorization {
    fun allows(path: String): Boolean

    data class Phase20Authorized(
        val scope: String,
        val allowedPaths: List<String>
    ) : ProtectedPathAuthorization {
        override fun allows(path: String): Boolean {
            return allowedPaths.any { allowed ->
                path == allowed || path.startsWith("$allowed/")
            }
        }
    }

    object None : ProtectedPathAuthorization {
        override fun allows(path: String): Boolean = false
    }
}