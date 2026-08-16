package atropos.core.factory

import atropos.core.security.RedactionFilter
import java.net.URI
import atropos.core.ProviderHttpClient

/**
 * Owns the transport limits for optional factory research.
 *
 * The factory orchestrator decides which channels to visit; this boundary
 * only validates and performs bounded HTTP(S) reads. It never interprets
 * research content or changes factory state.
 */
internal class BoundedResearchFetcher(
    private val maxBytes: Int,
    private val timeoutMillis: Int,
    private val maxRequests: Int = 2,
    private val maxQueryParameters: Int = 8,
    private val maxUrlChars: Int = 2048,
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val httpClient: ProviderHttpClient = ProviderHttpClient(redactionFilter)
) {
    private var requestsUsed = 0

    init {
        require(maxBytes > 0) { "bounded research byte limit must be positive" }
        require(timeoutMillis > 0) { "bounded research timeout must be positive" }
        require(maxRequests > 0) { "bounded research request limit must be positive" }
        require(maxQueryParameters >= 0) { "bounded research query limit cannot be negative" }
        require(maxUrlChars > 0) { "bounded research URL limit must be positive" }
    }

    @Synchronized
    fun fetch(rawUrl: String, method: String = "GET"): Result<String> = runCatching {
        require(rawUrl.length <= maxUrlChars) { "bounded research URL exceeded $maxUrlChars characters" }
        require(method == "GET" || method == "HEAD") {
            "bounded research permits GET or HEAD only"
        }
        val uri = URI(rawUrl)
        require(uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) {
            "bounded research permits HTTP(S) only"
        }
        require(uri.userInfo == null) { "bounded research URL must not contain embedded credentials" }
        val queryParameters = uri.rawQuery
            ?.takeIf { it.isNotBlank() }
            ?.split('&')
            ?.count { it.isNotBlank() }
            ?: 0
        require(queryParameters <= maxQueryParameters) {
            "bounded research URL exceeded $maxQueryParameters query parameters"
        }
        require(requestsUsed < maxRequests) {
            "bounded research request limit exhausted"
        }
        requestsUsed += 1

        val response = httpClient.requestBounded(uri.toString(), method, timeoutMillis, maxBytes)
        require(response.statusCode in 200..299) {
            "bounded research request returned HTTP ${response.statusCode}"
        }
        response.body
    }
}
