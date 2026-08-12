package atropos.core.phase20

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReproducibilityGateTest {
    private val gate = ReproducibilityGate()

    @Test
    fun matching_declared_bytes_pass() {
        val root = Files.createTempDirectory("atropos-repro-pass-")
        Files.createDirectories(root.resolve("src"))
        Files.writeString(root.resolve("src/Main.kt"), "fun main() = Unit\n")

        val result = gate.evaluate(ReproducibilityInput(root, manifest(root, "src/Main.kt")))

        assertTrue(result.passed, result.reason)
        assertTrue(result.snapshotSha256.isNotBlank())
    }

    @Test
    fun changed_or_missing_bytes_fail_closed() {
        val root = Files.createTempDirectory("atropos-repro-fail-")
        Files.writeString(root.resolve("Main.kt"), "before\n")
        val manifest = manifest(root, "Main.kt")
        Files.writeString(root.resolve("Main.kt"), "after\n")

        assertFalse(gate.evaluate(ReproducibilityInput(root, manifest)).passed)
        assertFalse(gate.evaluate(ReproducibilityInput(root, mapOf("missing.kt" to "00"))).passed)
    }

    @Test
    fun unsafe_paths_and_empty_baselines_fail_without_reading_outside_root() {
        val root = Files.createTempDirectory("atropos-repro-scope-")

        assertFalse(gate.evaluate(ReproducibilityInput(root, mapOf("../outside" to "00"))).passed)
        assertFalse(gate.evaluate(ReproducibilityInput(root, emptyMap())).passed)
    }

    private fun manifest(root: Path, relative: String): Map<String, String> =
        mapOf(relative to sha256(Files.readAllBytes(root.resolve(relative))))

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
