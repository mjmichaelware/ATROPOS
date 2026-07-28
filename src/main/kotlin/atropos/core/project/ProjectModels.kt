package atropos.core.project

import java.time.Instant
import java.util.UUID

data class RepositoryBinding(
    val repoRoot: String,
    val branch: String = "",
    val baselineCommit: String = "",
    val dirtyFingerprint: String = ""
)

data class ProjectRecord(
    val id: String = "project-${UUID.randomUUID().toString().take(12)}",
    val name: String,
    val kind: String,
    val binding: RepositoryBinding,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

data class ProjectRegistrationResult(
    val created: Boolean,
    val record: ProjectRecord
)
