package atropos.core.verification

import kotlin.test.Test
import kotlin.test.assertTrue
import java.nio.file.Files
import java.nio.file.Path

class DeterministicVerifierTest {
    @Test
    fun catches_broken_package_path_and_shell_safety() {
        val root = Files.createTempDirectory("atropos-deterministic")
        val source = root.resolve("src/main/kotlin/example").also { Files.createDirectories(it) }.resolve("Bad.kt")
        Files.writeString(
            source,
            """
            package wrong.pkg
            import example.*
            import example.missing.Type
            import kotlin.text.StringBuilder
            import kotlin.text.StringBuilder
            fun bad() = "x"
            """.trimIndent()
        )

        val verifier = DeterministicVerifier(root)
        val result = verifier.verify(
            sourcePaths = listOf(source),
            shellCommand = "rm -rf /"
        )

        assertTrue(result.findings.any { it.invariantId == "package_path_invariant" })
        assertTrue(result.findings.any { it.invariantId == "duplicate_imports" })
        assertTrue(result.findings.any { it.invariantId == "import_reconciliation" })
        assertTrue(result.findings.any { it.invariantId == "shell_safety" })
        assertTrue(result.findings.first { it.invariantId == "package_path_invariant" }.evidence.contains("expected="))
        assertTrue(result.findings.first { it.invariantId == "package_path_invariant" }.evidence.contains("observed="))
        assertTrue(result.findings.all { it.evidence.isNotBlank() })
        assertTrue(result.findings.all { it.remediation.isNotBlank() })
        assertTrue(result.findings.all { it.classification == DeterministicClassification.DETERMINISTIC })
        assertTrue(!result.passed)
    }

    @Test
    fun catches_invalid_dloi_address_and_forbidden_patch_path() {
        val verifier = DeterministicVerifier(Path.of(".").toAbsolutePath().normalize())
        val result = verifier.verify(
            sourcePaths = listOf(Path.of("build/generated/Fake.kt").toAbsolutePath().normalize()),
            patchText = """
                diff --git a/build/output.txt b/build/output.txt
                --- a/build/output.txt
                +++ b/build/output.txt
                @@ -0,0 +1 @@
                +bad
            """.trimIndent(),
            dloiAddress = "authority#does_not_exist"
        )

        assertTrue(result.findings.any { it.invariantId == "forbidden_path" })
        assertTrue(result.findings.any { it.invariantId == "patch_structure" })
        assertTrue(result.findings.any { it.invariantId == "dloi_address" })
    }

    @Test
    fun catches_out_of_scope_sources_before_model_review() {
        val root = Files.createTempDirectory("atropos-deterministic-root-")
        val outside = Files.createTempDirectory("atropos-deterministic-outside-").resolve("Out.kt")
        Files.writeString(outside, "package outside\nclass Out\n")

        val result = DeterministicVerifier(root).verify(sourcePaths = listOf(outside))

        assertTrue(result.findings.any { it.invariantId == "source_scope" })
        assertTrue(result.findings.all { it.classification == DeterministicClassification.DETERMINISTIC })
        assertTrue(!result.passed)
    }
}
