/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.nl

import atropos.core.ingest.AtMentionScanner
import atropos.core.ingest.AttachmentReader
import atropos.core.ingest.IngestedAttachment
import atropos.core.ingest.MentionResolution
import atropos.core.ingest.MentionResolver
import atropos.core.intent.MentionExtractor
import java.nio.file.Files
import java.nio.file.Path

/**
 * The single front door for natural language, whatever surface it came from.
 *
 * `SUP.NL.BYTE-CANONICAL-FORM` requires canonicalization to be inserted "as
 * first stage of any NL entry point", and `SUP.NL.ENVELOPE-WRAP` requires only
 * the envelope to travel onward. One pipeline is how those two hold: three
 * entry points each doing their own ordering would eventually disagree, and
 * the one that skipped a stage would be the one an attacker used.
 *
 * The order is fixed and each step earns its position:
 *
 * 1. **Canonicalize** first, because everything after it — hashing, mention
 *    scanning, command matching — is comparing bytes, and comparing
 *    un-normalized bytes gives different answers for text that looks identical.
 * 2. **Scan for mentions** on the canonical text, so a mention split by a
 *    zero-width character is still found.
 * 3. **Resolve mentions** against territory, then read what they name.
 *    Refused mentions are reported, not dropped: an operator who attached a
 *    file and got no acknowledgement assumes it was read. So is a resolved one
 *    whose bytes never reach the prompt, which is the worse failure — it
 *    reports success and delivers nothing.
 * 4. **Wrap** into an envelope, so provenance travels with the request.
 * 5. **Resolve locally**, so a provider is consulted only when nothing cheaper
 *    could answer.
 */
