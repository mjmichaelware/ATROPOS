/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.projection

import atropos.core.auth.AttestationResult
import atropos.core.auth.AuthorityDocument
import atropos.core.auth.CascadeResolution
import kotlin.test.Test
import kotlin.test.assertTrue

class AuthorityProjectionTest {

    private val agents = AuthorityDocument("AGENTS.md", "a".repeat(64), precedenceRank = 0)
    private val settings = AuthorityDocument("settings.json", "b".repeat(64), precedenceRank = 2)

    @Test
    fun `attested documents with no violation resolve`() {
        val json = AuthorityProjection().render(
            listOf(AttestationResult.Attested(agents), AttestationResult.Attested(settings)),
            listOf(CascadeResolution.Resolved("humanAuthority", "required", "AGENTS.md", final = true))
        )

        assertTrue(json.contains("\"resolved\":true"))
        // The strongest document names the source.
        assertTrue(json.contains("\"source\":\"AGENTS.md\""))
        assertTrue(json.contains("\"nonOverridable\":true"))
    }

    @Test
    fun `attesting nothing does not resolve`() {
        // Absence of a grant is never permission: a runtime that attested
        // nothing must not read as one operating under intact authority.
        val json = AuthorityProjection().render(emptyList(), emptyList())

        assertTrue(json.contains("\"resolved\":false"))
        assertTrue(json.contains("\"source\":null"))
    }

    @Test
    fun `a mutated document does not resolve and says why`() {
        val json = AuthorityProjection().render(
            listOf(AttestationResult.Mismatch("AGENTS.md", "a".repeat(64), "c".repeat(64))),
            emptyList()
        )

        assertTrue(json.contains("\"resolved\":false"))
        assertTrue(json.contains("\"state\":\"mismatch\""))
        assertTrue(json.contains("changed since it was recorded"))
    }

    @Test
    fun `a missing document does not resolve`() {
        val json = AuthorityProjection().render(
            listOf(AttestationResult.Missing("AGENTS.md")),
            emptyList()
        )

        assertTrue(json.contains("\"resolved\":false"))
        assertTrue(json.contains("\"state\":\"missing\""))
    }

    @Test
    fun `a core-key override blocks resolution and names the key`() {
        // A count would tell the operator something is being overridden without
        // telling them which invariant — the only part they can act on.
        val json = AuthorityProjection().render(
            listOf(AttestationResult.Attested(agents)),
            listOf(
                CascadeResolution.Violation(
                    key = "freeSpaceGate",
                    heldBy = "AGENTS.md",
                    attemptedBy = listOf("settings.json"),
                    reason = "freeSpaceGate is a core invariant and cannot be overridden by settings.json"
                )
            )
        )

        assertTrue(json.contains("\"resolved\":false"))
        assertTrue(json.contains("\"key\":\"freeSpaceGate\""))
        assertTrue(json.contains("settings.json"))
    }

    @Test
    fun `an undefined key is not a violation`() {
        val json = AuthorityProjection().render(
            listOf(AttestationResult.Attested(agents)),
            listOf(CascadeResolution.Undefined("somethingElse"))
        )

        assertTrue(json.contains("\"resolved\":true"))
        assertTrue(json.contains("\"violations\":[]"))
    }
}
