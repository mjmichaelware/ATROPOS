/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.github

import atropos.core.AtroposConfig
import atropos.core.security.TokenIsolationVault
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Duration

data class GitHubDeviceAuthorization(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresInSeconds: Long,
    val intervalSeconds: Long
)

data class GitHubDeviceToken(val accessToken: String, val tokenType: String)

data class GitHubOAuthRequest(val method: String, val url: String, val form: String)
data class GitHubOAuthResponse(val status: Int, val body: String)

fun interface GitHubOAuthTransport {
    fun send(request: GitHubOAuthRequest): GitHubOAuthResponse
}

/**
 * Bounded GitHub device authorization flow for terminal-only environments.
 *
 * The client id is public configuration (`ATROPOS_GITHUB_OAUTH_CLIENT_ID`),
 * never a secret. The resulting token enters the existing local vault and is
 * subsequently read through the normal `GITHUB_TOKEN` secret precedence.
 */
class GitHubDeviceAuthClient(
    private val clientId: String? = System.getenv("ATROPOS_GITHUB_OAUTH_CLIENT_ID"),
    private val transport: GitHubOAuthTransport = ::sendOverHttps,
    private val vault: TokenIsolationVault = TokenIsolationVault(
        AtroposConfig.configRoot().resolve("secrets")
    ),
    private val sleeper: (Long) -> Unit = Thread::sleep,
    private val maxPolls: Int = 60
) {
    fun begin(): GitHubDeviceAuthorization {
        val id = clientId?.trim()?.takeIf { it.isNotEmpty() }
            ?: error("GitHub OAuth client id is not configured; set ATROPOS_GITHUB_OAUTH_CLIENT_ID")
        val response = transport.send(
            GitHubOAuthRequest(
                "POST",
                DEVICE_CODE_URL,
                form("client_id" to id, "scope" to "repo read:org")
            )
        )
        require(response.status in 200..299) {
            "GitHub device authorization failed: HTTP ${response.status}"
        }
        val deviceCode = jsonString(response.body, "device_code")
        val userCode = jsonString(response.body, "user_code")
        val verificationUri = (jsonOptionalString(response.body, "verification_uri")
            ?: jsonString(response.body, "verification_uri_complete"))
        require(verificationUri.startsWith("https://github.com/")) {
            "GitHub returned an invalid device verification URI"
        }
        return GitHubDeviceAuthorization(
            deviceCode = deviceCode,
            userCode = userCode,
            verificationUri = verificationUri,
            expiresInSeconds = jsonLong(response.body, "expires_in"),
            intervalSeconds = jsonLong(response.body, "interval").coerceAtLeast(1)
        )
    }

    fun poll(authorization: GitHubDeviceAuthorization): GitHubDeviceToken {
        val id = clientId?.trim()?.takeIf { it.isNotEmpty() }
            ?: error("GitHub OAuth client id is not configured; set ATROPOS_GITHUB_OAUTH_CLIENT_ID")
        repeat(maxPolls.coerceAtLeast(1)) {
            val response = transport.send(
                GitHubOAuthRequest(
                    "POST",
                    ACCESS_TOKEN_URL,
                    form(
                        "client_id" to id,
                        "device_code" to authorization.deviceCode,
                        "grant_type" to DEVICE_GRANT
                    )
                )
            )
            val accessToken = jsonOptionalString(response.body, "access_token")
            if (response.status in 200..299 && !accessToken.isNullOrBlank()) {
                return GitHubDeviceToken(
                    accessToken = accessToken,
                    tokenType = jsonOptionalString(response.body, "token_type") ?: "bearer"
                )
            }
            when (jsonOptionalString(response.body, "error")) {
                "authorization_pending" -> sleeper(authorization.intervalSeconds * 1_000L)
                "slow_down" -> sleeper((authorization.intervalSeconds + 5L) * 1_000L)
                "expired_token" -> error("GitHub device authorization expired; run /auth github again")
                "access_denied" -> error("GitHub device authorization was denied")
                else -> error("GitHub device token exchange failed: HTTP ${response.status}")
            }
        }
        error("GitHub device authorization did not complete within the bounded polling window")
    }

    fun store(token: GitHubDeviceToken): Path {
        require(token.accessToken.isNotBlank()) { "GitHub OAuth returned an empty token" }
        return vault.writeSecret("GITHUB_TOKEN", token.accessToken)
    }

    private fun form(vararg values: Pair<String, String>): String = values.joinToString("&") { (key, value) ->
        "${encode(key)}=${encode(value)}"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun jsonString(body: String, key: String): String =
        jsonOptionalString(body, key) ?: error("GitHub OAuth response omitted $key")

    private fun jsonOptionalString(body: String, key: String): String? =
        Regex("\\\"${Regex.escape(key)}\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").find(body)?.groupValues?.get(1)

    private fun jsonLong(body: String, key: String): Long =
        Regex("\\\"${Regex.escape(key)}\\\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toLongOrNull()
            ?: error("GitHub OAuth response omitted numeric $key")

    private companion object {
        const val DEVICE_CODE_URL = "https://github.com/login/device/code"
        const val ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token"
        const val DEVICE_GRANT = "urn:ietf:params:oauth:grant-type:device_code"

        fun sendOverHttps(request: GitHubOAuthRequest): GitHubOAuthResponse {
            val response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().send(
                HttpRequest.newBuilder(URI.create(request.url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .method(request.method, HttpRequest.BodyPublishers.ofString(request.form))
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            )
            return GitHubOAuthResponse(response.statusCode(), response.body())
        }
    }
}
