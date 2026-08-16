/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.nl

import atropos.core.ingest.AtMentionScanner
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `SUP.NL.*` and `SUP.ART.AT-MENTION-UPLOAD`.
 *
 * Source Doc 5 asks whether messy phone input needs a trained model. These
 * assert the answer is no: the defects that actually arrive are encoding
 * defects with one correct answer each, and a deterministic pass handles them
 * reproducibly — which a model could not do, because the same dirty string
 * would not always clean the same way.
 */
class NlEntryPipelineTest {

    private val canonicalizer = NlCanonicalizer()

    // ── SUP.NL.BYTE-CANONICAL-FORM ───────────────────────────────────────

    @Test
    fun `swipe keyboard doubled spaces collapse`() {
        val result = canonicalizer.canonicalize("build  the   thing   please")

        assertEquals("build the thing please", result.canonical)
        assertTrue(result.changed)
    }

    @Test
    fun `autocorrect smart quotes fold to plain ones`() {
        val result = canonicalizer.canonicalize("run “the build” now")

        assertEquals("run \"the build\" now", result.canonical)
    }

    @Test
    fun `invisible characters left by emoji and pastes are removed`() {
        val result = canonicalizer.canonicalize("sta​tus‍ of the build")

        assertEquals("status of the build", result.canonical)
        assertTrue(result.notes.any { it.contains("invisible") })
    }

    @Test
    fun `ligatures and full-width characters normalize`() {
        assertEquals("file", canonicalizer.canonicalize("ﬁle").canonical)
        assertEquals("status", canonicalizer.canonicalize("ｓtatus").canonical)
    }

    @Test
    fun `spelling is never corrected`() {
        val result = canonicalizer.canonicalize("atropos buld the thing")

        assertEquals(
            "atropos buld the thing", result.canonical,
            "a canonicalizer that guessed at words would change what was asked"
        )
    }

    @Test
    fun `paragraph structure survives but blank runs collapse`() {
        val result = canonicalizer.canonicalize("first line\n\n\n\nsecond line")

        assertEquals("first line\n\nsecond line", result.canonical)
    }

    @Test
    fun `canonicalizing a canonical string changes nothing`() {
        val once = canonicalizer.canonicalize("run  “the”  build​ now")
        val twice = canonicalizer.canonicalize(once.canonical)

        assertEquals(once.canonical, twice.canonical)
        assertFalse(twice.changed, "the pass must be idempotent or no hash of it means anything")
    }

    @Test
    fun `input that was entirely invisible is unrecoverable, not merely empty`() {
        val result = canonicalizer.canonicalize("​‍⁠")

        assertFalse(result.recoverable)
    }

    @Test
    fun `oversized input is bounded before any per-character work`() {
        val result = NlCanonicalizer(maxChars = 100).canonicalize("x".repeat(10_000))

        assertEquals(100, result.canonical.length)
        assertTrue(result.notes.any { it.contains("truncated") })
    }

    // ── SUP.NL.ENVELOPE-WRAP ─────────────────────────────────────────────

    @Test
    fun `the envelope keeps both hashes so a canonicalizer bug stays detectable`() {
        val wrapped = NlEnvelopeWrapper().wrap(
            canonicalizer.canonicalize("build  the thing"),
            NlSource.CLI_PROMPT
        )

        assertTrue(wrapped.wasTransformed)
        assertFalse(wrapped.canonicalSha256 == wrapped.originalSha256)
        assertTrue(wrapped.evidence().contains("source=cli"))
    }

    @Test
    fun `the original text is never carried, only its hash`() {
        val wrapped = NlEnvelopeWrapper().wrap(
            canonicalizer.canonicalize("token sk-ant-api03-SECRETSECRETSECRET  here"),
            NlSource.BRIDGE_MESSAGE
        )

        assertFalse(wrapped.evidence().contains("SECRET"))
    }

    @Test
    fun `accepted entry exposes the canonical typed intent envelope`() {
        val entry = NlEntryPipeline(emptyList()).accept("/status", NlSource.CLI_PROMPT)

        assertEquals("/status", entry.intentEnvelope.command)
        assertEquals(entry.envelope.canonicalSha256, entry.intentEnvelope.intentId)
        assertTrue(entry.intentEnvelope.parsedOk)
    }

    // ── SUP.NL.LOCAL-MEMORY-LOOKUP ───────────────────────────────────────

    private fun resolve(text: String, commands: List<String>): NlResolution =
        LocalNlResolver(knownCommands = { commands }).resolve(
            NlEnvelopeWrapper().wrap(canonicalizer.canonicalize(text), NlSource.CLI_PROMPT)
        )

