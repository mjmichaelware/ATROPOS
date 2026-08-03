package atropos.core.agent

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentAskAnswerNormalizerTest {

    private val normalizer = AgentAskAnswerNormalizer()

    private fun snapshot(byteCount: Int = 4096) = AgentContextSnapshot(
        repoRoot = Path.of("/repo"),
        text = "context",
        byteCount = byteCount,
        truncated = false
    )

    @Test
    fun `a real answer passes through trimmed`() {
        assertEquals(
            "The router picks the first healthy provider.",
            normalizer.normalize("  The router picks the first healthy provider.  ")
        )
    }

    @Test
    fun `a response reciting the context pack is replaced`() {
        val echoed = "Here is what I see:\n# Repo Root\n/repo\n# Git Status\n M src/Main.kt"
        val normalized = normalizer.normalize(echoed)

        assertFalse(normalized.contains("# Git Status"), "recited context must not reach the operator")
        assertTrue(normalized.startsWith("Yes. ATROPOS supplied bounded repo context"))
    }

    @Test
    fun `each context header is recognised on its own`() {
        listOf("\n# Repo Root", "\n# Git Status", "\n# Selected Sources", "Repository context:")
            .forEach { marker ->
                assertTrue(
                    normalizer.normalize("prefix$marker suffix").startsWith("Yes. ATROPOS supplied bounded"),
                    "$marker should be recognised as a context echo"
                )
            }
    }

    @Test
    fun `merely mentioning the repository is not an echo`() {
        val answer = "The repo root is resolved by AtroposRepoRootLocator, and git status is collected."
        assertEquals(
            answer,
            normalizer.normalize(answer),
            "detection is narrow on purpose: a false positive discards a real answer"
        )
    }

    @Test
    fun `the fallback answer names the task and the context size`() {
        val fallback = normalizer.fallbackAnswer("explain the router", snapshot(byteCount = 8192))
        assertTrue(fallback.contains("Task: explain the router"))
        assertTrue(fallback.contains("Context bytes: 8192"))
    }

    @Test
    fun `a blank task is labelled rather than left empty`() {
        assertTrue(normalizer.fallbackAnswer("   ", snapshot()).contains("(blank task)"))
    }

    @Test
    fun `the fallback states plainly that there is no filesystem access`() {
        assertTrue(normalizer.fallbackAnswer("anything", snapshot()).contains("no direct filesystem access"))
    }
}
