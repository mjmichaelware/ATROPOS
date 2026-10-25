package atropos.datacenter.harvester

/**
 * Repository Consent & Allowlist Gate.
 * Enforces Zero Spam / Explicit Consent law.
 * Restricts all polling and PR actions to authorized targets or repos with explicit opt-in tags.
 */
class RepositoryConsentGate(
    private val explicitAllowlist: List<String> = listOf("MichaelAPWare/ATROPOS", "MichaelAPWare/atropos-engine")
) {
    private val optInTags = setOf("atropos-opt-in", "ai-contributions-welcome")

    fun checkConsent(platform: String, repoIdentifier: String, tags: List<String> = emptyList()): ConsentEvaluation {
        if (explicitAllowlist.contains(repoIdentifier)) {
            return ConsentEvaluation(
                isAuthorized = true,
                platform = platform,
                repoIdentifier = repoIdentifier,
                reason = "Target '$repoIdentifier' is present on explicit owner allowlist."
            )
        }

        if (tags.any { optInTags.contains(it.lowercase()) }) {
            return ConsentEvaluation(
                isAuthorized = true,
                platform = platform,
                repoIdentifier = repoIdentifier,
                reason = "Target '$repoIdentifier' contains verified opt-in tag."
            )
        }

        return ConsentEvaluation(
            isAuthorized = false,
            platform = platform,
            repoIdentifier = repoIdentifier,
            reason = "Target '$repoIdentifier' is not allowlisted and lacks explicit opt-in."
        )
    }
}
