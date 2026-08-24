/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.scavenge

import atropos.core.github.GitHubApiClient
import atropos.core.thinking.Narrate
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Public work someone has asked for help with, found rather than guessed at.
 *
 * The idea is the operator's: scan public repositories for pull requests stuck
 * on merge conflicts and for issues nobody has picked up, so ATROPOS can be
 * pointed at real work instead of at exercises.
 *
 * ## What this does and does not do
 *
 * It finds and ranks. It does not fork, push, comment, or open anything.
 *
 * That boundary is the whole design, not caution about a hard part. A tool
 * that discovers a thousand strangers' repositories and can also write to them
 * is a spam engine one loop away, and the people on the receiving end did not
 * ask ATROPOS for anything. Discovery is useful on its own -- the operator
 * reads the list, picks one, and drives it themselves through the same
 * commands they would use on their own repository.
 *
 * ## Only what the author already flagged
 *
 * Issues are searched by the labels maintainers use to invite help
 * (`good first issue`, `help wanted`). An unlabelled issue is not an
 * invitation, and treating every open issue as an opportunity is how this
 * feature would become a nuisance. Conflicted pull requests are the operator's
 * own by default -- someone else's half-finished branch is not a request for a
 * stranger to rebase it -- and widening that is an explicit argument.
 */
