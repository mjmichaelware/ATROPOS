package atropos.core.security

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedactionFilterTest {
    private val filter = RedactionFilter()

    @Test
    fun redacts_bearers_private_keys_signed_urls_and_paths() {
        val raw = """
            Authorization: Bearer ABCDEFGHIJKLMNOPQRSTUVWX
            api_key=sk-ABCDEFGHIJKLMNOPQRSTUVWX
            -----BEGIN PRIVATE KEY-----
            secret-material
            -----END PRIVATE KEY-----
            https://example.com/file?X-Amz-Signature=abcdef&token=ghijkl
            /tmp/client_secret-prod.json
        """.trimIndent()

        val report = filter.report(raw)
        assertTrue(report.changed)
        assertTrue(report.redacted.contains("<redacted"))
        assertFalse(report.redacted.contains("ABCDEFGHIJKLMNOPQRSTUVWX"))
        assertFalse(report.redacted.contains("secret-material"))
        assertFalse(report.redacted.contains("client_secret-prod.json"))
    }

    @Test
    fun redacts_every_canonical_surface_without_raw_secret_output() {
        val secret = "fake-secret-value-12345"
        val surfaces = listOf(
            "ui status shows api_key=$secret",
            "shell log Authorization: Bearer ABCDEFGHIJKLMNOPQRSTUVWX",
            "history command token=$secret",
            "memory note password=$secret",
            "queue payload secret=$secret",
            "prompt context /tmp/client_secret-prod.json",
            "diff +OPENAI_API_KEY=$secret",
            "status credential path /tmp/client_secret-prod.json"
        )

        val redacted = surfaces.map(filter::redact)

        assertTrue(redacted.all { it.contains("<redacted") })
        assertTrue(redacted.none { it.contains(secret) })
        assertTrue(redacted.none { it.contains("ABCDEFGHIJKLMNOPQRSTUVWX") })
        assertTrue(redacted.none { it.contains("client_secret-prod.json") })
    }
}
