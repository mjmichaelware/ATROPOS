package atropos.core.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.nio.file.Files

class SecretEnrollmentSourceTest {
    @Test
    fun local_vault_source_enrolls_configured_values_without_changing_evidence_shape() {
        val root = Files.createTempDirectory("atropos-enrollment-vault-")
        TokenIsolationVault(root, TestSecretVaultKeyProvider()).writeSecret("TEST_API_KEY", "local-secret")

        val result = SecretEnrollment(
            listOf(LocalVaultSecretSource(listOf("TEST_API_KEY"), TokenIsolationVault(root, TestSecretVaultKeyProvider())))
        ).enrollInto(KnownSecretRegistry())

        assertEquals(setOf("TEST_API_KEY"), result.enrolledLabels)
        // One credential, but several enrolled representations: variantCount counts
        // the forms guarded, not the secrets. For "local-secret" the closure yields
        // 4 distinct forms — raw, base64, and hex in both cases — because the
        // URL-encoded form is identical to raw (`-` is URL-safe) and the padded and
        // unpadded base64 coincide (12 bytes divides by 3). Asserting a literal
        // count here would encode that accident, so it is derived.
        assertEquals(1, result.enrolledLabels.size)
        assertEquals(SecretEncodingClosure.variantsOf("local-secret").size, result.variantCount)
        assertTrue(result.variantCount > 1, "a single form would mean the closure is not enrolling encodings")
        assertTrue(result.evidenceLine().contains("local_vault=1"))
        assertTrue(!result.evidenceLine().contains("local-secret"))
    }

    @Test
    fun discovery_failure_is_typed_and_visible_without_exposing_exception_text() {
        val source = object : SecretEnrollmentSource {
            override val sourceName = "test-vault"
            override fun discover(): Map<String, String> =
                error("provider secret value must never become enrollment evidence")
        }

        val result = SecretEnrollment(listOf(source)).enrollInto(KnownSecretRegistry())

        assertEquals(1, result.failures.size)
        assertEquals("test-vault", result.failures.single().source)
        assertEquals("IllegalStateException", result.failures.single().errorType)
        assertTrue(!result.evidenceLine().contains("provider secret value"))
        assertTrue(result.evidenceLine().contains("failures=test-vault:IllegalStateException"))
    }
}
