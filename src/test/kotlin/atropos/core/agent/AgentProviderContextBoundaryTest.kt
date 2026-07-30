package atropos.core.agent

import atropos.core.provider.ContextEnvelopeFactory
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgentProviderContextBoundaryTest {
    @Test
    fun rejects_envelope_from_another_repository_before_provider_call() {
        val activeRoot = Files.createTempDirectory("atropos-active-")
        val foreign = ContextEnvelopeFactory.createSimple("groq", "", "self-host", Files.createTempDirectory("atropos-foreign-"))

        val refusal = AgentProviderContextBoundary.validateEnvelope(foreign, activeRoot)

        assertEquals(AgentProviderContextBoundary.Refusal.Code.REPOSITORY_MISMATCH, refusal?.code)
    }

    @Test
    fun rejects_forged_or_missing_envelope_hash() {
        val root = Files.createTempDirectory("atropos-envelope-")
        val envelope = ContextEnvelopeFactory.createSimple("groq", "", "self-host", root)

        val refusal = AgentProviderContextBoundary.validateEnvelope(
            envelope.copy(canonicalContextHash = "forged"),
            root
        )

        assertEquals(AgentProviderContextBoundary.Refusal.Code.INVALID_CONTEXT_HASH, refusal?.code)
    }

    @Test
    fun requires_matching_pack_and_receipt_markers() {
        val context = "SOURCE_PACK_ID=pack-123\nFETCH_RECEIPT_ID=fetch-456\nFILE src/Main.kt\n"

        assertNull(AgentProviderContextBoundary.validateSourcePack(context, "pack-123", "fetch-456"))
        assertEquals(
            AgentProviderContextBoundary.Refusal.Code.MISSING_FETCH_RECEIPT,
            AgentProviderContextBoundary.validateSourcePack(context, "pack-123", null)?.code
        )
        assertEquals(
            AgentProviderContextBoundary.Refusal.Code.PACK_RECEIPT_MISMATCH,
            AgentProviderContextBoundary.validateSourcePack(context, "pack-other", "fetch-456")?.code
        )
    }

    @Test
    fun rejects_truncated_source_context() {
        val context = "SOURCE_PACK_ID=pack-123\nFETCH_RECEIPT_ID=fetch-456\nFILE src/Main.kt\n"

        val refusal = AgentProviderContextBoundary.validateSourcePack(
            context = context,
            sourcePackId = "pack-123",
            fetchReceiptId = "fetch-456",
            truncated = true
        )

        assertEquals(AgentProviderContextBoundary.Refusal.Code.TRUNCATED_SOURCE_PACK, refusal?.code)
    }
}
