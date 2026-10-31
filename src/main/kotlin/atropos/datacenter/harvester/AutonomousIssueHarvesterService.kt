package atropos.datacenter.harvester

import atropos.datacenter.harvester.sources.GitHubIssueSource
import atropos.datacenter.harvester.sources.PlatformIssueSource
import atropos.datacenter.shipper.HuggingFaceShipper
import atropos.datacenter.storage.NeurodevelopmentalTelemetryStreamer
import atropos.datacenter.storage.TelemetryStreamBatch
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant

/**
 * Summary of a single harvested and triaged item.
 */
data class HarvesterItemSummary(
    val id: String,
    val title: String,
    val platform: String,
    val repository: String,
    val triageSuccess: Boolean,
    val patchDiffSummary: String,
    val executionTimeMs: Long
)

/**
 * Telemetry summary of an autonomous harvest run across authorized repositories.
 */
data class HarvesterRunSummary(
    val batchId: String,
    val targetRepos: List<String>,
    val totalItemsHarvested: Int,
    val triagedItemsCount: Int,
    val quotaRemaining: Int,
    val isThrottled: Boolean,
    val huggingFaceSynced: Boolean,
    val supabaseSynced: Boolean,
    val items: List<HarvesterItemSummary>,
    val timestampEpochMs: Long = System.currentTimeMillis()
)

/**
 * Coordinates end-to-end GitHub/GitLab/Jira issue harvesting, ephemeral worktree
 * triage analysis, dopamine reinforcement reward calculus, and real-time streaming
 * to the Hugging Face and Supabase neurodevelopmental telemetry sinks.
 */
class AutonomousIssueHarvesterService(
    private val sources: List<PlatformIssueSource> = listOf(GitHubIssueSource()),
    private val consentGate: RepositoryConsentGate = RepositoryConsentGate(),
    private val triagePipeline: AutonomousIssueTriagePipeline = AutonomousIssueTriagePipeline(),
    private val streamer: NeurodevelopmentalTelemetryStreamer = NeurodevelopmentalTelemetryStreamer.defaultStreamer,
    private val shipper: HuggingFaceShipper = HuggingFaceShipper()
) {
    private val pollingDaemon = UnifiedPollingDaemon(sources, consentGate)

    /**
     * Executes the end-to-end pull, triage, and data center sync pipeline.
     */
    fun executeHarvesterRun(targetRepos: List<String> = listOf("mjmichaelware/ATROPOS")): HarvesterRunSummary {
        val batchId = "batch-harvest-${System.currentTimeMillis()}"
        val harvestedIssues = pollingDaemon.pollAuthorizedTargets(targetRepos)

        val itemSummaries = mutableListOf<HarvesterItemSummary>()
        val telemetryRecords = mutableListOf<Map<String, Any?>>()
        val jsonLinesBuilder = StringBuilder()

        for (issue in harvestedIssues) {
            val sanitizedIssue = sanitizeIssue(issue)
            val worktree = triagePipeline.provisionEphemeralWorktree("https://github.com/${sanitizedIssue.repository}")
            val triageResult = try {
                triagePipeline.executeRepairDag(sanitizedIssue, worktree)
            } finally {
                triagePipeline.cleanupWorktree(worktree)
            }

            itemSummaries.add(
                HarvesterItemSummary(
                    id = sanitizedIssue.issueId,
                    title = sanitizedIssue.title,
                    platform = sanitizedIssue.platform,
                    repository = sanitizedIssue.repository,
                    triageSuccess = triageResult.success,
                    patchDiffSummary = triageResult.generatedPatchDiff.take(120),
                    executionTimeMs = triageResult.executionTimeMs
                )
            )

            val payloadMap = mapOf<String, Any?>(
                "platform" to sanitizedIssue.platform,
                "repository" to sanitizedIssue.repository,
                "issueId" to sanitizedIssue.issueId,
                "title" to sanitizedIssue.title,
                "labels" to sanitizedIssue.labels,
                "triageSuccess" to triageResult.success,
                "patchDiff" to triageResult.generatedPatchDiff,
                "verificationLog" to triageResult.verificationLog,
                "executionTimeMs" to triageResult.executionTimeMs,
                "neuralRewards" to mapOf(
                    "baseSignal" to if (triageResult.success) 5.0 else -1.0,
                    "cleanVerificationBonus" to if (triageResult.success) 2.5 else 0.0
                )
            )

            val payloadJson = JSONObject(payloadMap).toString()
            val payloadHash = sha256(payloadJson)
            val recordId = "tel-harv-${System.currentTimeMillis()}-${sanitizedIssue.issueId}"
            val instanceId = "instance_${sanitizedIssue.repository.replace('/', '__')}-issue-${sanitizedIssue.issueId}"

            val fullRecord = mapOf<String, Any?>(
                "recordId" to recordId,
                "timestamp" to System.currentTimeMillis(),
                "instanceId" to instanceId,
                "h0Lineage" to sha256(instanceId),
                "kind" to "GITHUB_PULL_HARVEST_TRIAGE",
                "reward" to if (triageResult.success) 5.0 else -1.0,
                "payload" to payloadMap,
                "payloadHash" to payloadHash
            )

            telemetryRecords.add(fullRecord)
            jsonLinesBuilder.append(JSONObject(fullRecord).toString()).append("\n")
        }

        // Quota metrics from sources
        val gitHubSource = sources.filterIsInstance<GitHubIssueSource>().firstOrNull()
        val quota = gitHubSource?.quota()

        // Merkle root of batch
        val merkleRoot = sha256(jsonLinesBuilder.toString())

        val batch = TelemetryStreamBatch(
            batchId = batchId,
            trajectoryId = "traj-harvest-${System.currentTimeMillis()}",
            recordsCount = telemetryRecords.size,
            payloadJson = jsonLinesBuilder.toString().trim(),
            merkleRoot = merkleRoot
        )

        // Stream to Hugging Face + Supabase backup under Soft-Fail Law
        val syncReport = streamer.dispatchAndVerifySync(batch)
        val shipperSuccess = shipper.streamBatch(telemetryRecords)

        return HarvesterRunSummary(
            batchId = batchId,
            targetRepos = targetRepos,
            totalItemsHarvested = harvestedIssues.size,
            triagedItemsCount = itemSummaries.count { it.triageSuccess },
            quotaRemaining = quota?.callsRemaining ?: 5000,
            isThrottled = quota?.isThrottled ?: false,
            huggingFaceSynced = syncReport.primaryHfSuccess || shipperSuccess,
            supabaseSynced = syncReport.backupSupabaseSuccess,
            items = itemSummaries
        )
    }

    private fun sanitizeIssue(issue: IssueRecord): IssueRecord {
        val competitorPattern = Regex("(?i)\\b(claude|copilot|cursor|cody|tabnine|codeium|supermaven|phind)\\b")
        return issue.copy(
            title = issue.title.replace(competitorPattern, "[REDACTED_PROVIDER]"),
            body = issue.body.replace(competitorPattern, "[REDACTED_PROVIDER]"),
            labels = issue.labels.map { it.replace(competitorPattern, "[REDACTED_PROVIDER]") }
        )
    }

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        val defaultService = AutonomousIssueHarvesterService()
    }
}
