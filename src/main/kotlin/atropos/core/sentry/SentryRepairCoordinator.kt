package atropos.core.sentry

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.json.JSONArray
import org.json.JSONObject

data class SentryStackFrame(val filename: String, val lineno: Int, val colno: Int, val function: String, val inApp: Boolean)
data class SentryCrashContext(val issueId: String, val title: String, val culpritPath: String, val errorType: String, val frames: List<SentryStackFrame>)
data class SentryRepairResult(val issueId: String, val targetFile: String, val patchPrompt: String, val proposedFixLocation: String)

class SentryRepairCoordinator(private val repoRoot: Path) {
    fun fetchIssueDetails(organizationSlug: String, issueId: String, authToken: String): SentryCrashContext {
        val uri = URI("https://sentry.io/api/0/issues/$issueId/events/latest/").toURL()
        val conn = uri.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $authToken")
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 8000
        conn.readTimeout = 8000

        val status = conn.responseCode
        if (status !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $status"
            throw IllegalStateException("Sentry API Error ($status): $err")
        }

        val body = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        val root = JSONObject(body)
        val title = root.optString("title", "Sentry Error $issueId")
        val culprit = root.optString("culprit", "")

        val frames = mutableListOf<SentryStackFrame>()
        val entries = root.optJSONArray("entries") ?: JSONArray()
        for (i in 0 until entries.length()) {
            val entry = entries.getJSONObject(i)
            if (entry.optString("type") == "exception") {
                val data = entry.optJSONObject("data")
                val values = data?.optJSONArray("values") ?: JSONArray()
                for (j in 0 until values.length()) {
                    val ex = values.getJSONObject(j)
                    val stack = ex.optJSONObject("stacktrace")
                    val rawFrames = stack?.optJSONArray("frames") ?: JSONArray()
                    for (k in 0 until rawFrames.length()) {
                        val f = rawFrames.getJSONObject(k)
                        frames.add(
                            SentryStackFrame(
                                filename = f.optString("filename", ""),
                                lineno = f.optInt("lineno", 0),
                                colno = f.optInt("colno", 0),
                                function = f.optString("function", "anonymous"),
                                inApp = f.optBoolean("inApp", false)
                            )
                        )
                    }
                }
            }
        }

        return SentryCrashContext(issueId, title, culprit, root.optString("message", "Exception"), frames)
    }

    fun extractInAppCulprit(context: SentryCrashContext): SentryStackFrame? =
        context.frames.reversed().firstOrNull { it.inApp && it.filename.isNotBlank() }
            ?: context.frames.lastOrNull()

    fun generateRepairPrompt(context: SentryCrashContext): SentryRepairResult {
        val culprit = extractInAppCulprit(context)
            ?: throw IllegalStateException("Could not identify culprit frame for Sentry issue ${context.issueId}")

        val sourceLines = try {
            val file = repoRoot.resolve(culprit.filename).normalize()
            if (Files.exists(file)) Files.readAllLines(file) else emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        val snippet = if (sourceLines.isNotEmpty() && culprit.lineno > 0) {
            val start = maxOf(0, culprit.lineno - 10)
            val end = minOf(sourceLines.size, culprit.lineno + 10)
            sourceLines.subList(start, end).mapIndexed { idx, line ->
                val curr = start + idx + 1
                val marker = if (curr == culprit.lineno) ">> " else "   "
                "$marker$curr: $line"
            }.joinToString("\n")
        } else {
            "[Source file ${culprit.filename} not found or line unknown]"
        }

        val prompt = """
            SENTRY ISSUE ${context.issueId}: ${context.title}
            Culprit: ${culprit.filename}:${culprit.lineno} in ${culprit.function}

            SURROUNDING CODE:
            $snippet

            OBJECTIVE: Generate minimal unified diff resolving this crash without altering unaffected contracts.
        """.trimIndent()

        return SentryRepairResult(context.issueId, culprit.filename, prompt, "${culprit.filename}:${culprit.lineno}")
    }
}
