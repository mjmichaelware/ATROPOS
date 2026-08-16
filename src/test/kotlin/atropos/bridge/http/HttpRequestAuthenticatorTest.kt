package atropos.bridge.http

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpRequestAuthenticatorTest {
    private fun request(authorization: String? = null) = HttpRequest(
        method = "GET",
        path = "/v1/status",
        query = emptyMap(),
        headers = authorization?.let { mapOf("authorization" to it) } ?: emptyMap(),
        body = ""
    )

    @Test
    fun `unset password leaves the local bridge open`() {
        assertNull(HttpRequestAuthenticator(null).authorize(request()))
    }

    @Test
    fun `configured password refuses missing or incorrect bearer`() {
        val authenticator = HttpRequestAuthenticator("test-password")
        assertEquals(401, authenticator.authorize(request())?.status)
        assertEquals(401, authenticator.authorize(request("Bearer wrong"))?.status)
    }

    @Test
    fun `configured password accepts only the matching bearer`() {
        assertNull(HttpRequestAuthenticator("test-password").authorize(request("Bearer test-password")))
        val response = HttpRequestAuthenticator("test-password").authorize(request("Bearer wrong"))
        assertTrue(response!!.body.contains("bridge-authentication-required"))
    }
}
