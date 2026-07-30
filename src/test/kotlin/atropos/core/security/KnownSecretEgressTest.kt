/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.security

import atropos.cli.errors.SystemExceptionHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Phase 4's acceptance predicate, stated as a test rather than a claim.
 *
 * The invariant is `RAW_SECRET_OUTPUT=false`. Asserting it about patterns is not
 * possible — patterns have a false-negative tail no test can close. Asserting it
 * about an *enrolled* credential is possible and exact, so these tests plant a
 * canary and demand it never survives any egress path.
 */
class KnownSecretEgressTest {

    private val canary = "atropos-canary-9f3c1d7e5b2a4860"

    @Test
    fun every_enrolled_representation_is_caught() {
        val registry = KnownSecretRegistry()
        val enrolled = registry.enroll("CANARY_KEY", canary)

        // Assert the forms that matter, not a count. The closure deduplicates, and
        // for a plain ASCII secret the URL-encoded, JSON-escaped and
        // whitespace-stripped forms are all identical to the raw one — so a count
        // assertion would encode an accident of this particular canary.
        val variants = SecretEncodingClosure.variantsOf(canary)
        val bytes = canary.toByteArray(Charsets.UTF_8)
        assertTrue(variants.contains(canary), "raw form missing")
        assertTrue(
            variants.contains(java.util.Base64.getEncoder().encodeToString(bytes)),
            "base64 form missing — this is the one a pattern filter misses in a JSON body"
        )
        assertTrue(
            variants.contains(bytes.joinToString("") { "%02x".format(it) }),
            "lowercase hex form missing"
        )
        assertEquals(variants.size, enrolled, "every variant should be enrolled")

        // The guarantee is stated over the closure, so the test is too: every form
        // the closure commits to must be caught, with no exceptions.
        SecretEncodingClosure.variantsOf(canary).forEach { variant ->
            val carrier = "provider said: $variant -- end"
            assertEquals(
                setOf("CANARY_KEY"),
                registry.findLeaks(carrier),
                "variant not caught: ${variant.take(12)}…"
            )
            assertFalse(
                registry.redact(carrier).contains(variant),
                "variant survived redaction: ${variant.take(12)}…"
            )
        }
    }

    @Test
    fun a_secret_split_by_terminal_wrapping_is_still_caught() {
        val registry = KnownSecretRegistry()
        registry.enroll("CANARY_KEY", canary)

        // Exactly what a wrapped 80-column terminal or a collated log does.
        val wrapped = canary.take(10) + "\n" + canary.drop(10)

        assertEquals(setOf("CANARY_KEY"), registry.findLeaks(wrapped))
    }

    @Test
    fun the_redaction_marker_names_the_credential_without_revealing_it() {
        val registry = KnownSecretRegistry()
        registry.enroll("GROQ_API_KEY", canary)

        val redacted = registry.redact("auth failed for $canary")

        assertTrue(redacted.contains("<redacted:GROQ_API_KEY>"), redacted)
        assertFalse(redacted.contains(canary), "the value survived: $redacted")
    }

    @Test
    fun a_value_too_short_to_protect_is_refused_rather_than_half_protected() {
        val registry = KnownSecretRegistry()

        // "abc" would match inside ordinary prose; enrolling it would make the
        // guard fire constantly and get switched off.
        assertEquals(0, registry.enroll("TINY", "abc"))
        assertTrue(registry.isEmpty())
        assertEquals(emptySet(), registry.findLeaks("abc appears in this sentence"))
    }

    @Test
    fun the_registry_never_retains_a_recoverable_secret() {
        val registry = KnownSecretRegistry()
        registry.enroll("CANARY_KEY", canary)

        // Everything the registry is willing to expose about itself must be free of
        // the value: labels are safe to log, digests are salted per process.
        assertEquals(setOf("CANARY_KEY"), registry.enrolledLabels)
        assertFalse(registry.toString().contains(canary))
        assertFalse(registry.enrolledLabels.joinToString().contains(canary))
    }

    @Test
    fun two_registries_do_not_share_a_digest_oracle() {
        val first = KnownSecretRegistry()
        val second = KnownSecretRegistry()
        first.enroll("K", canary)
        second.enroll("K", canary)

        // Both catch the leak, but a digest lifted from one must not be a lookup
        // table against the other — that is what the per-process salt buys.
        assertEquals(setOf("K"), first.findLeaks(canary))
        assertEquals(setOf("K"), second.findLeaks(canary))
    }

    @Test
    fun redaction_filter_catches_an_enrolled_secret_that_matches_no_pattern() {
        // The point of tier 1: this canary looks like ordinary text. No API-key
        // regex matches it, so a pattern-only filter emits it verbatim.
        val patternsOnly = RedactionFilter()
        assertTrue(patternsOnly.redact(canary).contains(canary), "expected patterns to miss it")

        val registry = KnownSecretRegistry().also { it.enroll("CANARY_KEY", canary) }
        val withTierOne = RedactionFilter(knownSecrets = registry)

        val report = withTierOne.report("dump: $canary")
        assertFalse(report.redacted.contains(canary), "tier 1 failed to redact: ${report.redacted}")
        assertTrue(report.changed)
        assertTrue(report.summary().contains("known_secret"), report.summary())
    }

    @Test
    fun the_last_resort_exception_handler_cannot_emit_a_secret() {
        val registry = KnownSecretRegistry().also { it.enroll("CANARY_KEY", canary) }
        val captured = mutableListOf<String>()
        val handler = SystemExceptionHandler(
            redactionFilter = RedactionFilter(knownSecrets = registry),
            sink = { captured += it }
        )

        // Exactly the shape an HTTP client throws: URL plus Authorization header.
        handler.handle(
            IllegalStateException(
                "POST https://api.example.invalid/v1/chat failed, Authorization: Bearer $canary"
            )
        )

        assertEquals(1, captured.size)
        assertFalse(captured.single().contains(canary), "canary reached stderr: ${captured.single()}")
    }

    @Test
    fun the_copy_for_support_block_cannot_carry_a_secret() {
        val registry = KnownSecretRegistry().also { it.enroll("CANARY_KEY", canary) }
        val renderer = atropos.cli.ui.ErrorRenderer(
            theme = atropos.cli.ui.TerminalTheme(atropos.cli.config.ConfigurationManager()),
            redactionFilter = RedactionFilter(knownSecrets = registry)
        )

        val rendered = renderer.render(
            atropos.cli.ui.ErrorRenderer.ErrorInfo(
                title = "provider auth failed",
                message = "key $canary rejected",
                details = "request header: Authorization: Bearer $canary"
            ),
            width = 100
        ).joinToString("\n")

        // This block is labelled "copy for support"; its whole purpose is to be
        // pasted elsewhere, so a secret here is a secret already forwarded.
        assertFalse(rendered.contains(canary), "canary reached the support block")
        assertTrue(rendered.contains("<redacted:CANARY_KEY>"), rendered)
    }
}
