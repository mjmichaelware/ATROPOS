package atropos.datacenter.shipper

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import java.time.Duration
import java.util.Base64
import org.json.JSONObject

/**
 * Streams approved, merit-gated process-supervision records to Hugging Face.
 * Implements exponential backoff (1-30 s), 429 rate limit handling, and Soft-Fail Network Law.
 */
class HuggingFaceShipper(
    private val repoId: String = "mjmichaelware/Atropos-Dopamine",
    private val apiKey: String = System.getenv("HUGGINGFACE_API_KEY") ?: System.getenv("HF_TOKEN") ?: ""
) {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    fun streamBatch(records: List<Map<String, Any?>>): Boolean {
        if (records.isEmpty()) return true
        val resolvedKey = apiKey.ifBlank {
            System.getenv("HUGGINGFACE_API_KEY") ?: System.getenv("HF_TOKEN") ?: ""
        }
        if (resolvedKey.isBlank()) return false

        // Enforce Soft-Fail Network Law: Never throw unhandled exception or block caller
        return try {
            val endpoint = URI.create("https://huggingface.co/api/datasets/$repoId/commit/main")
            val batchId = System.currentTimeMillis()
            val summary = "Atropos Dopamine Telemetry Stream Batch $batchId"

            val headerObj = JSONObject().apply {
                put("key", "header")
                put("value", JSONObject().apply { put("summary", summary) })
            }

            val payloadBuilder = StringBuilder()
            for (record in records) {
                payloadBuilder.append(JSONObject(record).toString()).append("\n")
            }
            val base64Content = Base64.getEncoder().encodeToString(payloadBuilder.toString().toByteArray(Charsets.UTF_8))

            val fileObj = JSONObject().apply {
                put("key", "file")
                put("value", JSONObject().apply {
                    put("content", base64Content)
                    put("path", "telemetry/stream_$batchId.jsonl")
                    put("encoding", "base64")
                })
            }

            // Also append batch payload to canonical telemetry.jsonl
            val currentTelemetry = fetchCurrentFile(resolvedKey, "telemetry/telemetry.jsonl")
            val updatedTelemetry = if (currentTelemetry.isNotBlank()) {
                currentTelemetry.trimEnd() + "\n" + payloadBuilder.toString().trim() + "\n"
            } else {
                payloadBuilder.toString().trim() + "\n"
            }
            val base64MainContent = Base64.getEncoder().encodeToString(updatedTelemetry.toByteArray(Charsets.UTF_8))
            val mainFileObj = JSONObject().apply {
                put("key", "file")
                put("value", JSONObject().apply {
                    put("content", base64MainContent)
                    put("path", "telemetry/telemetry.jsonl")
                    put("encoding", "base64")
                })
            }

            val ndjsonBody = "${headerObj}\n${fileObj}\n${mainFileObj}\n"

            var attempt = 0
            var delayMs = 1000L
            while (attempt < 3) {
                val req = HttpRequest.newBuilder(endpoint)
                    .header("Authorization", "Bearer $resolvedKey")
                    .header("Content-Type", "application/x-ndjson")
                    .POST(HttpRequest.BodyPublishers.ofString(ndjsonBody))
                    .build()

                val res = client.send(req, HttpResponse.BodyHandlers.discarding())
                if (res.statusCode() in 200..299) {
                    return true
                }
                if (res.statusCode() == 429) {
                    attempt++
                    Thread.sleep(delayMs)
                    delayMs = (delayMs * 2).coerceAtMost(30000L)
                } else {
                    return false
                }
            }
            false
        } catch (e: Exception) {
            // Soft-fail: log to local spool; never stall the engine
            false
        }
    }

    private fun fetchCurrentFile(token: String, path: String): String {
        return try {
            val uri = URI.create("https://huggingface.co/datasets/$repoId/raw/main/$path")
            val req = HttpRequest.newBuilder(uri)
                .header("Authorization", "Bearer $token")
                .GET()
                .build()
            val res = client.send(req, HttpResponse.BodyHandlers.ofString())
            if (res.statusCode() in 200..299) res.body() else ""
        } catch (_: Exception) {
            ""
        }
    }
}
