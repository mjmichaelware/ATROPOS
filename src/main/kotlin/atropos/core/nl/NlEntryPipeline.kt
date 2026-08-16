/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.nl

import atropos.core.ingest.AtMentionScanner
import atropos.core.ingest.MentionResolution
import atropos.core.ingest.MentionResolver
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
 * 3. **Resolve mentions** against territory. Refused mentions are reported,
 *    not dropped: an operator who attached a file and got no acknowledgement
 *    assumes it was read.
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
    private val mentionExtractor: MentionExtractor = MentionExtractor(
        territoryRoots.flatMap { root ->
            runCatching { Files.list(root).use { stream -> stream.map { it.fileName.toString() }.toList() } }
                .getOrDefault(emptyList())
        }.toSet()
    ),
    /** Injected so the pipeline can be tested without a filesystem. */
    private val sizeOf: (Path) -> Long = { path ->
        runCatching { Files.size(path) }.getOrDefault(-1L)
    }
) {
    fun accept(raw: String, source: NlSource): NlEntry {
        val canonical = canonicalizer.canonicalize(raw)

        val attachments = mutableListOf<Path>()
        val refusedAttachments = mutableListOf<String>()

        mentionExtractor.extractMentions(canonical.canonical).forEach { extracted ->
            val mention = extracted.token.removePrefix("@")
            val candidate = territoryRoots.firstNotNullOfOrNull { root ->
                runCatching { root.resolve(mention.removePrefix("@")).normalize() }.getOrNull()
            }
            // Size is read before the resolver decides, because the ceiling is
            // one of the things it decides on. A missing file reports -1, which
            // is under any ceiling and is caught by the territory check instead.
            val size = candidate?.let(sizeOf) ?: -1L

            when (val resolution = mentions.resolve(mention, size)) {
                // add(), not +=: Path is itself Iterable<Path>, so `+=` is
                // ambiguous between appending the path and appending its
                // segments.
                is MentionResolution.Resolved -> attachments.add(resolution.path)
                is MentionResolution.Refused ->
                    refusedAttachments += "@$mention — ${resolution.reason} (${resolution.remedy})"
            }
        }

        val envelope = wrapper.wrap(canonical, source)
        return NlEntry(
            envelope = envelope,
            resolution = resolver.resolve(envelope),
            attachments = attachments,
            refusedAttachments = refusedAttachments,
            canonicalization = canonical
        )
    }
}

/**
 * @param refusedAttachments never silently empty when a mention was refused.
 *   An operator who attached a file and heard nothing back assumes it was
 *   read, and then asks a question about a document the engine never saw.
 */
data class NlEntry(
    val envelope: NlEnvelope,
    val resolution: NlResolution,
    val attachments: List<Path>,
    val refusedAttachments: List<String>,
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

    /** What to tell the operator before acting, or null when there is nothing. */
    fun notice(): String? {
        val lines = buildList {
            if (canonicalization.changed) add(canonicalization.render())
            if (attachments.isNotEmpty()) {
                add("attached: " + attachments.joinToString(", ") { it.fileName.toString() })
            }
            refusedAttachments.forEach { add("not attached: $it") }
        }
        return lines.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }
}
