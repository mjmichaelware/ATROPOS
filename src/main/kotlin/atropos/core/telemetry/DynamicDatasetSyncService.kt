/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

import atropos.core.ProviderHttpClient
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * SD7-T1-TEL08: Dynamic Dataset Streaming Client.
 *
 * Streams compressed Parquet and JSONL telemetry batches into remote cloud datasets
 * asynchronously and without schema rigidity.
 * Enforces SOFT_FAIL_NETWORK_LAW: network disconnects, HTTP 429, or authentication
 * timeouts record RecordSkip(reason) and continue without stalling local execution.
 */
class DynamicDatasetSyncService(
    private val datasetRepo: String = System.getenv("ATROPOS_DATASET_REPO") ?: System.getenv("ATROPOS_HF_DATASET_REPO") ?: "atropos/telemetry-corpus",
    private val hfToken: String? = System.getenv("HF_TOKEN") ?: System.getenv("HUGGINGFACE_HUB_TOKEN"),
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
) {
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "atropos-dataset-sync").apply { isDaemon = true }
    }

    data class SyncResult(
        val success: Boolean,
        val recordsSynced: Int,
        val skipReason: String? = null,
        val httpStatus: Int? = null
    )

    /**
     * Dispatches batch payload asynchronously under Soft-Fail Network Law.
     */
    fun dispatchAsync(payload: TelemetryBatchPayload, onComplete: ((SyncResult) -> Unit)? = null) {
        executor.submit {
            val result = dispatchSync(payload)
            onComplete?.invoke(result)
        }
    }

    /**
     * Synchronous dispatch with backoff and soft-fail recovery.
     */
    fun dispatchSync(payload: TelemetryBatchPayload): SyncResult {
        if (payload.records.isEmpty()) {
            return SyncResult(true, 0)
        }

        val jsonLines = payload.toJsonLines()
        val endpoint = System.getenv("ATROPOS_DATASET_ENDPOINT") ?: "https://huggingface.co/api/datasets/$datasetRepo"

        var backoffMs = 1000L
        val maxBackoffMs = 30000L
        val maxAttempts = 3

        for (attempt in 1..maxAttempts) {
            try {
                val reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/x-ndjson")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonLines))

                if (!hfToken.isNullOrBlank()) {
                    reqBuilder.header("Authorization", "Bearer $hfToken")
                }

                val response = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString())
                val code = response.statusCode()

                when {
                    code in 200..299 -> {
                        return SyncResult(
                            success = true,
                            recordsSynced = payload.records.size,
                            httpStatus = code
                        )
                    }
                    code == 429 -> {
                        // Rate limit backoff
                        Thread.sleep(backoffMs)
                        backoffMs = (backoffMs * 2).coerceAtMost(maxBackoffMs)
                    }
                    else -> {
                        // Remote API returned error; soft fail and record skip
                        return SyncResult(
                            success = false,
                            recordsSynced = 0,
                            skipReason = "RecordSkip(HTTP $code: ${response.body().take(100)})",
                            httpStatus = code
                        )
                    }
                }
            } catch (e: Exception) {
                // Network unreachable or offline: SOFT_FAIL_NETWORK_LAW applies
                if (attempt == maxAttempts) {
                    return SyncResult(
                        success = false,
                        recordsSynced = 0,
                        skipReason = "RecordSkip(offline_or_network_error: ${e.message})"
                    )
                }
                try {
                    Thread.sleep(backoffMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
                backoffMs = (backoffMs * 2).coerceAtMost(maxBackoffMs)
            }
        }

        return SyncResult(
            success = false,
            recordsSynced = 0,
            skipReason = "RecordSkip(max_retries_exceeded)"
        )
    }

    fun shutdown() {
        executor.shutdown()
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