class NlEntryPipeline(
    private val territoryRoots: List<Path>,
    private val canonicalizer: NlCanonicalizer = NlCanonicalizer(),
    private val wrapper: NlEnvelopeWrapper = NlEnvelopeWrapper(),
    private val resolver: LocalNlResolver = LocalNlResolver(),
    private val mentions: MentionResolver = MentionResolver(territoryRoots),
    private val attachmentReader: AttachmentReader = AttachmentReader(),
    /**
     * Recognises a bare `@name` as a granted territory.
     *
     * A different question from an attachment, and the reason the two owners
     * exist: `@docs/spec.txt` names a file, `@atropos` names a place. Built
     * from the granted roots' own directory names, so what the operator may
     * refer to is exactly what they were granted — nothing here widens
     * territory, it only reads it back.
     */
    private val territoryNames: MentionExtractor = MentionExtractor(
        territoryRoots.mapNotNull { it.fileName?.toString() }.toSet()
    ),
    /** Injected so the pipeline can be tested without a filesystem. */
    private val sizeOf: (Path) -> Long = { path ->
        runCatching { Files.size(path) }.getOrDefault(-1L)
    }
) {
    /**
     * Resolves the mentions in [raw] and substitutes what they name.
     *
     * For callers that are not asking a question — a `/factory run` whose
     * prompt *is* the document, rather than a request about it. Those go
     * through the command router, which never reached [accept], so
     * `/factory run implement @spec.md` took the literal path string as its
     * prompt and named the generated app after a directory.
     *
     * The text is substituted raw, without [IngestedAttachment.promptBlock]'s
     * fences. Fences exist to keep a document from being read as the operator's
     * request when both are sent to a model; here the document *is* the
     * request, and the fence markers would end up in extracted requirements.
     *
     * Shares [MentionResolver] and [AttachmentReader] with [accept]. A second
     * resolution path would be a second answer to "may this file be read".
     */
    fun expandMentions(raw: String): MentionExpansion {
        val attachments = mutableListOf<IngestedAttachment>()
        val refused = mutableListOf<String>()
        var expanded = raw

        AtMentionScanner.scan(raw).forEach { mention ->
            when (val ingested = ingest(mention, attachments, refused)) {
                null -> Unit
                else -> if (ingested.text != null) {
                    expanded = expanded.replace("@$mention", ingested.text)
                }
            }
        }
        return MentionExpansion(expanded, attachments, refused)
    }

    /**
     * Resolves one mention, recording it as attached or refused.
     *
     * @return the attachment when it was read, null when it was refused.
     */
    private fun ingest(
        mention: String,
        attachments: MutableList<IngestedAttachment>,
        refused: MutableList<String>
    ): IngestedAttachment? {
        val candidate = territoryRoots.firstNotNullOfOrNull { root ->
            runCatching { root.resolve(mention.removePrefix("@")).normalize() }.getOrNull()
        }
        // Size is read before the resolver decides, because the ceiling is one
        // of the things it decides on.
        val size = candidate?.let(sizeOf) ?: -1L

        return when (val resolution = mentions.resolve(mention, size)) {
            is MentionResolution.Resolved -> {
                val ingested = attachmentReader.read(resolution)
                if (ingested == null) {
                    // Resolved but unreadable: permitted by every boundary and
                    // still not delivered. Reported as a refusal because that
                    // is what the operator experiences.
                    refused += "@$mention — could not be read (check permissions)"
                    null
                } else {
                    // add(), not +=: Path is itself Iterable<Path>, so `+=` is
                    // ambiguous between appending the path and its segments.
                    attachments.add(ingested)
                    ingested
                }
            }
            is MentionResolution.Refused -> {
                refused += "@$mention — ${resolution.reason} (${resolution.remedy})"
                null
            }
        }
    }

    fun accept(raw: String, source: NlSource): NlEntry {
        val canonical = canonicalizer.canonicalize(raw)

        val attachments = mutableListOf<IngestedAttachment>()
        val refusedAttachments = mutableListOf<String>()

        // [AtMentionScanner], not MentionExtractor. The two answer different
        // questions and only one of them is about files: the extractor matches
        // `@name` against a set of known root names, so its grammar stops at
        // the first `/` or `.` and `@docs/spec.txt` reaches the resolver as
        // `docs` — a directory, refused, with the operator told nothing useful.
        // The scanner's grammar is path-shaped on purpose, which is what an
        // attachment is.
        AtMentionScanner.scan(canonical.canonical).forEach { mention ->
            ingest(mention, attachments, refusedAttachments)
        }

        // Bare `@name` mentions, after the path-shaped ones have been taken.
        //
        // A name immediately followed by `/` or `.` is the head of a path the
        // scanner already owns — `@docs/spec.txt` would otherwise also register
        // as a territory called `docs`, and the operator would be told about a
        // place when they attached a file.
        val text = canonical.canonical
        val namedTerritories = mutableListOf<String>()
        val unknownNames = mutableListOf<String>()
        territoryNames.extractMentions(text).forEach { mention ->
            val after = text.getOrNull(mention.endIndex + 1)
            if (after == '/' || after == '.') return@forEach
            if (mention.resolvedPath != null) {
                namedTerritories.add(mention.resolvedPath)
            } else {
                unknownNames.add(mention.token)
            }
        }

        val envelope = wrapper.wrap(canonical, source)
        return NlEntry(
            envelope = envelope,
            resolution = resolver.resolve(envelope),
            attachments = attachments,
            refusedAttachments = refusedAttachments,
            namedTerritories = namedTerritories.distinct(),
            unknownNames = unknownNames.distinct(),
            canonicalization = canonical
        )
    }
}

/**
 * The text of a command with its mentions substituted, and what they were.
 *
 * @param refused never silently empty when a mention was refused: a command
 *   that ran against a document the engine could not read must not look like
 *   one that ran against the document.
 */
