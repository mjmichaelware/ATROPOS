/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.scavenge

import atropos.core.github.GitHubApiClient
import atropos.core.github.GitHubApiWireRequest
import atropos.core.github.GitHubApiWireResponse
import atropos.core.policy.ActionProposal
import atropos.core.policy.AgencyDecision
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.ExecutionPolicyDecision
import atropos.core.policy.PolicyActionClass
import atropos.core.policy.PolicyDecisionType
import atropos.core.security.MapSecretSource
import atropos.core.security.SecretSinkKind
import atropos.core.security.SecretSinkMatrix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GitHubScavengerTest {

    @Test
    fun production_path_delegates_to_the_gated_github_client() {
        val requests = mutableListOf<GitHubApiWireRequest>()
        val client = GitHubApiClient(
            secretSource = MapSecretSource(mapOf("GITHUB_TOKEN" to "test-token")),
            gate = ::allowNetwork,
            transport = { request ->
                requests += request
                GitHubApiWireResponse(
                    200,
                    if (request.url.contains("is%3Aissue")) issueBody() else "{\"items\":[]}"
                )
            }
        )

        SecretSinkMatrix.setPermitted(SecretSinkKind.EGRESS_URL, true)
        try {
            val found = GitHubScavenger(apiClient = client)
                .scavenge(GitHubScavenger.Query(owner = "acme"))

            assertEquals(1, found.size)
            assertEquals(2, requests.size)
            assertTrue(requests.all { it.url.startsWith("https://api.github.com/search/issues") })
            assertTrue(requests.all { it.token == "test-token" })
        } finally {
            SecretSinkMatrix.resetDefaults()
        }
    }

    private fun issueBody() = """
        {"total_count":1,"items":[
          {"html_url":"https://github.com/acme/widget/issues/42",
           "title":"Parser drops the last row",
           "updated_at":"2026-08-01T10:00:00Z",
           "labels":[{"name":"bug"},{"name":"good first issue"}]}
        ]}
    """.trimIndent()

    private fun pullBody() = """
        {"total_count":1,"items":[
          {"html_url":"https://github.com/acme/widget/pull/7",
           "title":"Add the retry path",
           "updated_at":"2026-08-05T10:00:00Z",
           "labels":[]}
        ]}
    """.trimIndent()

    /** Answers each search by what the query asked for, and records the URLs. */
    private class FakeGitHub(private val issue: String, private val pull: String) {
        val urls = mutableListOf<String>()

        fun handle(request: GitHubScavenger.Request): GitHubScavenger.Response {
            urls += request.url
            return GitHubScavenger.Response(200, if (request.url.contains("is%3Aissue")) issue else pull)
        }
    }

    private fun scavenger(github: FakeGitHub, token: String? = "t0ken") =
        GitHubScavenger(token = token, http = github::handle)

    private companion object {
        fun allowNetwork(proposal: ActionProposal): AgencyDecision = AgencyDecision(
            proposal = proposal,
            policyDecision = ExecutionPolicyDecision(
                id = "test-network",
                decision = PolicyDecisionType.ALLOW,
                actionClass = PolicyActionClass.NETWORK,
                destructive = false,
                reason = "test allowed"
            ),
            disposition = AgencyDisposition.ALLOWED,
            reason = "test allowed"
        )
    }

    @Test
    fun it_finds_issues_a_maintainer_labelled_for_help() {
        val github = FakeGitHub(issue = issueBody(), pull = """{"items":[]}""")

        val found = scavenger(github).scavenge(GitHubScavenger.Query(owner = "acme"))

        val issue = found.single { it.kind == GitHubScavenger.Kind.INVITED_ISSUE }
        assertEquals("acme/widget", issue.repository)
        assertEquals(42, issue.reference)
        assertEquals("Parser drops the last row", issue.title)
        // The maintainer's own words for why they want help, not this class's
        // opinion of the work.
        assertEquals("good first issue", issue.signal)
    }

    @Test
    fun it_searches_only_the_labels_that_are_an_invitation() {
        // Every open issue is not a request for help, and treating it as one is
        // how a helpful tool becomes a nuisance to people who never asked it
        // for anything.
        val github = FakeGitHub(issue = issueBody(), pull = """{"items":[]}""")

        scavenger(github).scavenge(GitHubScavenger.Query(owner = "acme"))

        val issueQuery = github.urls.single { it.contains("is%3Aissue") }
        GitHubScavenger.INVITING_LABELS.forEach { label ->
            assertTrue(
                issueQuery.contains(label.replace(" ", "+")),
                "the search does not ask for '$label': $issueQuery"
            )
        }
        assertTrue(issueQuery.contains("no%3Aassignee"), "it offers work already taken: $issueQuery")
        assertTrue(issueQuery.contains("archived%3Afalse"), "it offers archived repositories")
    }

    @Test
    fun other_peoples_stuck_branches_are_left_alone_unless_asked_for() {
        // Someone else's half-finished branch is not a request for a stranger
        // to rebase it.
        val github = FakeGitHub(issue = """{"items":[]}""", pull = pullBody())
        scavenger(github).scavenge(GitHubScavenger.Query(owner = "acme"))
        assertTrue(github.urls.single { it.contains("is%3Apr") }.contains("user%3Aacme"))

        val wide = FakeGitHub(issue = """{"items":[]}""", pull = pullBody())
        scavenger(wide).scavenge(GitHubScavenger.Query(owner = "acme", includeOthersConflicts = true))
        assertTrue(!wide.urls.single { it.contains("is%3Apr") }.contains("user%3Aacme"))
    }

    @Test
    fun the_most_recently_touched_comes_first() {
        // A three-year-old "help wanted" is not an invitation any more,
        // whatever the label still says.
        val github = FakeGitHub(issue = issueBody(), pull = pullBody())

        val found = scavenger(github).scavenge(GitHubScavenger.Query(owner = "acme"))

        assertEquals(listOf(7, 42), found.map(GitHubScavenger.Candidate::reference))
    }

    @Test
    fun one_failing_search_does_not_lose_the_other() {
        // A rate limit is a normal thing to hit, and losing the issues because
        // the pull-request search 403'd would be the wrong trade.
        val body = issueBody()
        val handler = { request: GitHubScavenger.Request ->
            if (request.url.contains("is%3Apr")) GitHubScavenger.Response(403, "rate limited")
            else GitHubScavenger.Response(200, body)
        }

        val found = GitHubScavenger(token = "t0ken", http = handler)
            .scavenge(GitHubScavenger.Query(owner = "acme"))

        assertEquals(1, found.size)
        assertEquals(GitHubScavenger.Kind.INVITED_ISSUE, found.single().kind)
    }

    @Test
    fun a_missing_token_is_named_rather_than_returning_nothing() {
        val failure = assertFailsWith<IllegalStateException> {
            scavenger(FakeGitHub(issue = "", pull = ""), token = null)
                .scavenge(GitHubScavenger.Query(owner = "acme"))
        }

        assertTrue(failure.message.orEmpty().contains("ATROPOS_GITHUB_TOKEN"))
    }

    @Test
    fun a_scan_with_no_owner_is_refused() {
        // Scanning all of GitHub finds noise, and the useful version of this is
        // pointed at a person or an organisation.
        assertFailsWith<IllegalArgumentException> {
            scavenger(FakeGitHub(issue = "", pull = "")).scavenge(GitHubScavenger.Query(owner = " "))
        }
    }

    @Test
    fun the_limit_is_honoured() {
        val many = """{"items":[""" + (1..40).joinToString(",") {
            """{"html_url":"https://github.com/acme/w/issues/$it","title":"t$it",""" +
                """"updated_at":"2026-08-0${it % 9 + 1}T00:00:00Z","labels":[{"name":"help wanted"}]}"""
        } + "]}"
        val github = FakeGitHub(issue = many, pull = """{"items":[]}""")

        val found = scavenger(github).scavenge(GitHubScavenger.Query(owner = "acme", limit = 5))

        assertEquals(5, found.size)
    }
}