class GitHubScavenger(
    private val token: String? = System.getenv("ATROPOS_GITHUB_TOKEN")
        ?: System.getenv("GITHUB_TOKEN")
        ?: System.getenv("GH_TOKEN"),
    private val http: ((Request) -> Response)? = null,
    private val apiClient: GitHubApiClient = GitHubApiClient()
) {

    data class Request(val url: String, val token: String)

    data class Response(val status: Int, val body: String)

    enum class Kind {
        /** A pull request its author cannot land because the base moved under it. */
        CONFLICTED_PULL_REQUEST,

        /** An issue whose maintainer has labelled it as wanting help. */
        INVITED_ISSUE
    }

    /**
     * @param repository `owner/name`.
     * @param reference the issue or pull request number.
     * @param signal the specific reason this was surfaced, in the source's own
     *   words -- the label a maintainer applied, or the mergeable state GitHub
     *   reported. Never this class's opinion of the work.
     */
    data class Candidate(
        val kind: Kind,
        val repository: String,
        val reference: Int,
        val title: String,
        val url: String,
        val signal: String,
        val updatedAt: String
    )

    /**
     * @param owner whose repositories to search. Defaults to nobody: a scan
     *   with no owner is a scan of all of GitHub, and the useful version of
     *   this is pointed at a person or an organisation.
     * @param includeOthersConflicts whether to include conflicted pull requests
     *   outside [owner]'s repositories. Off by default, deliberately.
     */
    data class Query(
        val owner: String,
        val languages: List<String> = emptyList(),
        val limit: Int = DEFAULT_LIMIT,
        val includeOthersConflicts: Boolean = false
    )

    fun scavenge(query: Query): List<Candidate> {
        val authorization = if (http != null) {
            token?.takeIf(String::isNotBlank) ?: throw IllegalStateException(
                "no GitHub token: set ATROPOS_GITHUB_TOKEN (or GITHUB_TOKEN) to a token with " +
                    "`public_repo` scope. Searching GitHub anonymously is rate-limited to " +
                    "the point of uselessness."
            )
        } else null
        require(query.owner.isNotBlank() || query.includeOthersConflicts) {
            "a scavenge needs an owner to search; scanning all of GitHub finds noise"
        }

        Narrate.research.stage("scavenging public work for ${query.owner.ifBlank { "anyone" }}")

        val candidates = (invitedIssues(query, authorization) + conflictedPullRequests(query, authorization))
            // Most recently touched first. A three-year-old "help wanted" is
            // not an invitation any more, whatever the label still says.
            .sortedByDescending(Candidate::updatedAt)
            .distinctBy { it.repository to it.reference }
            .take(query.limit)

        Narrate.research.counted("candidates found", candidates.size)
        candidates.forEachIndexed { index, candidate ->
            Narrate.research.item(
                index = index + 1,
                total = candidates.size,
                id = "${candidate.repository}#${candidate.reference}",
                what = "${candidate.kind.name.lowercase().replace('_', ' ')} — ${candidate.signal}"
            )
        }
        return candidates
    }

    private fun invitedIssues(query: Query, authorization: String?): List<Candidate> {
        val terms = buildList {
            add("is:issue")
            add("is:open")
            add("no:assignee")
            add("archived:false")
            if (query.owner.isNotBlank()) add("user:${query.owner}")
            query.languages.forEach { add("language:$it") }
            add(INVITING_LABELS.joinToString(",") { "label:\"$it\"" })
        }
        return search(terms, authorization, Kind.INVITED_ISSUE) { item ->
            // The maintainer's own words for why they want help, not a guess.
            LABEL_PATTERN.findAll(item)
                .map { it.groupValues[1] }
                .firstOrNull { candidate -> INVITING_LABELS.any { it.equals(candidate, true) } }
                ?: "labelled for help"
        }
    }

    private fun conflictedPullRequests(query: Query, authorization: String?): List<Candidate> {
        val terms = buildList {
            add("is:pr")
            add("is:open")
            add("status:failure")
            add("archived:false")
            if (query.owner.isNotBlank() && !query.includeOthersConflicts) add("user:${query.owner}")
            query.languages.forEach { add("language:$it") }
        }
        return search(terms, authorization, Kind.CONFLICTED_PULL_REQUEST) { "checks failing" }
    }

    /**
     * Runs one search and reads the fields this needs out of the response.
     *
     * Scanned rather than parsed, for the same reason the CI gate scans its
     * run listing: a JSON library would be a dependency the rest of the engine
     * does not carry, and these are flat scalars in a known response shape.
     * Each item is sliced at its own `html_url`, so a field from a neighbouring
     * result cannot be read as this one's.
     */
    private fun search(
        terms: List<String>,
        authorization: String?,
        kind: Kind,
        signal: (String) -> String
    ): List<Candidate> {
        val query = URLEncoder.encode(terms.joinToString(" "), StandardCharsets.UTF_8)
        val response = http?.let { send ->
            send(Request("$API/search/issues?q=$query&sort=updated&per_page=$PAGE_SIZE", authorization!!))
        } ?: apiClient.searchIssues(
            query = terms.joinToString(" "),
            declaredTerritory = listOf(".")
        ).let { GitHubScavenger.Response(it.status, it.body) }
        if (response.status != 200) {
            // Reported, not thrown. One search failing should not lose the
            // results of the other, and a rate limit is a normal thing to hit.
            Narrate.research.trouble(
                "GitHub search for ${kind.name.lowercase().replace('_', ' ')} returned ${response.status}",
                response.body.take(160)
            )
            return emptyList()
        }
        return response.body.split("\"html_url\":")
            .drop(1)
            .mapNotNull { slice -> candidate(slice, kind, signal) }
    }

    private fun candidate(slice: String, kind: Kind, signal: (String) -> String): Candidate? {
        val url = Regex("^\"([^\"]+)\"").find(slice.trim())?.groupValues?.get(1) ?: return null
        val match = REPOSITORY_PATTERN.find(url) ?: return null
        return Candidate(
            kind = kind,
            repository = match.groupValues[1],
            reference = match.groupValues[3].toIntOrNull() ?: return null,
            title = field(slice, "title") ?: return null,
            url = url,
            signal = signal(slice),
            updatedAt = field(slice, "updated_at").orEmpty()
        )
    }

    private fun field(slice: String, name: String): String? =
        Regex("\"$name\":\"((?:[^\"\\\\]|\\\\.)*)\"").find(slice)
            ?.groupValues?.get(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\n", " ")

    companion object {
        /**
         * The labels maintainers use to say "please help with this".
         *
         * A closed list. Every open issue is not an invitation, and treating it
         * as one is how a helpful tool becomes a nuisance to people who never
         * asked it for anything.
         */
        val INVITING_LABELS = listOf("good first issue", "help wanted")

        const val DEFAULT_LIMIT = 20

        private const val API = "https://api.github.com"
        private const val PAGE_SIZE = 30
        private val LABEL_PATTERN = Regex("\"name\":\"([^\"]+)\"")
        private val REPOSITORY_PATTERN =
            Regex("""github\.com/([^/]+/[^/]+)/(issues|pull)/(\d+)""")
    }
}
