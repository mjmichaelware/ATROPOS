package atropos.core.agent

import atropos.core.provider.ContextEnvelope
import atropos.core.provider.ContextEnvelopeFactory
import atropos.core.provider.SourceBindingKind
import java.nio.file.Path

/**
 * Fail-closed boundary for provider calls that depend on repository context.
 *
 * This is deliberately a validator, not a second context or policy system:
 * source ownership stays with CodebaseContextPacker and envelope ownership
 * stays with ContextEnvelopeFactory.
 */
object AgentProviderContextBoundary {
    data class Refusal(
        val code: Code,
        val detail: String
    ) {
        enum class Code {
            BLANK_CONTEXT,
            INVALID_IDENTITY,
            REPOSITORY_MISMATCH,
            INVALID_CONTEXT_HASH,
            MISSING_SOURCE_PACK,
            MISSING_FETCH_RECEIPT,
            MISSING_CONTEXT,
            PACK_RECEIPT_MISMATCH,
            TRUNCATED_SOURCE_PACK
        }

        val message: String get() = "provider context refused: $detail"
    }

    fun validateEnvelope(envelope: ContextEnvelope, repoRoot: Path): Refusal? {
        if (envelope.systemIdentity != "ATROPOS") {
            return Refusal(Refusal.Code.INVALID_IDENTITY, "context identity is not ATROPOS")
        }
        val expectedRoot = repoRoot.toAbsolutePath().normalize().toString()
        val suppliedRoot = runCatching {
            Path.of(envelope.repositoryRoot).toAbsolutePath().normalize().toString()
        }.getOrNull()
        if (suppliedRoot != expectedRoot || envelope.repository != repoRoot.fileName.toString()) {
            return Refusal(Refusal.Code.REPOSITORY_MISMATCH, "context repository does not match the active ATROPOS root")
        }
        val expectedHash = ContextEnvelopeFactory.computeHash(envelope.copy(canonicalContextHash = ""))
        if (envelope.canonicalContextHash.isBlank() || envelope.canonicalContextHash != expectedHash) {
            return Refusal(Refusal.Code.INVALID_CONTEXT_HASH, "context envelope hash is missing or invalid")
        }
        return null
    }

    fun validateSourcePack(
        context: String?,
        sourcePackId: String?,
        fetchReceiptId: String?,
        sourcePackContentHash: String? = null,
        sourceTreeHash: String? = null,
        sourceBindingKind: SourceBindingKind? = null,
        truncated: Boolean = false
    ): Refusal? {
        if (context == null) {
            return Refusal(Refusal.Code.MISSING_CONTEXT, "attested source context is unavailable")
        }
        if (context.isBlank()) {
            return Refusal(Refusal.Code.BLANK_CONTEXT, "provider context is empty")
        }
        if (sourcePackId.isNullOrBlank()) {
            return Refusal(Refusal.Code.MISSING_SOURCE_PACK, "source context pack is unavailable")
        }
        if (fetchReceiptId.isNullOrBlank()) {
            return Refusal(Refusal.Code.MISSING_FETCH_RECEIPT, "source context fetch receipt is unavailable")
        }
        if (truncated) {
            return Refusal(Refusal.Code.TRUNCATED_SOURCE_PACK, "source context pack is truncated")
        }
        val packMarker = "SOURCE_PACK_ID=$sourcePackId"
        val receiptMarker = "FETCH_RECEIPT_ID=$fetchReceiptId"
        if (!hasExactMarker(context, packMarker) || !hasExactMarker(context, receiptMarker)) {
            return Refusal(
                Refusal.Code.PACK_RECEIPT_MISMATCH,
                "source context does not contain the supplied pack and fetch receipt identifiers"
            )
        }
        sourcePackContentHash?.takeIf { it.isNotBlank() }?.let {
            if (!hasExactMarker(context, "PACK_CONTENT_HASH=$it")) {
                return Refusal(Refusal.Code.PACK_RECEIPT_MISMATCH, "source context content hash does not match the attached pack")
            }
        }
        sourceTreeHash?.takeIf { it.isNotBlank() }?.let {
            if (!hasExactMarker(context, "TREE_HASH=$it")) {
                return Refusal(Refusal.Code.PACK_RECEIPT_MISMATCH, "source context tree hash does not match the attached receipt")
            }
        }
        sourceBindingKind?.let {
            if (!hasExactMarker(context, "BINDING=$it")) {
                return Refusal(Refusal.Code.PACK_RECEIPT_MISMATCH, "source context binding kind does not match the attached receipt")
            }
        }
        return null
    }

    private fun hasExactMarker(context: String, marker: String): Boolean =
        context.lineSequence().any { it == marker }
}