data class MentionExpansion(
    val text: String,
    val attachments: List<IngestedAttachment>,
    val refused: List<String>
) {
    val changed: Boolean get() = attachments.isNotEmpty() || refused.isNotEmpty()

    /** What to tell the operator, or null when no mention was involved. */
    fun notice(): String? = buildList {
        if (attachments.isNotEmpty()) {
            add(
                "attached: " + attachments.joinToString(", ") { attachment ->
                    attachment.name +
                        when {
                            attachment.truncated -> " (truncated)"
                            !attachment.isText -> " (binary; contents not included)"
                            else -> ""
                        }
                }
            )
        }
        refused.forEach { add("not attached: $it") }
    }.takeIf { it.isNotEmpty() }?.joinToString("\n")
}

/**
 * @param refusedAttachments never silently empty when a mention was refused.
 *   An operator who attached a file and heard nothing back assumes it was
 *   read, and then asks a question about a document the engine never saw.
 */
data class NlEntry(
    val envelope: NlEnvelope,
    val resolution: NlResolution,
    val attachments: List<IngestedAttachment>,
    val refusedAttachments: List<String>,
    /** Granted territories the prompt named by bare `@name`. */
    val namedTerritories: List<String> = emptyList(),
    /**
     * `@name` tokens that match no granted territory and no file.
     *
     * Reported rather than ignored, for the same reason a refused attachment
     * is: an operator who wrote `@myapp` and heard nothing assumes the engine
     * knew what they meant.
     */
    val unknownNames: List<String> = emptyList(),
    val canonicalization: NlCanonicalResult
) {
    val needsProvider: Boolean get() = resolution is NlResolution.Prose

    /** The typed intent boundary consumed by command and factory callers. */
    val intentEnvelope: atropos.core.intent.IntentEnvelope
        get() = atropos.core.intent.IntentEnvelope(
            intentId = envelope.canonicalSha256,
            command = when (resolution) {
                is NlResolution.ExactCommand -> resolution.command
                is NlResolution.Suggested -> resolution.command
                is NlResolution.Ambiguous -> resolution.candidates.firstOrNull().orEmpty()
                is NlResolution.Prose -> envelope.canonical
                NlResolution.Empty -> ""
            },
            parameters = when (val resolved = resolution) {
                is NlResolution.ExactCommand -> mapOf("arguments" to resolved.arguments)
                else -> emptyMap()
            },
            parsedOk = resolution !is NlResolution.Empty
        )

    /**
     * What a provider is actually asked, attachments included.
     *
     * The envelope's [NlEnvelope.canonical] stays exactly what the operator
     * typed — its whole purpose is that `originalSha256` proves that, and
     * splicing a document into it would make the provenance record disagree
     * with the human. So the composition happens here instead, once, where
     * every surface can reach it.
     *
     * Callers that send `envelope.canonical` to a provider send a prompt with
     * the attachments missing. That was the defect: the CLI printed
     * "attached: spec.txt" and then asked the model a question about a document
     * it had never been given.
     */
    fun promptText(): String {
        if (attachments.isEmpty()) return envelope.canonical
        return buildString {
            // Attachments first, question last. The operator's own words are
            // what the model must act on, and a request buried above a long
            // document competes with the document for attention.
            attachments.forEach { attachment ->
                appendLine(attachment.promptBlock())
                appendLine()
            }
            append(envelope.canonical)
        }
    }

    /** The evidence lines for what this entry carried, attachments included. */
    fun evidence(): List<String> = listOf(envelope.evidence()) + attachments.map { it.evidence() }

    /** What to tell the operator before acting, or null when there is nothing. */
    fun notice(): String? {
        val lines = buildList {
            if (canonicalization.changed) add(canonicalization.render())
            if (attachments.isNotEmpty()) {
                add(
                    "attached: " + attachments.joinToString(", ") { attachment ->
                        // The truncation is said out loud. An operator who is
                        // told a file was attached will ask about all of it.
                        attachment.name + if (attachment.truncated) " (truncated)" else ""
                    }
                )
            }
            refusedAttachments.forEach { add("not attached: $it") }
            if (namedTerritories.isNotEmpty()) {
                add("territory: " + namedTerritories.joinToString(", "))
            }
            unknownNames.forEach { add("not recognised: $it names no granted territory and no file") }
        }
        return lines.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }
}
