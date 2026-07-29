package atropos.core.agent

import atropos.core.AtroposConfig
import atropos.core.ProviderCascadeResult
import atropos.core.ProviderCascadeRouter
import atropos.core.ProviderFactory
import atropos.core.memory.LocalMemoryStore
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgentPatchCascadeRunnerTest {
    @Test
    fun refuses_valid_diff_when_provider_response_lacks_context_attestation() {
        val root = Files.createTempDirectory("atropos-agent-patch-attestation-")
        val memory = LocalMemoryStore(root.resolve(".atropos/memory").toFile(), env = emptyMap())
        val response = """
            diff --git a/src/main/kotlin/atropos/Foo.kt b/src/main/kotlin/atropos/Foo.kt
            new file mode 100644
            index 0000000..1111111
            --- /dev/null
            +++ b/src/main/kotlin/atropos/Foo.kt
            @@ -0,0 +1,2 @@
            +package atropos
            +object Foo
        """.trimIndent()

        val runner = AgentPatchCascadeRunner(
            router = ProviderCascadeRouter(ProviderFactory(AtroposConfig.load())),
            patchExtractor = AgentPatchExtractor(),
            repoRoot = root,
            memoryStore = memory,
            authorizeProvider = { _, _, _ -> },
            completeWithCascade = { requestedProvider, _, _, _, beforeAttempt ->
                beforeAttempt(requestedProvider)
                ProviderCascadeResult(requestedProvider, response, emptyList())
            }
        )

        val result = runner.run(listOf("groq"), "create foo", "repo context")

        assertEquals(null, result.success)
        val failure = assertNotNull(result.failure)
        assertEquals("context attestation failed", failure.rejectionReason)
        assertTrue(memory.search("attestation").any { it.record.title.contains("agent patch context attestation refused") })
    }
}
