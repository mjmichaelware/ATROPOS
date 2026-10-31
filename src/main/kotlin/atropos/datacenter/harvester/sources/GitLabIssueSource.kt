package atropos.datacenter.harvester.sources

import atropos.datacenter.harvester.ApiQuotaState
import atropos.datacenter.harvester.IssueRecord
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import org.json.JSONArray

/**
 * GitLab API issue source.
 * Queries GitLab v4 REST API and parses rate limit headers.
 */
class GitLabIssueSource(
    private val gitlabToken: String? = System.getenv("GITLAB_TOKEN")
) : PlatformIssueSource {
    override val platformName: String = "GitLab"
    private var currentQuota = ApiQuotaState("GitLab", 2000, Instant.now().epochSecond + 3600, false)
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    override fun fetchOpenIssues(targetRepo: String): List<IssueRecord> {
        return try {
            val encodedProject = URLEncoder.encode(targetRepo, Charsets.UTF_8.name())
            val uri = URI("https://gitlab.com/api/v4/projects/$encodedProject/issues?state=opened&per_page=20")
            val reqBuilder = HttpRequest.newBuilder(uri)
                .header("User-Agent", "ATROPOS-Agent-Engine")
            if (!gitlabToken.isNullOrBlank()) {
                reqBuilder.header("PRIVATE-TOKEN", gitlabToken)
            }
            val req = reqBuilder.GET().build()
            val res = httpClient.send(req, HttpResponse.BodyHandlers.ofString())

            val remaining = res.headers().firstValue("ratelimit-remaining").orElse(null)?.toIntOrNull()
            val reset = res.headers().firstValue("ratelimit-reset").orElse(null)?.toLongOrNull()
            if (remaining != null && reset != null) {
                currentQuota = ApiQuotaState("GitLab", remaining, reset, remaining < 20)
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
                        labelsList.add(labelsArr.optString(j))
                    }
                }
                records.add(
                    IssueRecord(
                        platform = "GitLab",
                        repository = targetRepo,
                        issueId = obj.optString("iid", obj.optString("id")),
                        title = obj.optString("title", "Untitled GitLab Issue"),
                        body = obj.optString("description", ""),
                        labels = labelsList,
                        comments = emptyList(),
                        createdAt = Instant.now()
                    )
                )
            }
            records
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun quota(): ApiQuotaState = currentQuota
}
