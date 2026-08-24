/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.github

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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubApiClientTest {
    @Test
    fun typed_endpoints_use_one_gated_transport_and_never_put_token_in_body() {
        val secret = "ghs-super-secret-token"
        val requests = mutableListOf<GitHubApiWireRequest>()
        var declaredTerritory: List<String>? = null
        withNetworkSink {
            val client = GitHubApiClient(
                secretSource = MapSecretSource(mapOf("GITHUB_TOKEN" to secret)),
                gate = { proposal ->
                    declaredTerritory = proposal.targetPaths
                    allowed(proposal)
                },
                transport = GitHubApiTransport { request ->
                    requests += request
                    GitHubApiWireResponse(200, "{\"ok\":true}")
                }
            )

            assertEquals(200, client.listIssues("owner", "repo").status)
            assertEquals(200, client.getPullRequestFiles("owner", "repo", 7).status)
            assertEquals(
                200,
                client.updateCheckRun(
                    "owner",
                    "repo",
                    9,
                    "{\"status\":\"completed\"}",
                    GitHubWriteAuthorization("operator", "confirm-1")
                ).status
            )
            assertEquals(200, client.getBranchProtection("owner", "repo", "release/v1").status)
        }

        assertEquals(listOf("GET", "GET", "PATCH", "GET"), requests.map { it.method })
        assertTrue(requests.all { it.url.startsWith("https://api.github.com/repos/owner/repo/") })
        assertTrue(requests.last().url.endsWith("/branches/release/v1/protection"))
        assertTrue(requests.all { it.token == secret })
        assertFalse(requests.any { it.body.orEmpty().contains(secret) })
        assertEquals(listOf("."), declaredTerritory)
    }

    @Test
    fun every_request_crosses_the_registered_github_integration_owner() {
        assertEquals("github", atropos.core.integration.IntegrationRegistry.requireRegistered("github").id)
    }

    @Test
    fun network_policy_and_secret_egress_fail_before_transport() {
        var called = false
        val client = GitHubApiClient(
            secretSource = MapSecretSource(mapOf("GITHUB_TOKEN" to "token")),
            gate = { proposal -> refused(proposal, AgencyDisposition.APPROVAL_REQUIRED, "operator approval") },
            transport = GitHubApiTransport {
                called = true
                GitHubApiWireResponse(200, "{}")
        )

        withNetworkSink {
            assertFailsWith<IllegalStateException> {
                client.execute(GitHubApiRequest("GET", "/repos/owner/repo/issues"))
            }
        }
        assertFalse(called)
    }

    @Test
    fun paths_methods_and_missing_tokens_are_fail_closed() {
        withNetworkSink {
            val client = GitHubApiClient(
                secretSource = MapSecretSource(emptyMap()),
                gate = ::allowed,
                transport = GitHubApiTransport { GitHubApiWireResponse(200, "{}") }
            )
            assertFailsWith<IllegalArgumentException> {
                client.execute(GitHubApiRequest("DELETE", "/repos/owner/repo/issues/1"))
            }
            assertFailsWith<IllegalArgumentException> {
                client.execute(GitHubApiRequest("GET", "/repos/owner/../other/issues"))
            }
            assertFailsWith<IllegalStateException> {
                client.execute(GitHubApiRequest("GET", "/repos/owner/repo/issues"))
            }
        }
    }

    @Test
    fun writes_require_operator_confirmation_before_secret_or_transport() {
        var called = false
        val client = GitHubApiClient(
            secretSource = MapSecretSource(mapOf("GITHUB_TOKEN" to "token")),
            gate = ::allowed,
            transport = GitHubApiTransport {
                called = true
                GitHubApiWireResponse(200, "{}")
            }
        )

        withNetworkSink {
            assertFailsWith<IllegalArgumentException> {
                client.execute(GitHubApiRequest("POST", "/repos/owner/repo/issues", "{}"))
            }
        }
        assertFalse(called)
    }

    private fun withNetworkSink(block: () -> Unit) {
        SecretSinkMatrix.setPermitted(SecretSinkKind.EGRESS_URL, true)
        try {
            block()
        } finally {
            SecretSinkMatrix.resetDefaults()
        }
    }

    private companion object {
        fun allowed(proposal: ActionProposal): AgencyDecision =
            refused(proposal, AgencyDisposition.ALLOWED, "test allowed")

        fun refused(
            proposal: ActionProposal,
            disposition: AgencyDisposition,
            reason: String
        ): AgencyDecision = AgencyDecision(
            proposal = proposal,
            policyDecision = ExecutionPolicyDecision(
                id = "test",
                decision = if (disposition == AgencyDisposition.ALLOWED) PolicyDecisionType.ALLOW else PolicyDecisionType.APPROVAL_REQUIRED,
                actionClass = PolicyActionClass.NETWORK,
                destructive = false,
                reason = reason
            ),
            disposition = disposition,
            reason = reason
        )
    }
}
