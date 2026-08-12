package atropos.core.project

import java.time.Instant
import java.util.UUID

data class RepositoryBinding(
    val repoRoot: String,
    val branch: String = "",
    val baselineCommit: String = "",
    val dirtyFingerprint: String = ""
)

/**
 * Source Document 4 §3.3 status vocabulary, verbatim.
 *
 * Status names describe human progress rather than scheduler mechanics, and
 * the same vocabulary is used by the CLI, the web surface and the register so
 * a project cannot read differently depending on where it is displayed.
 */
enum class ProjectStatus {
    IDLE,
    PLANNING,
    WAITING,
    WORKING,
    REVIEW_REQUIRED,
    BLOCKED,
    COMPLETED,
    FAILED,
    CANCELLED;

    /** Wire form shared with the web surface: `review-required`, not `REVIEW_REQUIRED`. */
    val canonical: String get() = name.lowercase().replace('_', '-')

    val terminal: Boolean get() = this in setOf(COMPLETED, FAILED, CANCELLED)

    companion object {
        fun fromCanonical(value: String): ProjectStatus? =
            entries.firstOrNull { it.canonical.equals(value.trim(), ignoreCase = true) }
    }
}

/**
 * §3.0: a project owns Objective, Plan, Resources, Execution, Verification,
 * Artifacts and History.
 *
 * Only what the runtime can genuinely fill is stored. An empty [objective]
 * means the operator did not state one — never that the store supplied a
 * placeholder on their behalf.
 */
data class ProjectRecord(
    val id: String = "project-${UUID.randomUUID().toString().take(12)}",
    val name: String,
    val kind: String,
    val binding: RepositoryBinding,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    /** §0.1 Q1 — "What am I trying to accomplish?", stated by the human. */
    val objective: String = "",
    val status: ProjectStatus = ProjectStatus.IDLE,
    /** Agent-queue entry ids this project owns (§2.3). */
    val workItemIds: List<String> = emptyList(),
    /** Evidence ids linked to this project (§10.3). */
    val evidenceIds: List<String> = emptyList()
) {
    /**
     * §3.4: "Completion requires evidence, not elapsed time."
     *
     * False means the project claims completion that nothing can corroborate.
     * Surfaces are expected to show that rather than accept the claim.
     */
    val completionIsVerifiable: Boolean
        get() = status != ProjectStatus.COMPLETED || evidenceIds.isNotEmpty()
}

data class ProjectRegistrationResult(
    val created: Boolean,
    val record: ProjectRecord
)

/** One entry in a project's permanent, append-only history (§2.9). */
data class ProjectEvent(
    val timestamp: Instant,
    val event: String,
    val actor: String,
    val message: String
)
