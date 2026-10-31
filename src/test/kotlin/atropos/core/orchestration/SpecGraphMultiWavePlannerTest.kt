/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.orchestration

import atropos.core.factory.AppIntent
import atropos.core.factory.AppProjectSpec
import atropos.core.factory.FactoryLineage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.time.Instant

class SpecGraphMultiWavePlannerTest {

    private val testPrompt = """
        ATROPOS MAXIMUM STRESS TEST: LINGUA-GRAPH ESPAÑOL
        // DIRECTIVE: You are to execute a full-scale, 100-file repository generation for an advanced Spanish language learning application named "LinguaGraph".
        // STACK ARCHITECTURE:
        - Backend: Kotlin Spring Boot (or Ktor) with strict typing.
        - Frontend: Next.js (TypeScript, React) with strict TS config.
        - Database: PostgreSQL with Prisma ORM or pure SQL DDL.
        // CORE FEATURES:
        1. Spaced Repetition Engine (SuperMemo-2 variant)
        2. Grammar Dependency Graph
        3. Complex State Management
    """.trimIndent()

    @Test
    fun `isMultiWavePrompt correctly detects LinguaGraph prompt`() {
        assertTrue(SpecGraphMultiWavePlanner.isMultiWavePrompt(testPrompt))
        assertFalse(SpecGraphMultiWavePlanner.isMultiWavePrompt("A simple CLI calculator in Python"))
    }

    @Test
    fun `buildNodes generates acyclic multi-wave DAG with expected wave stages`() {
        val tempDir = Files.createTempDirectory("test-multi-wave")
        try {
            val request = WaveExecutionRequest(
                requestId = "req-test-multiwave",
                projectSpec = AppProjectSpec(
                    prompt = testPrompt,
                    intent = AppIntent(
                        name = "linguagraph",
                        kind = "polyglot",
                        summary = "LinguaGraph application",
                        features = listOf("spaced-repetition", "grammar-graph", "offline-sync")
                    )
                ),
                lineage = FactoryLineage(
                    projectId = "proj-test",
                    promptSha256 = "sha256-test",
                    promptFingerprint = "fp-test",
                    promptSpans = "span-test",
                    researchSha256 = "res-sha256"
                ),
                targetDirectory = tempDir
            )

            val nodes = SpecGraphMultiWavePlanner.buildNodes(request)
            assertTrue(nodes.size >= 30, "Must contain full multi-wave node suite, got ${'$'}{nodes.size}")

            // Verify topological acyclicity
            val nodeMap = nodes.associateBy { it.id }
            val visited = mutableSetOf<String>()
            val stack = mutableSetOf<String>()

            fun checkAcyclic(id: String): Boolean {
                visited.add(id)
                stack.add(id)
                val node = nodeMap[id] ?: return true
                for (dep in node.dependencies) {
                    if (dep !in visited) {
                        if (!checkAcyclic(dep)) return false
                    } else if (dep in stack) {
                        return false // Cycle!
                    }
                }
                stack.remove(id)
                return true
            }

            for (node in nodes) {
                if (node.id !in visited) {
                    assertTrue(checkAcyclic(node.id), "DAG must be strictly acyclic; cycle detected at ${'$'}{node.id}")
                }
            }

            // Verify synthesis of core mathematical SM2 and Grammar Graph files
            val worktree = tempDir.resolve("worktree")
            Files.createDirectories(worktree)

            val sm2Res = SpecGraphMultiWavePlanner.synthesize("backend/src/main/kotlin/linguagraph/core/sm2/SuperMemo2Calculator.kt", worktree, request)
            assertTrue(sm2Res.success)
            assertTrue(Files.exists(worktree.resolve("backend/src/main/kotlin/linguagraph/core/sm2/SuperMemo2Calculator.kt")))

            val grammarRes = SpecGraphMultiWavePlanner.synthesize("backend/src/main/kotlin/linguagraph/core/grammar/GrammarDependencyGraph.kt", worktree, request)
            assertTrue(grammarRes.success)
            assertTrue(Files.exists(worktree.resolve("backend/src/main/kotlin/linguagraph/core/grammar/GrammarDependencyGraph.kt")))

            val ciRes = SpecGraphMultiWavePlanner.synthesize(".github/workflows/ci.yml", worktree, request)
            assertTrue(ciRes.success)
            assertTrue(Files.exists(worktree.resolve(".github/workflows/ci.yml")))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
