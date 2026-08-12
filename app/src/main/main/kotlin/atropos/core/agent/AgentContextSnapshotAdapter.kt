package atropos.core.agent

import atropos.core.provider.ContextEnvelope
import atropos.core.provider.ContextEnvelopeFactory
import java.nio.file.Path

/**
 * Converts between the ask path's context shapes.
 *
 * Two small conversions that were private extensions on [AgentService], where
 * they read as incidental. Both encode a rule worth stating.
 */
internal object AgentContextSnapshotAdapter {

    /**
     * Presents a caller-supplied context override as a collected snapshot.
     *
     * `truncated = false` unconditionally: an override is context a caller
     * assembled deliberately and handed over whole. Marking it truncated would
     * make the source-pack boundary refuse a complete context for a property
     * only collected contexts can have.
     */
    fun toSnapshot(override: AgentAskContextOverride, repoRoot: Path): AgentContextSnapshot =
        AgentContextSnapshot(
            repoRoot = repoRoot,
            text = override.contextText,
            byteCount = override.byteCount,
            truncated = false,
            sourcePackId = override.sourcePackId,
            fetchReceiptId = override.fetchReceiptId,
            sourcePackContentHash = override.sourcePackContentHash,
            sourceTreeHash = override.sourceTreeHash,
            sourceBindingKind = override.sourceBindingKind
        )

    /**
     * Re-points an envelope at the provider that will actually be called.
     *
     * The canonical hash covers the provider id, so changing the id without
     * recomputing it would produce an envelope whose hash describes a different
     * call — and attestation, which checks the response against that hash,
     * would then fail for a reason unrelated to the response. The hash is
     * cleared before recomputing so the old value cannot be folded into the new one.
     *
     * Returns the same instance when the provider already matches, so an
     * unchanged envelope keeps its identity.
     */
    fun forProvider(envelope: ContextEnvelope, providerId: String): ContextEnvelope {
        if (envelope.providerId == providerId) return envelope
        val adjusted = envelope.copy(providerId = providerId, canonicalContextHash = "")
        return adjusted.copy(canonicalContextHash = ContextEnvelopeFactory.computeHash(adjusted))
    }
}
