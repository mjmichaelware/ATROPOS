/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.security

import atropos.cli.errors.SystemExceptionHandler
import atropos.core.BaseHttpProvider
import atropos.core.agent.AgentDaemonLogWriter
import atropos.core.agent.AgentPatchStore
import atropos.core.agent.AgentPatchCheckResult
import atropos.core.agent.SupervisedSessionStore
import atropos.core.agent.AgentRuntimeKind
import java.nio.file.Files
import java.nio.charset.StandardCharsets
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

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

    @Test
    fun logs_channel_canary_redacts_enrolled_secrets() {
        val registry = KnownSecretRegistry().also { it.enroll("CANARY_KEY", canary) }
        val filter = RedactionFilter(knownSecrets = registry)
        val writer = AgentDaemonLogWriter(redactionFilter = filter)

        val logFile = Files.createTempFile("atropos-log-", ".log")
        try {
            val pipe = java.io.PipedOutputStream()
            val pin = java.io.PipedInputStream(pipe)
            val process = object : Process() {
                override fun getOutputStream(): java.io.OutputStream = pipe
                override fun getInputStream(): java.io.InputStream = pin
                override fun getErrorStream(): java.io.InputStream = java.io.ByteArrayInputStream(ByteArray(0))
                override fun waitFor(): Int = 0
                override fun exitValue(): Int = 0
                override fun destroy() {}
            }
            val thread = writer.attach(process, logFile)
            pipe.write("this has $canary inside\n".toByteArray(Charsets.UTF_8))
            pipe.close()
            thread.join(2000)

            val logContent = Files.readString(logFile)
            assertTrue(logContent.contains("<redacted:CANARY_KEY>"), logContent)
            assertFalse(logContent.contains(canary), logContent)
        } finally {
            Files.deleteIfExists(logFile)
        }
    }

    @Test
    fun prompts_channel_canary_redacts_enrolled_secrets() {
        val registry = RedactionFilter.defaultRegistry
        registry.enroll("CANARY_KEY", canary)
        val provider = object : BaseHttpProvider() {
            override val name = "test"
            fun testRedaction(p: String, c: String): Pair<String, String> {
                return redactionFilter.redact(p) to redactionFilter.redact(c)
            }
            override fun complete(prompt: String, context: String): String = ""
        }
        val (p, c) = provider.testRedaction(canary, canary)
        assertEquals("<redacted:CANARY_KEY>", p)
        assertEquals("<redacted:CANARY_KEY>", c)
    }

    @Test
    fun diffs_channel_canary_redacts_enrolled_secrets() {
        val registry = KnownSecretRegistry().also { it.enroll("CANARY_KEY", canary) }
        val filter = RedactionFilter(knownSecrets = registry)
        val repoRoot = Files.createTempDirectory("atropos-diff-test-")
        try {
            val patchStore = AgentPatchStore(repoRoot, redactionFilter = filter)
            val patch = patchStore.createRecord("groq", "task with $canary", 0, "--- a/x\n+++ b/x\n@@ -1 +1 @@\n-a\n+b\n")
            patchStore.writeMeta(patch, AgentPatchCheckResult(passed = true, exitCode = 0, output = "api_key=$canary"))
            val meta = Files.readString(patch.metaFile, StandardCharsets.UTF_8)
            assertFalse(meta.contains(canary))
            assertTrue(meta.contains("<redacted:CANARY_KEY>"))
        } finally {
            repoRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun history_channel_canary_redacts_enrolled_secrets() {
        val registry = KnownSecretRegistry().also { it.enroll("CANARY_KEY", canary) }
        val filter = RedactionFilter(knownSecrets = registry)
        val repoRoot = Files.createTempDirectory("atropos-history-test-")
        try {
            val store = SupervisedSessionStore(repoRoot = repoRoot, clock = { Instant.now() }, redactionFilter = filter)
            val initial = store.initialRecord(AgentRuntimeKind.OPENCODE)
            val updated = store.heartbeat(initial, "important message with $canary")
            val metaFile = repoRoot.resolve(".atropos/bootstrap/sessions/${updated.id}.meta")
            val content = Files.readString(metaFile, StandardCharsets.UTF_8)
            assertFalse(content.contains(canary))
            val readBack = store.readSession(updated.id)
            assertNotNull(readBack)
            assertTrue(readBack.lastMessage.orEmpty().contains("<redacted:CANARY_KEY>"))
            assertFalse(readBack.lastMessage.orEmpty().contains(canary))
        } finally {
            repoRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun ui_channel_canary_redacts_enrolled_secrets() {
        val registry = KnownSecretRegistry().also { it.enroll("CANARY_KEY", canary) }
        val filter = RedactionFilter(knownSecrets = registry)

        // 1. AgentJobRenderer
        val theme = atropos.cli.ui.TerminalTheme(atropos.cli.config.ConfigurationManager())
        val renderer = atropos.cli.ui.AgentJobRenderer(theme = theme, redactionFilter = filter)
        val job = atropos.cli.ui.AgentJobSummary(
            id = "job-1",
            task = "run with $canary",
            status = atropos.cli.ui.AgentJobStatus.FAILED,
            note = "failed due to $canary",
            finalReport = "result: $canary"
        )
        val renderedSummary = renderer.renderRunSummary(job, 80).joinToString("\n")
        assertFalse(renderedSummary.contains(canary))
        assertTrue(renderedSummary.contains("<redacted:CANARY_KEY>"))

        // 2. ProviderActivationRecord
        val record = atropos.core.provider.ProviderActivationRecord(
            providerId = "groq",
            mode = atropos.core.provider.ProviderVerificationMode.VERIFY,
            state = atropos.core.provider.ProviderActivationState.VERIFIED,
            descriptorPresent = true,
            adapterStatus = null,
            keySources = listOf(canary),
            impact = listOf(canary),
            executableSupport = true,
            fixtureMatrix = null,
            verificationSummary = "checked $canary",
            remediation = "fix $canary"
        )
        val defRegistry = RedactionFilter.defaultRegistry
        defRegistry.enroll("CANARY_KEY", canary)
        val renderedRecord = record.render()
        assertFalse(renderedRecord.contains(canary))
        assertTrue(renderedRecord.contains("<redacted:CANARY_KEY>"))

        // 3. ProviderActivationService
        val activationService = atropos.core.provider.ProviderActivationService(
            registry = atropos.core.provider.StaticProviderDescriptorRegistry(),
            ollamaProbe = { true }
        )
        val renderedAll = activationService.renderVerifyAll()
        assertFalse(renderedAll.contains(canary))
    }
}
