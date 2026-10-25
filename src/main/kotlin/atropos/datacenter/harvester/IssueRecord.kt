package atropos.datacenter.harvester

import java.time.Instant

/**
 * Normalized issue record extracted from GitHub, GitLab, or Jira.
 */
data class IssueRecord(
    val platform: String,
    val repository: String,
    val issueId: String,
    val title: String,
    val body: String,
    val labels: List<String> = emptyList(),
    val comments: List<String> = emptyList(),
    val createdAt: Instant = Instant.now()
)
