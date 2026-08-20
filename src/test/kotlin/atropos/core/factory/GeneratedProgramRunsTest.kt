/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The generated repository is compiled and run, not read.
 *
 * String-matching a template proves the template contains the strings the
 * matcher looks for. It cannot tell a Ruby file that runs from one with a
 * missing `end`, and the whole point of laying a project out in its own
 * language is that the result works in that language.
 *
 * So this writes each scaffold to a temporary directory and executes its own
 * `verify.sh`, which compiles the source, runs the tests and prints the
 * verification marker. A toolchain that is not installed is reported rather
 * than passed over in silence.
 */
class GeneratedProgramRunsTest {

    private data class Case(val prompt: String, val language: ProjectLanguage, val tool: String)

    private val cases = listOf(
        Case("a Python 3.11 tool built with pytest and pyproject.toml", ProjectLanguage.PYTHON, "python3"),
        Case("a TypeScript project using npm and tsconfig.json", ProjectLanguage.TYPESCRIPT, "node"),
        Case("a Go module with go.mod and goroutines", ProjectLanguage.GO, "go"),
        Case("a Rust crate built with cargo and Cargo.toml", ProjectLanguage.RUST, "cargo"),
        Case("a Java service with Maven and pom.xml", ProjectLanguage.JAVA, "javac"),
        Case("a Ruby gem with Gemfile and rubygems", ProjectLanguage.RUBY, "ruby"),
        Case("a PHP application with composer.json and Laravel", ProjectLanguage.PHP, "php"),
        Case("a C++ project built with CMake and CMakeLists.txt", ProjectLanguage.CPP, "cmake")
    )

    @Test
    fun every_generated_program_compiles_and_passes_its_own_verification() {
        val skipped = mutableListOf<String>()
        val ran = mutableListOf<String>()

        cases.forEach { case ->
            if (!onPath(case.tool)) {
                skipped += "${case.language.displayName} (${case.tool} not installed)"
                return@forEach
            }
            val detected = ProjectLanguage.detect(case.prompt)
            assertTrue(
                detected == ProjectLanguage.Detection.Scaffolded(case.language),
                "'${case.prompt}' detected as $detected, not ${case.language}"
            )
            val root = Files.createTempDirectory("atropos-generated-${case.language.name.lowercase()}")
            try {
                write(root, case.prompt)
                val (exitCode, output) = run(root)
                assertTrue(exitCode == 0, "${case.language.displayName} verify.sh exited $exitCode:\n$output")
                assertTrue(
                    "APP_FACTORY_VERIFY_OK" in output,
                    "${case.language.displayName} verification did not reach its marker:\n$output"
                )
                ran += case.language.displayName
            } finally {
                root.toFile().deleteRecursively()
            }
        }

        // Reported, not hidden: a green test that quietly ran nothing is the
        // failure mode this is guarding against.
        println("generated programs executed: ${ran.joinToString(", ").ifEmpty { "none" }}")
        if (skipped.isNotEmpty()) println("generated programs skipped: ${skipped.joinToString(", ")}")
        if (ran.isEmpty()) fail("no language toolchain was available, so nothing was actually verified")
    }

    private fun write(root: Path, prompt: String) {
        val files = RepoScaffold().files(
            AppProjectSpec(
                prompt = prompt,
                intent = AppIntent(name = "trackr", kind = "cli", features = listOf("report"))
            ),
            FactoryLineage(
                promptFingerprint = "fp",
                promptSha256 = "a".repeat(64),
                researchSha256 = "b".repeat(64),
                researchDocument = "",
                promptDocument = prompt,
                projectId = "p",
                confidence = FactoryConfidence(score = 90, breakdown = "", questions = emptyList()),
                promptSpans = "1"
            )
        )
        files.forEach { (relative, contents) ->
            val file = root.resolve(relative)
            file.parent.createDirectories()
            file.writeText(contents)
        }
    }

    private fun run(root: Path): Pair<Int, String> {
        val process = ProcessBuilder("sh", "verify.sh")
            .directory(root.toFile())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(240, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return -1 to "$output\n[timed out]"
        }
        return process.exitValue() to output
    }

    private fun onPath(tool: String): Boolean =
        System.getenv("PATH").orEmpty().split(':').any { directory ->
            directory.isNotEmpty() && Files.isExecutable(Path.of(directory, tool))
        }
}
