/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * B-SUP-033: Formal Multi-Surface Contract Tests
 *
 * Implements contract tests that verify consistency
 * across CLI, Web, and Android surfaces.
 */
package atropos.core.supremacy

import java.nio.file.Files
import java.nio.file.Path

object SurfaceContractTests {
    sealed class Surface {
        object CLI : Surface()
        object WEB : Surface()
        object ANDROID : Surface()
    }

    data class Contract(
        val id: String,
        val description: String,
        val surfaces: Set<Surface>,
        val test: (Surface) -> Boolean
    )

    data class TestResult(
        val contractId: String,
        val surface: Surface,
        val passed: Boolean,
        val details: String?
    )

    private val contracts = mutableListOf<Contract>()
    private val results = mutableListOf<TestResult>()

    /**
     * Registers a cross-surface contract.
     */
    fun registerContract(
        id: String,
        description: String,
        surfaces: Set<Surface>,
        test: (Surface) -> Boolean
    ) {
        contracts.add(Contract(id, description, surfaces, test))
    }

    /**
     * Runs all registered contracts.
     */
    fun runAll(): List<TestResult> {
        results.clear()
        for (contract in contracts) {
            for (surface in contract.surfaces) {
                val passed = try {
                    contract.test(surface)
                } catch (e: Exception) {
                    false
                }
                val details = if (passed) null else "Test failed on $surface"
                results.add(TestResult(contract.id, surface, passed, details))
            }
        }
        return results
    }

    /**
     * Gets failed tests.
     */
    fun failedTests(): List<TestResult> = results.filter { !it.passed }

    /**
     * Persists test results to CAS.
     */
    fun persistResults(casDir: Path): Path {
        Files.createDirectories(casDir)
        val file = casDir.resolve("surface-contract-tests.json")
        val json = results.map { r ->
            """
                {
                    "contractId": "${r.contractId}",
                    "surface": "${r.surface}",
                    "passed": ${r.passed},
                    "details": "${r.details ?: ""}"
                }
            """.trimIndent()
        }.joinToString(",\n", "[\n", "\n]")
        Files.writeString(file, json)
        return file
    }

    /**
     * CLI command to run contract tests.
     */
    fun runAndShow(): String {
        runAll()
        val passed = results.count { it.passed }
        val total = results.size
        return "Surface contract tests: $passed/$total passed\n${failedTests().joinToString("\n") { "  FAIL: ${it.contractId} on ${it.surface} - ${it.details}" }}"
    }
}