/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.github

import atropos.core.security.TokenIsolationVault
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GitHubDeviceAuthClientTest {
    @Test
    fun device_flow_polls_pending_then_stores_token_in_existing_vault() {
        val root = Files.createTempDirectory("github-device-auth")
        val requests = mutableListOf<GitHubOAuthRequest>()
        var polls = 0
        val client = GitHubDeviceAuthClient(
            clientId = "public-client-id",
            transport = GitHubOAuthTransport { request ->
                requests += request
                if (request.url.endsWith("/device/code")) {
                    GitHubOAuthResponse(200, """{"device_code":"device","user_code":"ABCD-EFGH","verification_uri":"https://github.com/login/device","expires_in":600,"interval":1}""")
                } else {
                    polls++
                    if (polls == 1) GitHubOAuthResponse(400, """{"error":"authorization_pending"}""")
                    else GitHubOAuthResponse(200, """{"access_token":"ghs-test-token","token_type":"bearer"}""")
                }
            },
            vault = TokenIsolationVault(root.resolve("secrets")),
            sleeper = {},
            maxPolls = 3
        )

        val authorization = client.begin()
        assertEquals("ABCD-EFGH", authorization.userCode)
        val token = client.poll(authorization)
        val path = client.store(token)
        assertEquals("GITHUB_TOKEN.secret", path.fileName.toString())
        assertEquals("ghs-test-token", TokenIsolationVault(root.resolve("secrets")).readSecret("GITHUB_TOKEN"))
        assertTrue(requests.all { !it.form.contains("ghs-test-token") })
    }

    @Test
    fun missing_client_id_fails_actionably_without_network() {
        val client = GitHubDeviceAuthClient(clientId = " ", transport = GitHubOAuthTransport { error("network must not run") })
        val failure = assertFailsWith<IllegalStateException> { client.begin() }
        assertTrue(failure.message!!.contains("ATROPOS_GITHUB_OAUTH_CLIENT_ID"))
    }
}
