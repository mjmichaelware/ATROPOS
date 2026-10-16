/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.research

import atropos.core.confidence.ConfidenceAction
import atropos.core.confidence.ConfidenceCalculator
import atropos.core.confidence.HysteresisConfidenceEngine
import atropos.core.memory.LocalMemoryStore
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ResearchAndConfidenceTest {

    @Test
    fun `prompt artifact persists and verifies CAS hash round-trip`() {
        val tempDir = Files.createTempDirectory("prompt-test")
        val promptText = "Must implement user authentication service and dashboard feature. Cannot use plaintext passwords."
        val artifact = PromptArtifactStore.persist(promptText, "proj-123", tempDir)

        assertTrue(artifact.h0.isNotBlank())
        assertTrue(artifact.spans.isNotEmpty())
        assertTrue(artifact.spans.any { it.type == SpanType.CONSTRAINT })

        val read = PromptArtifactStore.read(artifact.h0, tempDir)
        assertNotNull(read)
        assertEquals(artifact.h0, read.h0)
        assertEquals(artifact.rawPromptText, read.rawPromptText)
    }

    @Test
    fun `soft-fail research stack records receipts for all channels`() {
        val tempDir = Files.createTempDirectory("memory-test")
        val memory = LocalMemoryStore(root = tempDir.toFile(), env = emptyMap())
        val stack = SoftFailResearchStack(memory)

        val artifact = PromptArtifact(
            h0 = "dummyh0",
            timestampUtc = java.time.Instant.now(),
            fingerprintId = "fp-1",
            projectId = "proj-123",
            rawPromptText = "Build simple cli feature",
            spans = listOf(PromptSpan("s1", "Build simple cli feature", SpanType.FEATURE))
        )

        val receipts = stack.executeStack(artifact, allowSuggestions = true)
        assertEquals(5, receipts.size)
        assertEquals(ResearchChannel.values().toSet(), receipts.map { it.channel }.toSet())
    }

    @Test
    fun `confidence calculator and hysteresis engine evaluate actions`() {
        val tempDir = Files.createTempDirectory("orch-test")
        val memory = LocalMemoryStore(root = tempDir.toFile(), env = emptyMap())
        val stack = SoftFailResearchStack(memory)
        val orchestrator = ResearchOrchestrator(stack)

        val artifact = PromptArtifact(
            h0 = "test001",
            timestampUtc = java.time.Instant.now(),
            fingerprintId = "fp-test",
            projectId = "test-proj",
            rawPromptText = "Feature: api endpoint for user profiles. Must ensure zero secret egress.",
            spans = listOf(
                PromptSpan("s1", "Feature: api endpoint for user profiles", SpanType.FEATURE),
                PromptSpan("s2", "Must ensure zero secret egress", SpanType.CONSTRAINT)
            )
        )

        val doc = orchestrator.research(artifact)
        assertTrue(doc.hDoc.isNotBlank())
        assertEquals(artifact.h0, doc.h0)
        assertTrue(doc.confidence.score in 0.0..1.0)

        val engine = HysteresisConfidenceEngine(thetaPause = 0.650, thetaSuggest = 0.850)
        val action = engine.evaluate(doc)
        assertTrue(action in setOf(ConfidenceAction.DISPATCH_WAVE_0, ConfidenceAction.INVOKE_SUGGESTION_PASS, ConfidenceAction.PARETO_DECISION_PAUSE))

        val updated = engine.updateConfidence(
            cCurrent = doc.confidence.score,
            spansTotal = 2,
            resolvedGaps = 1,
            answersCount = 1
        )
        assertTrue(updated in 0.0..0.9999)
        assertTrue(updated >= doc.confidence.score)
    }
}
