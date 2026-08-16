/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.http

/** Optional loopback-bridge authentication; blank configuration means local-open. */
class HttpRequestAuthenticator(password: String?) {
    private val configuredPassword = password?.trim()?.takeIf { it.isNotEmpty() }

    fun authorize(request: HttpRequest): HttpResponse? {
        val expected = configuredPassword ?: return null
        if (request.header("authorization") == "Bearer $expected") return null
        return HttpResponse.refusal(
            status = 401,
            reason = "bridge-authentication-required",
            detail = "The local bridge requires a bearer password.",
            remedy = "Send Authorization: Bearer <configured bridge password>."
        )
    }
}
