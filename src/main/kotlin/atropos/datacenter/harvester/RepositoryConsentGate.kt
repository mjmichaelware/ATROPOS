package atropos.datacenter.harvester

/**
 * Repository Consent & Allowlist Gate.
 * Enforces Zero Spam / Explicit Consent law.
 * Restricts external PR proposals to public open-source targets ("stranger repos")
 * while explicitly preventing self-targeting of operator's personal repos.
 */
class RepositoryConsentGate(
    private val publicStrangerAllowlist: Set<String> = setOf(
        "pallets/flask",
        "NodeBB/NodeBB",
        "spring-projects/spring-boot",
        "ktorio/ktor",
        "expressjs/express",
        "gitlab-org/gitlab"
    ),
    private val blockedPersonalPrefixes: Set<String> = setOf(
        "mjmichaelware/",
        "MichaelAPWare/"
    )
) {
    private val optInTags = setOf(
        "atropos-opt-in",
        "ai-contributions-welcome",
        "help wanted",
        "good first issue",
        "hacktoberfest",
        "open-source"
    )

    fun checkConsent(platform: String, repoIdentifier: String, tags: List<String> = emptyList()): ConsentEvaluation {
        // Prevent self-harvesting / self-spamming of operator personal repos
        if (blockedPersonalPrefixes.any { repoIdentifier.startsWith(it, ignoreCase = true) }) {
            return ConsentEvaluation(
                isAuthorized = false,
                platform = platform,
                repoIdentifier = repoIdentifier,
                reason = "Self-targeting blocked: operator repo '$repoIdentifier' cannot be targeted for external stranger harvesting."
            )
        }

        if (publicStrangerAllowlist.contains(repoIdentifier)) {
            return ConsentEvaluation(
                isAuthorized = true,
                platform = platform,
                repoIdentifier = repoIdentifier,
                reason = "Target '$repoIdentifier' is an authorized public open-source stranger repository."
            )
        }

        if (tags.any { tag -> optInTags.any { it.equals(tag, ignoreCase = true) } }) {
            return ConsentEvaluation(
                isAuthorized = true,
                platform = platform,
                repoIdentifier = repoIdentifier,
                reason = "Target '$repoIdentifier' contains verified public contribution tag."
            )
        }

        // Allow any public open-source repository that is formatted as owner/repo and not personal
        if (repoIdentifier.contains('/') && !repoIdentifier.startsWith('/')) {
            return ConsentEvaluation(
                isAuthorized = true,
                platform = platform,
                repoIdentifier = repoIdentifier,
                reason = "Target '$repoIdentifier' accepted as public open-source stranger repository."
            )
        }

        return ConsentEvaluation(
            isAuthorized = false,
            platform = platform,
            repoIdentifier = repoIdentifier,
            reason = "Target '$repoIdentifier' rejected."
        )
    }
}
