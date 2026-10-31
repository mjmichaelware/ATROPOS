/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import atropos.core.AIProvider
import atropos.core.ProviderFactory
import atropos.core.dopamine.ProviderDopamineRewardTracker
import atropos.core.factory.ProjectLanguage
import atropos.core.repair.AutonomousAstHealingLoop
import atropos.core.repair.DiagnosticSlice
import atropos.core.supremacy.CapabilityAttenuationSandbox
import atropos.core.synthesis.PayloadLanguageMismatchException
import atropos.core.synthesis.PreParserInterceptor
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DynamicDeterministicRoutingTest {

    @Test
    fun small_models_are_filtered_out_immediately_when_payload_requires_large_context() {
        val registry = StaticProviderDescriptorRegistry()
        // 100-file repository payload requiring 100,000 tokens
        val largePayloadTokens = 100_000

        val candidates = listOf("gemini", "groq", "cerebras", "deepseek", "openrouter", "deepinfra")
        // groq (131072), deepseek (131072), openrouter (131072), gemini (1048576) fit
        // deepinfra (32768) does not fit
        val ranked = FreeProviderCapabilityRanker.rankFreeFirst(candidates, largePayloadTokens, registry)

        assertTrue(ranked.isNotEmpty())
        assertFalse(ranked.contains("deepinfra"), "small model deepinfra must be filtered out immediately")
        assertTrue(ranked.contains("deepseek"), "capable model deepseek must be retained")
        assertTrue(ranked.contains("openrouter"), "capable model openrouter must be retained")
    }

    @Test
    fun historical_dopamine_score_promotes_most_reliable_provider_to_first() {
        val tracker = ProviderDopamineRewardTracker.SHARED
        tracker.recordSuccess("deepseek", "repair.ast", 10.0, "clean AST patch")
        tracker.recordFailure("groq", "repair.ast", "AST_SYNTAX_OR_PATCH_FAILURE", "syntax mistake", -5.0)

        val candidates = listOf("groq", "deepseek", "openrouter")
        val ranked = FreeProviderCapabilityRanker.rankFreeFirst(candidates, 1000, StaticProviderDescriptorRegistry(), tracker)

        assertEquals("deepseek", ranked.first(), "deepseek with highest dopamine score must be routed first")
    }

    @Test
    fun hallucination_interceptor_rejects_python_file_in_typescript_project() {
        val pythonPayload = """
            --- FILE: unknown_file.py ---
            def fix_package_json():
                pass
            --- END FILE ---
        """.trimIndent()

        val ex = assertFailsWith<PayloadLanguageMismatchException> {
            PreParserInterceptor.validate(
                rawResponse = pythonPayload,
                language = ProjectLanguage.TYPESCRIPT,
                provider = "gemini-test"
            )
        }

        assertTrue(ex.message.contains("violates active TypeScript project layout"))
        assertEquals("py", ex.detectedExtension)
    }

    @Test
    fun hallucination_interceptor_accepts_valid_typescript_and_package_json() {
        val tsPayload = """
            --- FILE: package.json ---
            {
              "name": "my-app",
              "scripts": { "test": "node --test" }
            }
            --- END FILE ---
            --- FILE: src/index.ts ---
            export const greet = (name: string): string => `Hello, ${'$'}{name}`;
            --- END FILE ---
        """.trimIndent()

        // Should not throw
        PreParserInterceptor.validate(
            rawResponse = tsPayload,
            language = ProjectLanguage.TYPESCRIPT,
            provider = "deepseek"
        )
    }

    @Test
    fun graceful_strike_failover_in_healing_loop_does_not_crash_cli() {
        val attempts = mutableListOf<String>()
        val router = ProviderCascadeRouter(
            factory = ProviderFactory(),
            providerResolver = { provider ->
                attempts += provider
                object : AIProvider {
                    override val name: String = provider
                    override fun complete(prompt: String, context: String): String = when (provider) {
                        "gemini-hallucinating" -> "--- FILE: unknown_file.py ---\ndef broken(): pass\n--- END FILE ---"
                        else -> "--- FILE: package.json ---\n{\n  \"name\": \"fixed\"\n}\n--- END FILE ---"
                    }
                }
            }
        )

        val loop = AutonomousAstHealingLoop(Path.of("."))
        val slice = DiagnosticSlice("package.json", 1, 1, "ERR", "Missing package.json", "")

        val result = loop.healWithGracefulFailover(
            slice = slice,
            currentCode = "{}",
            language = ProjectLanguage.TYPESCRIPT,
            cascadeRouter = router,
            initialProvider = "gemini-hallucinating"
        )

        // Gemini failed pre-parser interception strikes, and cascaded without throwing IllegalStateException
        assertTrue(result.diagnosticResolved)
        assertTrue(result.repairedDiff?.contains("fixed") == true)
    }

    @Test
    fun sandbox_provisioner_initializes_git_and_scaffolds_package_json_for_typescript() {
        val tempDir = createTempDirectory("sandbox-prov-test")
        try {
            val sandbox = CapabilityAttenuationSandbox("test-secret".toByteArray())
            sandbox.provisionIsolatedWorktree(tempDir, ProjectLanguage.TYPESCRIPT)

            assertTrue(Files.exists(tempDir.resolve("package.json")), "package.json must be scaffolded")
            assertTrue(Files.exists(tempDir.resolve("tsconfig.json")), "tsconfig.json must be scaffolded")
            assertTrue(Files.exists(tempDir.resolve(".git")), ".git must be initialized explicitly")
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
