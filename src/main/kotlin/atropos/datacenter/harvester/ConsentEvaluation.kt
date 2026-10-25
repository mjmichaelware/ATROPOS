package atropos.datacenter.harvester

/**
 * Consent evaluation result from RepositoryConsentGate.
 */
data class ConsentEvaluation(
    val isAuthorized: Boolean,
    val platform: String,
    val repoIdentifier: String,
    val reason: String
)