    @Test
    fun `an exact command never reaches a provider`() {
        val resolution = resolve("/status", listOf("/status", "/help"))

        assertTrue(resolution is NlResolution.ExactCommand)
        assertEquals("/status", resolution.command)
        assertTrue(resolution.resolvedLocally)
    }

    @Test
    fun `a command typed without its slash still resolves locally`() {
        val resolution = resolve("status", listOf("/status"))

        assertTrue(resolution is NlResolution.ExactCommand)
        assertEquals("/status", resolution.command)
    }

    @Test
    fun `a misspelling is proposed, never executed`() {
        val resolution = resolve("staus", listOf("/status", "/providers"))

        assertTrue(resolution is NlResolution.Suggested)
        assertEquals("/status", resolution.command)
        assertTrue(resolution.render().endsWith("run it?"))
    }

    @Test
    fun `a sentence that starts near a command is prose, not a command`() {
        val resolution = resolve("status of the build please", listOf("/status"))

        assertTrue(
            resolution is NlResolution.Prose,
            "matching a question to a command answers something nobody asked"
        )
    }

    @Test
    fun `genuine prose is the only thing that needs a provider`() {
        val resolution = resolve("write me a todo app with a dark theme", listOf("/status"))

        assertTrue(resolution is NlResolution.Prose)
        assertFalse(resolution.resolvedLocally)
    }

    // ── SUP.ART.AT-MENTION-UPLOAD ────────────────────────────────────────

    @Test
    fun `mentions are found and email addresses are not`() {
        val found = AtMentionScanner.scan("summarise @docs/spec.pdf and mail me@example.com")

        assertEquals(listOf("docs/spec.pdf"), found)
    }

    @Test
    fun `a mention without an extension is not a file reference`() {
        assertTrue(AtMentionScanner.scan("ask @someone about it").isEmpty())
    }

    @Test
    fun `an attached file inside territory is ingested and named back`() {
        val root = Files.createTempDirectory("atropos-nl-test")
        Files.createDirectories(root.resolve("docs"))
        Files.writeString(root.resolve("docs/spec.txt"), "the spec")

        val entry = NlEntryPipeline(listOf(root)).accept("summarise @docs/spec.txt", NlSource.CLI_PROMPT)

        assertEquals(listOf<Path>(root.resolve("docs/spec.txt")), entry.attachments)
        assertTrue(entry.notice()!!.contains("attached: spec.txt"))
    }

    @Test
    fun `a mention escaping territory is refused and the refusal is reported`() {
        val root = Files.createTempDirectory("atropos-nl-test")

        val entry = NlEntryPipeline(listOf(root)).accept("read @../../.ssh/id_rsa.txt", NlSource.CLI_PROMPT)

        assertTrue(entry.attachments.isEmpty())
        assertEquals(1, entry.refusedAttachments.size)
        assertTrue(
            entry.notice()!!.contains("not attached"),
            "silence would let the operator believe the file was read"
        )
    }

    @Test
    fun `a disallowed file type is refused by type, not by territory`() {
        val root = Files.createTempDirectory("atropos-nl-test")
        Files.writeString(root.resolve("payload.exe"), "x")

        val entry = NlEntryPipeline(listOf(root)).accept("run @payload.exe", NlSource.CLI_PROMPT)

        assertTrue(entry.attachments.isEmpty())
        assertTrue(entry.refusedAttachments.single().contains("not ingestible"))
    }

    @Test
    fun `an oversized attachment is refused by the ingest ceiling`() {
        val root = Files.createTempDirectory("atropos-nl-test")
        Files.writeString(root.resolve("big.txt"), "x")

        val entry = NlEntryPipeline(
            territoryRoots = listOf(root),
            sizeOf = { 64L * 1024 * 1024 }
        ).accept("summarise @big.txt", NlSource.CLI_PROMPT)

        assertTrue(entry.attachments.isEmpty())
        assertTrue(entry.refusedAttachments.single().contains("ingest ceiling"))
    }

    @Test
    fun `a mention split by an invisible character is still found`() {
        val root = Files.createTempDirectory("atropos-nl-test")
        Files.createDirectories(root.resolve("docs"))
        Files.writeString(root.resolve("docs/spec.txt"), "the spec")

        val entry = NlEntryPipeline(listOf(root))
            .accept("read @docs/spec​.txt", NlSource.ANDROID_COMPOSER)

        assertEquals(1, entry.attachments.size)
    }
}
