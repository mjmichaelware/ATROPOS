package atropos.core.agent

import atropos.core.security.RedactionFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelfHostEvidenceTextCodecTest {
    private val codec = SelfHostEvidenceTextCodec(RedactionFilter())

    @Test
    fun cleans_and_hashes_evidence_after_redaction() {
        val clean = codec.clean("provider token=plain-token")

        assertFalse(clean.contains("plain-token"))
        assertTrue(clean.contains("<redacted:secret>"))
        assertEquals(64, codec.sha256Text(clean).length)
    }

    @Test
    fun classifies_entries_without_mutating_the_original_order() {
        val evidence = listOf("context_attestation ok", "gate_result passed", "swap_result passed")

        assertEquals(listOf("gate_result passed"), codec.evidenceClass(evidence, "gate_result"))
        assertEquals(
            listOf("context_attestation ok", "gate_result passed", "swap_result passed"),
            evidence
        )
    }

    @Test
    fun json_and_markdown_encoders_escape_structural_text() {
        val value = "a`b\"c\\d\nline"

        assertFalse(codec.escapeMarkdown(value).contains('`'))
        assertTrue(codec.json(value).contains("\\\""))
        assertTrue(codec.json(value).contains("\\n"))
    }
}
