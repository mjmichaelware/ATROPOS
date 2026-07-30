package atropos.core.agent

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LeaseTokenDigestTest {
    @Test
    fun persisted_identity_is_not_the_bearer_token_and_validates_presented_token() {
        val token = "lease-token-with-sensitive-bearer-identity"
        val digest = LeaseTokenDigest.of(token)

        assertNotEquals(token, digest)
        assertTrue(LeaseTokenDigest.matches(digest, token))
        assertFalse(LeaseTokenDigest.matches(digest, "different-token"))
        assertFalse(LeaseTokenDigest.matches(digest, null))
    }
}
