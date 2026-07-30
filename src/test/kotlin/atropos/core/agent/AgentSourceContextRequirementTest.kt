package atropos.core.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentSourceContextRequirementTest {
    @Test
    fun selfHostAndCodeAwarePromptsRequireSourcePack() {
        assertTrue(AgentSourceContextRequirement.requiredForAsk("ATROPOS, improve yourself"))
        assertTrue(AgentSourceContextRequirement.requiredForAsk("inspect src/main/kotlin/atropos/core/agent/Foo.kt"))
        assertTrue(AgentSourceContextRequirement.requiredForAsk("explain this code path"))
        assertFalse(AgentSourceContextRequirement.requiredForAsk("what is your current status?"))
    }

    @Test
    fun codeAware_override_without_pack_is_typed_refusal() {
        val refusal = AgentSourceContextRequirement.refusalFor(
            operation = "ask",
            task = "inspect src/main/kotlin/atropos/core/agent/Foo.kt",
            sourcePackId = null,
            fetchReceiptId = null
        )

        assertTrue(refusal != null)
        assertEquals(
            AgentSourceContextRequirement.Refusal.Code.MISSING_SOURCE_PACK,
            refusal?.code
        )
        assertTrue(refusal?.message?.contains("provider ask refused") == true)
    }

    @Test
    fun codeAware_override_without_fetch_receipt_is_refused_after_pack_check() {
        val refusal = AgentSourceContextRequirement.refusalFor(
            operation = "ask",
            task = "ATROPOS, improve yourself",
            sourcePackId = "pack-123",
            fetchReceiptId = null
        )

        assertTrue(refusal != null)
        assertEquals(
            AgentSourceContextRequirement.Refusal.Code.MISSING_FETCH_RECEIPT,
            refusal?.code
        )
    }

    @Test
    fun non_code_aware_override_does_not_require_source_attestation() {
        val refusal = AgentSourceContextRequirement.refusalFor(
            operation = "ask",
            task = "what is your current status?",
            sourcePackId = null,
            fetchReceiptId = null
        )

        assertEquals(null, refusal)
    }

    @Test
    fun code_aware_context_with_mismatched_pack_marker_is_typed_refusal() {
        val refusal = AgentSourceContextRequirement.refusalFor(
            operation = "ask",
            task = "ATROPOS improve yourself",
            sourcePackId = "pack-123",
            fetchReceiptId = "fetch-456",
            context = "SOURCE_PACK_ID=pack-other\nFETCH_RECEIPT_ID=fetch-456"
        )

        assertEquals(
            AgentSourceContextRequirement.Refusal.Code.PACK_RECEIPT_MISMATCH,
            refusal?.code
        )
    }
}
