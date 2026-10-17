package atropos.core.github

import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

data class PullRequestRecord(val number: Int, val title: String, val branch: String, val author: String)

class GitHubRemoteMcpTool(private val tokenProvider: () -> String) {
    private fun openConnection(endpoint: String, method: String): HttpURLConnection {
        val url = URI("https://api.github.com$endpoint").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("Authorization", "Bearer ${tokenProvider()}")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        return conn
    }

    fun listOpenPullRequests(owner: String, repo: String): List<PullRequestRecord> {
        atropos.core.thinking.Thinking.step("mcp", "Querying GitHub PRs for $owner/$repo")
        val conn = openConnection("/repos/$owner/$repo/pulls?state=open", "GET")
        if (conn.responseCode !in 200..299) return emptyList()
        val body = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        val arr = JSONArray(body)
        val records = (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            PullRequestRecord(
                number = obj.getInt("number"),
                title = obj.getString("title"),
                branch = obj.getJSONObject("head").getString("ref"),
                author = obj.getJSONObject("user").getString("login")
            )
        }
        atropos.core.thinking.Thinking.detail("mcp", "Found ${records.size} open PRs on $owner/$repo")
        return records
    }

    fun createPullRequest(owner: String, repo: String, head: String, base: String, title: String, body: String): Int {
        atropos.core.thinking.Thinking.step("mcp", "Creating GitHub PR: $title")
        val conn = openConnection("/repos/$owner/$repo/pulls", "POST")
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        val payload = JSONObject().apply {
            put("title", title)
            put("head", head)
            put("base", base)
            put("body", body)
        }
        conn.outputStream.use { it.write(payload.toString().toByteArray(StandardCharsets.UTF_8)) }
        if (conn.responseCode !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP ${conn.responseCode}"
            throw IllegalStateException("PR Creation failed: $err")
        }
        val resp = JSONObject(conn.inputStream.bufferedReader().readText())
        val prNumber = resp.getInt("number")
        atropos.core.thinking.Thinking.detail("mcp", "Created PR #$prNumber ($head -> $base)")
        return prNumber
    }

    fun postComment(owner: String, repo: String, issueOrPrNumber: Int, comment: String): Boolean {
        atropos.core.thinking.Thinking.step("mcp", "Posting comment on PR/issue #$issueOrPrNumber")
        val conn = openConnection("/repos/$owner/$repo/issues/$issueOrPrNumber/comments", "POST")
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        val payload = JSONObject().apply { put("body", comment) }
        conn.outputStream.use { it.write(payload.toString().toByteArray(StandardCharsets.UTF_8)) }
        val ok = conn.responseCode in 200..299
        atropos.core.thinking.Thinking.detail("mcp", "GitHub comment posted on #$issueOrPrNumber: ok=$ok")
        return ok
    }
}
