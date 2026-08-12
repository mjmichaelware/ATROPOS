/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `SUP.VERIF.PROMPT-IMMUTABILITY` and `SUP.VERIF.CONTEXT-ENVELOPE-ATTEST`:
 * a governing prompt cannot be rewritten after attestation, and context that
 * changed after sealing is a typed failure rather than a silent continue.
 */
class ContextAttestationTest {

    private fun envelope(
        repository: String = "atropos",
        branch: String = "main"
    ) = ContextEnvelope(
        repository = repository,
        repositoryRoot = "/repo",
        branch = branch,
        baselineCommit = "abc123",
        hierarchyRole = "worker",
        contextVersion = "1.0"
    )

    // ── SUP.VERIF.PROMPT-IMMUTABILITY ────────────────────────────────────

    @Test
    fun `a prompt's hash always describes its own text`() {
        val prompt = ImmutablePrompt.of("never write outside territory", PromptRole.SYSTEM)!!

        assertTrue(prompt.attests("never write outside territory"))
        assertFalse(prompt.attests("never write outside territory "))
    }

    @Test
    fun `identical text attests identically regardless of when it was loaded`() {
        val early = ImmutablePrompt.of("rule", PromptRole.AUTHORITY, loadedAt = Instant.EPOCH)!!
        val late = ImmutablePrompt.of("rule", PromptRole.AUTHORITY, loadedAt = Instant.now())!!

        assertEquals(early.ancestrySha256, late.ancestrySha256)
    }

    @Test
    fun `a blank prompt cannot be constructed`() {
        assertNull(ImmutablePrompt.of("", PromptRole.SYSTEM))
        assertNull(ImmutablePrompt.of("   \n ", PromptRole.SYSTEM))
    }

    @Test
    fun `the evidence line carries role, ancestry and source`() {
        val prompt = ImmutablePrompt.of("rule", PromptRole.AUTHORITY, sourceSha256 = "deadbeefcafe")!!

        val evidence = prompt.evidence()

        assertTrue(evidence.contains("role=authority"))
        assertTrue(evidence.contains("sha256="))
        assertTrue(evidence.contains("source=deadbeefcafe"))
    }

    @Test
    fun `an assembled prompt says so rather than claiming a source`() {
        assertTrue(ImmutablePrompt.of("rule", PromptRole.TASK)!!.evidence().contains("source=assembled"))
    }

    // ── SUP.VERIF.CONTEXT-ENVELOPE-ATTEST ────────────────────────────────

    @Test
    fun `a sealed envelope verifies unchanged`() {
        val attestor = ContextEnvelopeAttestor()
        val pack = envelope()

        val verdict = attestor.verify(pack, attestor.seal(pack))

        assertTrue(verdict.trusted)
    }

    @Test
    fun `an envelope changed after sealing is drift, not a new baseline`() {
        val attestor = ContextEnvelopeAttestor()
        val sealed = attestor.seal(envelope())

        val verdict = attestor.verify(envelope(branch = "other"), sealed)

        assertTrue(verdict is EnvelopeVerdict.Drifted)
        assertFalse(verdict.trusted)
        assertTrue(verdict.remedy.contains("Re-derive"))
    }

    @Test
    fun `a missing required field is caught before the hash is consulted`() {
        val attestor = ContextEnvelopeAttestor()
        val blank = envelope(repository = "")

        val verdict = attestor.verify(blank, attestor.seal(blank))

        assertTrue(verdict is EnvelopeVerdict.MissingFields)
        assertEquals(listOf("repository"), verdict.fields)
    }

    @Test
    fun `an envelope held too long expires rather than being trusted`() {
        var now = Instant.parse("2026-08-12T09:00:00Z")
        val attestor = ContextEnvelopeAttestor(maxAge = Duration.ofMinutes(15), clock = { now })
        val pack = envelope()
        val sealed = attestor.seal(pack)

        now = now.plus(Duration.ofMinutes(16))
        val verdict = attestor.verify(pack, sealed)

        assertTrue(verdict is EnvelopeVerdict.Expired)
        assertTrue(verdict.reason().contains("expired"))
    }

    @Test
    fun `fields that legitimately vary between uses do not read as drift`() {
        val attestor = ContextEnvelopeAttestor()
        val pack = envelope()
        val sealed = attestor.seal(pack)

        val sameContextDifferentRun = pack.copy(runId = "run-9", task = "something else")

        assertTrue(
            attestor.verify(sameContextDifferentRun, sealed).trusted,
            "a drift signal that fires on every run is one nobody reads"
        )
    }

    @Test
    fun `each failure states a different remedy`() {
        val attestor = ContextEnvelopeAttestor()
        val remedies = listOf(
            attestor.verify(envelope(repository = ""), attestor.seal(envelope(repository = ""))),
            attestor.verify(envelope(branch = "other"), attestor.seal(envelope()))
        ).map { it.reason() }

        assertEquals(remedies.size, remedies.distinct().size)
    }
}
