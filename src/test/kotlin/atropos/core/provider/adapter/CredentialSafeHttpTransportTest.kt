package atropos.core.provider.adapter

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class CredentialSafeHttpTransportTest {
    @Test
    fun rejects_insecure_and_user_info_endpoints_before_a_request_is_sent() {
        assertFailsWith<IllegalArgumentException> {
            CredentialSafeHttpTransport.open(URI("http://provider.example.test/v1"))
        }
        assertFailsWith<IllegalArgumentException> {
            CredentialSafeHttpTransport.open(URI("https://credential@provider.example.test/v1"))
        }
    }

    @Test
    fun disables_redirects_and_caches_for_credentialed_provider_requests() {
        val connection = CredentialSafeHttpTransport.open(URI("https://provider.example.test/v1"))

        assertFalse(connection.instanceFollowRedirects)
        assertFalse(connection.useCaches)
        connection.disconnect()
    }
}
