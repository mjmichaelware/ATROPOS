package atropos.core.provider

import atropos.core.provider.adapter.AdapterJson
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProviderFailureRedactionTest {
    private val credential = "credential-value-should-never-persist"
    private val credentialUrl = "https://api.example.test/v1/chat?api_key=$credential&trace=operator"

    @Test
    fun normalizer_redacts_credential_bearing_exception_urls() {
        val failure = ProviderErrorNormalizer().normalize(
            "example",
            IllegalArgumentException("request failed for $credentialUrl")
        )

        assertSanitized(failure.cleanSummary)
        assertTrue(failure.cleanSummary.contains("<redacted:url>"))
    }

    @Test
    fun parsed_adapter_errors_and_failure_results_do_not_expose_urls() {
        val result = AdapterJson.parseOpenAiCompatibleSuccess(
            "example",
            """{"error":{"message":"upstream rejected $credentialUrl"}}"""
        )
        val failure = result as ProviderCallResult.Failure

        assertSanitized(failure.failure.cleanSummary)
        assertSanitized(failure.toString())
    }

    @Test
    fun manually_created_failures_are_sanitized_before_quota_persistence() {
        val file = Files.createTempDirectory("atropos-provider-failure").resolve("quota.tsv").toFile()
        val ledger = FileQuotaLedger(
            file,
            listOf(ProviderQuotaRecord("example", CostMode.FREE, quotaWeight = 1))
        )

        ledger.recordFailure(
            "example",
            ProviderFailure("example", NormalizedProviderFailureType.INTERNAL, "transport failed: $credentialUrl")
        )

        assertSanitized(ledger.get("example")?.lastErrorSummary.orEmpty())
        assertSanitized(file.readText())
    }

    @Test
    fun provider_responses_are_sanitized_before_attestation_or_display() {
        val envelope = ContextEnvelope(
            providerId = "example",
            repository = "repo",
            repositoryRoot = "/tmp/repo",
            branch = "main",
            baselineCommit = "abc123",
            task = "task",
            systemIdentity = "ATROPOS",
            contextVersion = "v1",
            canonicalContextHash = "hash"
        )

        val parsed = ProviderResponseContextParser.parse("provider echoed $credentialUrl", envelope)

        assertSanitized(parsed.rawResponse)
        assertSanitized(parsed.cleanedResponse)
    }

    private fun assertSanitized(value: String) {
        assertFalse(value.contains(credential), value)
        assertFalse(value.contains("api.example.test"), value)
    }
}
