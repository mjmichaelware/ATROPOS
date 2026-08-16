/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Compares a declared source snapshot with the bytes currently on disk.
 *
 * The gate is deliberately content-only: it never invokes a build, reads
 * process state, or treats a successful command as evidence of reproduction.
 * Callers must provide the baseline manifest before a release decision can
 * use this gate.
 */
data class ReproducibilityInput(
    val actualRoot: Path,
    val expectedFiles: Map<String, String>
)

data class ReproducibilityResult(
    val passed: Boolean,
    val reason: String,
    val expectedFileCount: Int,
    val comparedFileCount: Int,
    val snapshotSha256: String
)

class ReproducibilityGate {
    fun evaluate(input: ReproducibilityInput): ReproducibilityResult {
        if (input.expectedFiles.isEmpty()) {
            return ReproducibilityResult(
                passed = false,
                reason = "reproducibility baseline is empty",
                expectedFileCount = 0,
                comparedFileCount = 0,
                snapshotSha256 = digest("empty-baseline")
            )
        }

        val actual = linkedMapOf<String, String>()
        for ((relative, expectedHash) in input.expectedFiles.toSortedMap()) {
            val normalized = normalizeRelative(relative)
                ?: return failure(input.expectedFiles.size, actual.size, "unsafe baseline path $relative")
            val path = PathResolver.resolveSafe(input.actualRoot, normalized)
            if (!path.startsWith(input.actualRoot.normalize()) || !Files.isRegularFile(path)) {
                return failure(input.expectedFiles.size, actual.size, "baseline file missing $relative")
            }
            val actualHash = sha256(Files.readAllBytes(path))
            actual[normalized] = actualHash
            if (!actualHash.equals(expectedHash, ignoreCase = true)) {
                return failure(input.expectedFiles.size, actual.size, "content mismatch $relative")
            }
        }

        return ReproducibilityResult(
            passed = true,
            reason = "all declared files reproduced",
            expectedFileCount = input.expectedFiles.size,
            comparedFileCount = actual.size,
            snapshotSha256 = snapshotDigest(actual)
        )
    }

    fun snapshot(root: Path, relativeFiles: Iterable<String>): Map<String, String> =
        relativeFiles.mapNotNull { relative ->
            val normalized = normalizeRelative(relative) ?: return@mapNotNull null
            val path = PathResolver.resolveSafe(root, normalized)
            if (!path.startsWith(root.normalize()) || !Files.isRegularFile(path)) return@mapNotNull null
            normalized to sha256(Files.readAllBytes(path))
        }.toMap().toSortedMap()

    private fun failure(expected: Int, compared: Int, reason: String) = ReproducibilityResult(
        passed = false,
        reason = reason,
        expectedFileCount = expected,
        comparedFileCount = compared,
        snapshotSha256 = digest(reason)
    )

    private fun snapshotDigest(files: Map<String, String>): String =
        digest(files.entries.joinToString("\n") { "${it.key}=${it.value}" })

    private fun normalizeRelative(value: String): String? {
        if (value.isBlank() || value.startsWith("/") || value.contains('\u0000')) return null
        val path = Path.of(value).normalize()
        if (path.isAbsolute || path.startsWith("..")) return null
        return path.toString().replace(path.fileSystem.separator, "/")
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun digest(value: String): String = sha256(value.toByteArray(StandardCharsets.UTF_8))
}
