package atropos.core.verification

import atropos.core.provider.ContextEnvelope
import atropos.core.provider.ProviderResponseContextParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OutputValidatorTest {
    @Test
    fun accepts_bounded_non_secret_output() {
        val result = OutputValidator().validate("answer: 42")

        assertTrue(result.accepted)
        assertEquals("answer: 42", result.redactedOutput)
    }

    @Test
    fun rejects_blank_oversized_and_secret_like_output() {
        assertFalse(OutputValidator().validate("   ").accepted)
        assertFalse(OutputValidator(maximumBytes = 3).validate("four").accepted)
        val secret = OutputValidator().validate("Authorization: Bearer ${"A".repeat(40)}")
        assertFalse(secret.accepted)
        assertTrue(secret.redactedOutput != "Authorization: Bearer ${"A".repeat(40)}")
    }

    @Test
    fun parser_carries_the_validation_result_before_attestation_consumers() {
        val response = ProviderResponseContextParser.parse("answer: 42", testEnvelope())

        assertTrue(response.validation.accepted)
        assertEquals("answer: 42", response.cleanedResponse)
    }

    private fun testEnvelope(): ContextEnvelope = ContextEnvelope(
        systemIdentity = "ATROPOS",
        repository = "test",
        repositoryRoot = "/repo",
        branch = "main",
        baselineCommit = "commit",
        hierarchyRole = "worker",
        contextVersion = "v1"
    )
}
