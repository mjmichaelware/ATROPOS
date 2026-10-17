package atropos.core.github

import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.Instant
import org.json.JSONObject

data class CheckRunParams(
    val owner: String,
    val repo: String,
    val headSha: String,
    val checkName: String,
    val conclusion: String,
    val summaryTitle: String,
    val markdownDetails: String
)

class GitHubActionsCheckDispatcher(private val tokenProvider: () -> String) {
    fun createCheckRun(params: CheckRunParams): Long {
        val url = URI("https://api.github.com/repos/${params.owner}/${params.repo}/check-runs").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer ${tokenProvider()}")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true

        val payload = JSONObject().apply {
            put("name", params.checkName)
            put("head_sha", params.headSha)
            put("status", "completed")
            put("conclusion", params.conclusion)
            put("completed_at", Instant.now().toString())
            put("output", JSONObject().apply {
                put("title", params.summaryTitle)
                put("summary", params.markdownDetails)
            })
        }

        conn.outputStream.use { it.write(payload.toString().toByteArray(StandardCharsets.UTF_8)) }
        val status = conn.responseCode
        if (status !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $status"
            throw IllegalStateException("GitHub API Check-Run failed ($status): $err")
        }

        val resp = JSONObject(conn.inputStream.bufferedReader().readText())
        return resp.getLong("id")
    }

    fun generateCiWorkflowYaml(): String = """
        name: Atropos Remote Verification
        on: [push, pull_request]
        jobs:
          verify:
            runs-on: ubuntu-latest
            steps:
              - uses: actions/checkout@v4
              - name: Set up JDK 21
                uses: actions/setup-java@v4
                with:
                  distribution: 'temurin'
                  java-version: '21'
              - name: Assemble & Verify Invariants
                run: |
                  ./gradlew compileKotlin compileTestKotlin --continue
    """.trimIndent()
}
