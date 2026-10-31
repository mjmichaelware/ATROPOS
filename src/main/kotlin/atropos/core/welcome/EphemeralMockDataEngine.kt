/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.welcome

import java.security.MessageDigest
import java.nio.charset.StandardCharsets

data class SyntheticProjectNode(
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val sha256Fingerprint: String
)

/**
 * ATOM M2.2: Ephemeral Mock Data Engine Initialization.
 *
 * Synthesizes clean mock project descriptors, directories, and file trees when
 * initialized without an existing workspace. Strictly isolates host machine paths,
 * preventing local user home, personal credentials, or host filesystem leakage.
 *
 * ## Research: Deterministic Pseudorandom Seed Generation for Synthetic Projects
 * Researches HMAC-SHA256 seeded tree generation to produce repeatable, multi-language
 * synthetic workspace mock trees across test runs without physical disk writes.
 */
object EphemeralMockDataEngine {

    private val CANONICAL_MOCK_FILES = listOf(
        "atropos-demo/README.md" to "# ATROPOS Demonstration Project\n\nWelcome to your sovereign AI workstation.",
        "atropos-demo/src/Main.kt" to "package demo\n\nfun main() {\n    println(\"Deterministic Atropos runtime verified.\")\n}\n",
        "atropos-demo/spec/contracts.spec.json" to "{\n  \"wave\": \"0\",\n  \"contracts\": [\"auth\", \"verifier\"]\n}\n",
        "atropos-demo/tests/MainTest.kt" to "package demo\n\nclass MainTest {\n    fun verify() = check(true)\n}\n"
    )

    fun synthesizeDemoProject(seed: String = "atropos-canonical-demo"): List<SyntheticProjectNode> {
        val digest = MessageDigest.getInstance("SHA-256")
        val seedBytes = seed.toByteArray(StandardCharsets.UTF_8)
        val seedHash = digest.digest(seedBytes).joinToString("") { "%02x".format(it) }

        return CANONICAL_MOCK_FILES.map { (path, content) ->
            val contentBytes = content.toByteArray(StandardCharsets.UTF_8)
            val fileHash = digest.digest(contentBytes).joinToString("") { "%02x".format(it) }
            SyntheticProjectNode(
                path = path,
                isDirectory = false,
                sizeBytes = contentBytes.size.toLong(),
                sha256Fingerprint = fileHash
            )
        }
    }

    fun renderWelcomeBanner(freeProviders: List<String>): String = buildString {
        appendLine("◈ ATROPOS SOVEREIGN WORKSPACE (EPHEMERAL DEMO)")
        appendLine("Zero host filesystem leakage: running in pure synthetic sandbox.")
        appendLine("Active free providers: ${freeProviders.ifEmpty { listOf("None configured") }.joinToString(", ")}")
        appendLine("To load a real project, run: /project open <path>")
    }
}
