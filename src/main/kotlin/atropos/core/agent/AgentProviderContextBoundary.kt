package atropos.core.agent

import atropos.core.provider.ContextEnvelope
import atropos.core.provider.ContextEnvelopeFactory
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
            PACK_RECEIPT_MISMATCH
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
        context: String,
        sourcePackId: String?,
        fetchReceiptId: String?
    ): Refusal? {
        if (context.isBlank()) {
            return Refusal(Refusal.Code.BLANK_CONTEXT, "provider context is empty")
        }
        if (sourcePackId.isNullOrBlank()) {
            return Refusal(Refusal.Code.MISSING_SOURCE_PACK, "source context pack is unavailable")
        }
        if (fetchReceiptId.isNullOrBlank()) {
            return Refusal(Refusal.Code.MISSING_FETCH_RECEIPT, "source context fetch receipt is unavailable")
        }
        val packMarker = "SOURCE_PACK_ID=$sourcePackId"
        val receiptMarker = "FETCH_RECEIPT_ID=$fetchReceiptId"
        if (!context.contains(packMarker) || !context.contains(receiptMarker)) {
            return Refusal(
                Refusal.Code.PACK_RECEIPT_MISMATCH,
                "source context does not contain the supplied pack and fetch receipt identifiers"
            )
        }
        return null
    }
}
