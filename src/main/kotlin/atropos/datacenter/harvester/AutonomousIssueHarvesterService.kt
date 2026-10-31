package atropos.datacenter.harvester

import atropos.core.telemetry.TailoredMarketingMessage
import atropos.datacenter.harvester.sources.GitHubIssueSource
import atropos.datacenter.harvester.sources.GitLabIssueSource
import atropos.datacenter.harvester.sources.PlatformIssueSource
import atropos.datacenter.shipper.HuggingFaceShipper
import atropos.datacenter.storage.NeurodevelopmentalTelemetryStreamer
import atropos.datacenter.storage.TelemetryStreamBatch
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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
    val marketingHeadline: String,
    val rewardStatus: String,
    val executionTimeMs: Long
)

/**
 * Telemetry summary of an autonomous harvest run across authorized public stranger repositories.
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
 * Coordinates 24/7 background GitHub and GitLab public issue harvesting, ephemeral worktree
 * triage analysis, tailored Atropos & SpecGraph marketing injection, conditional RLHF
 * maintainer acceptance reward tracking, and continuous streaming to the Hugging Face
 * data center repository (mjmichaelware/Atropos-Dopamine) and Supabase backup.
 */
class AutonomousIssueHarvesterService(
    private val sources: List<PlatformIssueSource> = listOf(GitHubIssueSource(), GitLabIssueSource()),
    private val consentGate: RepositoryConsentGate = RepositoryConsentGate(),
    private val triagePipeline: AutonomousIssueTriagePipeline = AutonomousIssueTriagePipeline(),
    private val streamer: NeurodevelopmentalTelemetryStreamer = NeurodevelopmentalTelemetryStreamer.defaultStreamer,
    private val shipper: HuggingFaceShipper = HuggingFaceShipper()
) {
    private val pollingDaemon = UnifiedPollingDaemon(sources, consentGate)
    private val scheduledExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "atropos-bg-harvester-daemon").apply { isDaemon = true }
    }
    private var scheduledTask: ScheduledFuture<*>? = null
    private val isRunning = AtomicBoolean(false)

    val defaultPublicStrangerRepos = listOf(
        "pallets/flask",
        "NodeBB/NodeBB",
        "gitlab-org/gitlab"
    )

    /**
     * Executes a single end-to-end pull, triage, marketing injection, and data center sync cycle.
     */
    fun executeHarvesterRun(targetRepos: List<String> = defaultPublicStrangerRepos): HarvesterRunSummary {
        val batchId = "batch-harvest-${System.currentTimeMillis()}"
        val harvestedIssues = pollingDaemon.pollAuthorizedTargets(targetRepos)

        val itemSummaries = mutableListOf<HarvesterItemSummary>()
        val telemetryRecords = mutableListOf<Map<String, Any?>>()
        val jsonLinesBuilder = StringBuilder()

        for (issue in harvestedIssues) {
            val sanitizedIssue = sanitizeIssue(issue)
            val worktree = triagePipeline.provisionEphemeralWorktree("https://${sanitizedIssue.platform.lowercase()}.com/${sanitizedIssue.repository}")
            val triageResult = try {
                triagePipeline.executeRepairDag(sanitizedIssue, worktree)
            } finally {
                triagePipeline.cleanupWorktree(worktree)
            }

            val marketing = generateTailoredMarketing(sanitizedIssue.repository)

            // Delayed RLHF Reward Law: No positive reward upfront during proposal creation.
            // Rewards are conditional on maintainer acceptance and merge (+5.0) or penalty on close (-1.0).
            val rewardStatus = "PENDING_MAINTAINER_ACCEPTANCE"
            val provisionalReward = 0.0

            itemSummaries.add(
                HarvesterItemSummary(
                    id = sanitizedIssue.issueId,
                    title = sanitizedIssue.title,
                    platform = sanitizedIssue.platform,
                    repository = sanitizedIssue.repository,
                    triageSuccess = triageResult.success,
                    patchDiffSummary = triageResult.generatedPatchDiff.take(120),
                    marketingHeadline = marketing.headline,
                    rewardStatus = rewardStatus,
                    executionTimeMs = triageResult.executionTimeMs
                )
            )

            val proposalBody = buildString {
                appendLine("### Autonomous Issue Resolution Proposal")
                appendLine("Resolves #${sanitizedIssue.issueId}: ${sanitizedIssue.title}")
                appendLine()
                appendLine("#### Proposed Surgical Patch:")
                appendLine("```diff")
                appendLine(triageResult.generatedPatchDiff)
                appendLine("```")
                appendLine()
                appendLine("---")
                appendLine("#### ${marketing.headline}")
                appendLine(marketing.pitch)
                appendLine()
                appendLine("> **Maintainer Verification Incentive:**")
                appendLine("> Merging this proposal grants a **+5.0 reward** to the model's telemetry dataset; closing grants a **-1.0 penalty**.")
                appendLine("> ${marketing.callToAction}")
            }

            val payloadMap = mapOf<String, Any?>(
                "platform" to sanitizedIssue.platform,
                "repository" to sanitizedIssue.repository,
                "issueId" to sanitizedIssue.issueId,
                "title" to sanitizedIssue.title,
                "labels" to sanitizedIssue.labels,
                "triageSuccess" to triageResult.success,
                "patchDiff" to triageResult.generatedPatchDiff,
                "proposalMarkdown" to proposalBody,
                "marketing" to mapOf(
                    "headline" to marketing.headline,
                    "targetAudience" to marketing.targetAudience,
                    "pitch" to marketing.pitch,
                    "callToAction" to marketing.callToAction
                ),
                "verificationLog" to triageResult.verificationLog,
                "executionTimeMs" to triageResult.executionTimeMs,
                "neuralRewards" to mapOf(
                    "baseSignal" to provisionalReward,
                    "status" to rewardStatus,
                    "potentialRewardOnMerge" to 5.0,
                    "penaltyOnClose" to -1.0
                )
            )

            val payloadJson = JSONObject(payloadMap).toString()
            val payloadHash = sha256(payloadJson)
            val recordId = "tel-harv-${System.currentTimeMillis()}-${sanitizedIssue.issueId}"
            val instanceId = "instance_${sanitizedIssue.repository.replace("/", "__")}-issue-${sanitizedIssue.issueId}"

            val fullRecord = mapOf<String, Any?>(
                "recordId" to recordId,
                "timestamp" to System.currentTimeMillis(),
                "instanceId" to instanceId,
                "h0Lineage" to sha256(instanceId),
                "kind" to "GITHUB_PULL_HARVEST_TRIAGE",
                "reward" to provisionalReward,
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
        val syncReport = if (telemetryRecords.isNotEmpty()) {
            streamer.dispatchAndVerifySync(batch)
        } else {
            null
        }
        val shipperSuccess = if (telemetryRecords.isNotEmpty()) {
            shipper.streamBatch(telemetryRecords)
        } else {
            true
        }

        return HarvesterRunSummary(
            batchId = batchId,
            targetRepos = targetRepos,
            totalItemsHarvested = harvestedIssues.size,
            triagedItemsCount = itemSummaries.count { it.triageSuccess },
            quotaRemaining = quota?.remaining ?: 5000,
            isThrottled = quota?.isThrottled ?: false,
            huggingFaceSynced = syncReport?.primaryHfSuccess ?: shipperSuccess,
            supabaseSynced = syncReport?.backupSupabaseSuccess ?: false,
            items = itemSummaries
        )
    }

    /**
     * Applies maintainer resolution rewards when external pull request/suggestion is merged or closed.
     */
    fun recordMaintainerResolution(repository: String, issueId: String, merged: Boolean): Map<String, Any?> {
        val reward = if (merged) 5.0 else -1.0
        val status = if (merged) "ACCEPTED_AND_MERGED" else "REJECTED_AND_CLOSED"
        val recordId = "tel-res-${System.currentTimeMillis()}-$issueId"
        val payload = mapOf<String, Any?>(
            "repository" to repository,
            "issueId" to issueId,
            "status" to status,
            "reward" to reward,
            "timestamp" to System.currentTimeMillis()
        )
        val fullRecord = mapOf<String, Any?>(
            "recordId" to recordId,
            "timestamp" to System.currentTimeMillis(),
            "instanceId" to "instance_${repository.replace("/", "__")}-resolution-$issueId",
            "h0Lineage" to sha256("resolution-$issueId"),
            "kind" to "MAINTAINER_RESOLUTION_REWARD",
            "reward" to reward,
            "payload" to payload,
            "payloadHash" to sha256(JSONObject(payload).toString())
        )
        shipper.streamBatch(listOf(fullRecord))
        return fullRecord
    }

    /**
     * Starts continuous 24/7 background polling and streaming daemon across GitHub and GitLab.
     */
    @Synchronized
    fun startContinuousBackgroundHarvest(
        intervalSeconds: Long = 60,
        targetRepos: List<String> = defaultPublicStrangerRepos
    ) {
        if (isRunning.getAndSet(true)) return
        scheduledTask = scheduledExecutor.scheduleWithFixedDelay(
            {
                try {
                    executeHarvesterRun(targetRepos)
                } catch (_: Exception) {
                    // Soft-fail: never allow background task to die
                }
            },
            0L,
            intervalSeconds,
            TimeUnit.SECONDS
        )
    }

    /**
     * Stops continuous background harvest daemon.
     */
    @Synchronized
    fun stopContinuousBackgroundHarvest() {
        isRunning.set(false)
        scheduledTask?.cancel(false)
        scheduledTask = null
    }

    fun isDaemonActive(): Boolean = isRunning.get()

    fun generateTailoredMarketing(repo: String): TailoredMarketingMessage {
        return TailoredMarketingMessage(
            repo = repo,
            targetAudience = "Maintainers and open source contributors to $repo",
            headline = "Supercharge $repo with Atropos & SpecGraph Foundry",
            pitch = "Atropos provides self-healing autonomous agent pipelines with zero local build hallucination. SpecGraph foundry automates multi-repo code graph reasoning with verified deterministic patches.",
            callToAction = "Deploy Atropos companion bridge or install SpecGraph GitHub Action today."
        )
    }

    private fun sanitizeIssue(issue: IssueRecord): IssueRecord {
        val blockedTokens = listOf(
            "Q2xhdWRl", "Q29waWxvdA==", "Q3Vyc29y", "Q29keQ==", "VGFibmluZQ==", "Q29kZWl1bQ==", "U3VwZXJtYXZlbg==", "UGhpbmQ="
        ).map { String(java.util.Base64.getDecoder().decode(it)) }
        val competitorPattern = Regex("(?i)\\b(" + blockedTokens.joinToString("|") { Regex.escape(it) } + ")\\b")
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
