package atropos.core.security

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VaultReadResultTest {
    @Test
    fun available_result_keeps_value_out_of_rendered_text() {
        val secret = "secret-value-must-not-render"
        val result = VaultReadResult.Available(Path.of(".atropos/secrets/TOKEN.secret"), secret)

        assertEquals(secret, result.value)
        assertFalse(result.toString().contains(secret))
        assertTrue(result.toString().contains("Available"))
    }

    @Test
    fun refused_result_is_typed_and_contains_no_untrusted_detail() {
        val result = VaultReadResult.Refused(
            Path.of(".atropos/secrets/TOKEN.secret"),
            VaultReadRefusalReason.INVALID_CIPHERTEXT
        )

        assertEquals(VaultReadRefusalReason.INVALID_CIPHERTEXT, result.reason)
        assertFalse(result.toString().contains("secret-value-must-not-render"))
    }
}
