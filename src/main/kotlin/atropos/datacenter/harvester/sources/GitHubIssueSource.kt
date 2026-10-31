package atropos.datacenter.harvester.sources

import atropos.datacenter.harvester.ApiQuotaState
import atropos.datacenter.harvester.IssueRecord
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import org.json.JSONArray

/**
 * GitHub API issue source.
 * Parses X-RateLimit-* headers and adheres to GitHub REST API v3 constraints.
 */
class GitHubIssueSource(
    private val authToken: String? = System.getenv("GITHUB_TOKEN")
) : PlatformIssueSource {
    override val platformName: String = "GitHub"
    private var currentQuota = ApiQuotaState("GitHub", 5000, Instant.now().epochSecond + 3600, false)
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    override fun fetchOpenIssues(targetRepo: String): List<IssueRecord> {
        // Enforce Soft-Fail Network Law
        return try {
            val token = authToken?.ifBlank { null } ?: System.getenv("GITHUB_TOKEN")
            val uri = URI("https://api.github.com/repos/$targetRepo/issues?state=all&per_page=30")
            val reqBuilder = HttpRequest.newBuilder(uri)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "ATROPOS-Agent-Engine")
            if (!token.isNullOrBlank()) {
                reqBuilder.header("Authorization", "Bearer $token")
            }
            val req = reqBuilder.GET().build()
            val res = httpClient.send(req, HttpResponse.BodyHandlers.ofString())

            val remaining = res.headers().firstValue("x-ratelimit-remaining").orElse(null)?.toIntOrNull()
            val reset = res.headers().firstValue("x-ratelimit-reset").orElse(null)?.toLongOrNull()
            if (remaining != null && reset != null) {
                updateQuotaFromHeaders(remaining, reset)
            }

            if (res.statusCode() !in 200..299) {
                return emptyList()
            }

            val array = JSONArray(res.body())
            val records = mutableListOf<IssueRecord>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val labelsList = mutableListOf<String>()
                val labelsArr = obj.optJSONArray("labels")
                if (labelsArr != null) {
                    for (j in 0 until labelsArr.length()) {
                        val labelObj = labelsArr.optJSONObject(j)
                        val name = labelObj?.optString("name") ?: labelsArr.optString(j)
                        if (!name.isNullOrBlank()) labelsList.add(name)
                    }
                }
                records.add(
                    IssueRecord(
                        platform = "GitHub",
                        repository = targetRepo,
                        issueId = obj.optString("number", obj.optString("id")),
                        title = obj.optString("title", "Untitled Issue"),
                        body = obj.optString("body", ""),
                        labels = labelsList,
                        comments = emptyList(),
                        createdAt = Instant.now()
                    )
                )
            }
            records
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun quota(): ApiQuotaState = currentQuota

    fun updateQuotaFromHeaders(remaining: Int, resetEpoch: Long) {
        currentQuota = ApiQuotaState("GitHub", remaining, resetEpoch, remaining < 20)
    }
}
