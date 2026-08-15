/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArchitectureComplianceCheckerTest {
    @Test
    fun enforcing_checker_blocks_large_mixed_concern_file() {
        val root = Files.createTempDirectory("atropos-architecture-")
        val file = root.resolve("src/main/kotlin/atropos/cli/commands/AgentCommand.kt")
        Files.createDirectories(file.parent)
        Files.writeString(
            file,
            largeKotlinFile(
                """
                package atropos.cli.commands
                import atropos.cli.ui.AnsiTerminalEngine
                class AgentCommand {
                    fun execute(tokens: List<String>) {
                        when (tokens[1]) {
                            "status" -> ui.renderNotice("status")
                        }
                    }
                }
                """.trimIndent(),
                420
            )
        )

        val report = ArchitectureComplianceChecker(enforcing = true).checkFiles(listOf(file.toFile()))

        assertTrue(report.blocksBuild)
        assertEquals("file.atomic.single_responsibility", report.violations.single().invariant)
        assertTrue(report.violations.single().mixedConcerns.contains("routing+rendering"))
    }

    @Test
    fun path_specific_threshold_catches_named_checkpoint_file() {
        val root = Files.createTempDirectory("atropos-architecture-dloi-")
        val file = root.resolve("src/main/kotlin/atropos/dloi/DloiService.kt")
        Files.createDirectories(file.parent)
        Files.writeString(
            file,
            largeKotlinFile(
                """
                package atropos.dloi
                import java.nio.file.Files
                class DloiService {
                    fun loadDocuments() = Files.walk(java.nio.file.Path.of("."))
                    private fun parse(address: String) = ParsedDloiAddress(address)
                    private data class ParsedDloiAddress(val value: String)
                }
                """.trimIndent(),
                320
            )
        )

        val report = ArchitectureComplianceChecker(enforcing = false).checkFiles(listOf(file.toFile()))

        assertFalse(report.blocksBuild, "advisory mode must not block")
        assertEquals(1, report.violations.size)
        assertTrue(report.violations.single().mixedConcerns.contains("source-loading+address-parsing"))
    }

    @Test
    fun long_single_responsibility_file_passes() {
        val root = Files.createTempDirectory("atropos-architecture-single-")
        val file = root.resolve("src/main/kotlin/example/LongPureModel.kt")
        Files.createDirectories(file.parent)
        Files.writeString(
            file,
            largeKotlinFile(
                """
                package example
                data class LongPureModel(val value: String)
                """.trimIndent(),
                450
            )
        )

        val report = ArchitectureComplianceChecker(enforcing = true).checkFiles(listOf(file.toFile()))

        assertTrue(report.passed)
        assertFalse(report.blocksBuild)
    }

    @Test
    fun cross_language_sources_use_the_same_atomicity_gate() {
        val root = Files.createTempDirectory("atropos-architecture-python-")
        val file = root.resolve("src/specgraph_foundry/router.py")
        Files.createDirectories(file.parent)
        Files.writeString(
            file,
            largeKotlinFile(
                """
                def route(request):
                    subprocess.run(request)
                    print("result")
                """.trimIndent(),
                420
            )
        )

        val report = ArchitectureComplianceChecker(enforcing = true).checkFiles(listOf(file.toFile()))

        assertTrue(report.blocksBuild)
        assertTrue(report.violations.single().mixedConcerns.contains("routing+rendering"))
    }

    @Test
    fun comments_and_literals_do_not_create_mixed_concerns() {
        val root = Files.createTempDirectory("atropos-architecture-mask-")
        val file = root.resolve("src/main/kotlin/example/SourceText.kt")
        Files.createDirectories(file.parent)
        Files.writeString(
            file,
            largeKotlinFile(
                listOf(
                    "package example",
                    "// fun execute() and Files.walk() are documentation, not execution.",
                    "class SourceText {",
                    "    val evidence = \"\"\"",
                    "        fun execute() { waitFor() }",
                    "        fun parseSelector() = Files.walk(path)",
                    "    \"\"\"",
                    "}"
                ).joinToString("\n"),
                420
            )
        )

        val report = ArchitectureComplianceChecker(enforcing = true).checkFiles(listOf(file.toFile()))

        assertTrue(report.passed)
    }

    @Test
    fun lexical_mask_preserves_newline_shape() {
        val source = "// fun execute()\nclass Example {\n    val text = \"fun verify()\"\n}"

        val masked = ArchitectureSourceMasker().mask(source)

        assertEquals(source.count { it == '\n' }, masked.count { it == '\n' })
        assertFalse(masked.contains("execute"))
        assertFalse(masked.contains("verify"))
    }

    @Test
    fun file_count_limits_pass_within_thresholds() {
        val root = Files.createTempDirectory("atropos-architecture-counts-")
        val file1 = root.resolve("File1.kt")
        Files.writeString(file1, "val a = 1\n")
        val checker = ArchitectureComplianceChecker()
        assertTrue(checker.checkFileCountLimits(listOf(file1.toFile())))
        root.toFile().deleteRecursively()
    }

    private fun largeKotlinFile(header: String, targetLines: Int): String {
        val lines = header.lines().toMutableList()
        while (lines.size < targetLines) {
            lines += "val generatedLine${lines.size} = ${lines.size}"
        }
        return lines.joinToString("\n")
    }
}
