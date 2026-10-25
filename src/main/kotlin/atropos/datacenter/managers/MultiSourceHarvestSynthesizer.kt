package atropos.datacenter.managers

import atropos.datacenter.harvester.IssueRecord
import java.time.Instant

/**
 * Manager 5: Multi-Source Harvest Synthesizer.
 * Oversees data scraping and issue extraction across GitHub, GitLab, Jira, and web sources.
 * Normalizes raw platform discussions and PR webhooks into structured training records.
 */
class MultiSourceHarvestSynthesizer {
    data class HarvestedTrainingRecord(
        val sourcePlatform: String,
        val issueId: String,
        val repository: String,
        val title: String,
        val problemStatement: String,
        val hintsAndDiscussions: List<String>,
        val reproducesLocally: Boolean,
        val synthesizedAt: Instant = Instant.now()
    )

    fun synthesizeIssue(rawIssue: IssueRecord, reproduces: Boolean): HarvestedTrainingRecord {
        return HarvestedTrainingRecord(
            sourcePlatform = rawIssue.platform,
            issueId = rawIssue.issueId,
            repository = rawIssue.repository,
            title = rawIssue.title,
            problemStatement = rawIssue.body,
            hintsAndDiscussions = rawIssue.comments,
            reproducesLocally = reproduces
        )
    }

    fun castVote(record: HarvestedTrainingRecord): ManagerVote {
        return if (record.problemStatement.isNotBlank()) {
            ManagerVote(
                managerName = "Manager-5-HarvestSynthesizer",
                vote = ManagerVote.Decision.APPROVE,
                confidence = 0.92,
                rationale = "Issue parsed from ${record.sourcePlatform}:${record.repository}; problem statement valid."
            )
        } else {
            ManagerVote(
                managerName = "Manager-5-HarvestSynthesizer",
                vote = ManagerVote.Decision.REJECT,
                confidence = 0.80,
                rationale = "Issue lacks actionable problem description; rejected."
            )
        }
    }
}
