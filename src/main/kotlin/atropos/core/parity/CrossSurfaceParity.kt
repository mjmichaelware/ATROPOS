/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.parity

/**
 * Universal cross-surface status definitions and project models shared across
 * CLI, Web, and Android surfaces.
 *
 * ## Research: Automated JSON Schema & TypeScript Generation
 * Research automated JSON schema export and TypeScript type generation pipelines
 * (e.g. Kotlinx Serialization JSON Schema / KSP TypeScript generator) to ensure
 * web frontends and mobile presentation layers remain mathematically synchronized
 * with Kotlin backend models without manual serialization boilerplate.
 */
enum class StatusVocabulary(val wireName: String, val display: String) {
    IDLE("idle", "Idle"),
    PLANNING("planning", "Planning"),
    WAITING("waiting", "Waiting"),
    WORKING("working", "Working"),
    REVIEW_REQUIRED("review-required", "Review Required"),
    BLOCKED("blocked", "Blocked"),
    COMPLETED("completed", "Completed"),
    FAILED("failed", "Failed"),
    CANCELLED("cancelled", "Cancelled"),

    // Canonical HOE signal aliases
    ATTESTED("attested", "Attested"),
    EXECUTING("executing", "Executing"),
    DEFENDING("defending", "Defending"),
    VERIFYING("verifying", "Verifying"),
    ERROR("error", "Error");

    companion object {
        fun fromString(value: String): StatusVocabulary? {
            val normalized = value.trim().lowercase().replace('_', '-').replace(' ', '-')
            return entries.firstOrNull { it.wireName == normalized || it.name.lowercase() == normalized }
        }
    }
}

data class ProjectIdentity(
    val id: String,
    val name: String,
    val path: String,
    val branch: String? = null,
    val headSha: String? = null
)

object CrossSurfaceParity {
    fun validateStatus(status: String): Boolean =
        StatusVocabulary.fromString(status) != null

    fun getStatusDisplay(status: String): String =
        StatusVocabulary.fromString(status)?.display ?: "Unknown"

    fun validateProjectIdentity(project: ProjectIdentity): Boolean =
        project.id.isNotBlank() && project.name.isNotBlank() && project.path.isNotBlank()
}
