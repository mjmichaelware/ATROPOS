package atropos.core.sentry

import atropos.core.policy.ActionProposal
import atropos.core.policy.AgencyDecision
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.ExecutionPolicyDecision
import atropos.core.policy.PolicyDecisionType
import atropos.core.security.MapSecretSource
import atropos.core.security.SecretSinkKind
import atropos.core.security.SecretSinkMatrix
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SentryApiClientTest {
    @AfterTest
    fun resetSinks() = SecretSinkMatrix.resetDefaults()

    @Test
    fun `fetch uses injected transport and parses top frame without logging token`() {
        SecretSinkMatrix.setPermitted(SecretSinkKind.EGRESS_URL, true)
        var capturedToken = ""
        val client = SentryApiClient(
            secretSource = MapSecretSource(mapOf("SENTRY_AUTH_TOKEN" to "sentry-secret-token")),
            gate = ::allow,
            transport = SentryApiTransport { request ->
                capturedToken = request.token
                SentryApiWireResponse(
                    200,
                    """{"id":"42","title":"Null pointer","culprit":"src/main.kt","exception":{"values":[{"stacktrace":{"frames":[{"filename":"/workspace/src/main.kt","lineno":27}]}}]}}"""
                )
            },
            baseUrl = "https://sentry.example.test"
        )

        val issue = client.getIssue("42", listOf("src"))

        assertEquals("sentry-secret-token", capturedToken)
        assertEquals("42", issue.id)
        assertEquals("Null pointer", issue.title)
        assertEquals("/workspace/src/main.kt", issue.frames.single().filename)
        assertEquals(27, issue.frames.single().lineNumber)
    }

    @Test
    fun `parser keeps actionable issue fields and tolerates missing line`() {
        val issue = SentryIssueParser.parse(
            "issue",
            """{"message":"Broken","culprit":"worker","stacktrace":{"frames":[{"filename":"src/App.kt"}]}}"""
        )

        assertEquals("issue", issue.id)
        assertEquals("Broken", issue.title)
        assertEquals("worker", issue.culprit)
        assertEquals(null, issue.frames.single().lineNumber)
        assertTrue(issue.frames.single().filename.endsWith("App.kt"))
    }

    @Test
    fun `parser refuses truncated or non-object wire responses`() {
        assertFailsWith<IllegalArgumentException> {
            SentryIssueParser.parse("issue", "{\"title\":\"Broken\"")
        }
        assertFailsWith<IllegalArgumentException> {
            SentryIssueParser.parse("issue", "upstream unavailable")
        }
    }

    @Test
    fun `list unresolved issues uses the bounded project endpoint`() {
        SecretSinkMatrix.setPermitted(SecretSinkKind.EGRESS_URL, true)
        val requests = mutableListOf<SentryApiRequest>()
        val client = SentryApiClient(
            secretSource = MapSecretSource(mapOf("SENTRY_AUTH_TOKEN" to "token")),
            gate = ::allow,
            transport = { request ->
                requests += request
                SentryApiWireResponse(200, "[]")
            },
            baseUrl = "https://sentry.example.test"
        )

        val response = client.listUnresolvedIssues("org", "project", listOf("src"))

        assertEquals(200, response.status)
        assertEquals("GET", requests.single().method)
        assertTrue(requests.single().url.contains("/api/0/projects/org/project/issues/"))
        assertTrue(requests.single().url.contains("is%3Aunresolved"))
    }

    private fun allow(proposal: ActionProposal): AgencyDecision = AgencyDecision(
        proposal = proposal,
        policyDecision = ExecutionPolicyDecision(
            id = "test", decision = PolicyDecisionType.ALLOW,
            actionClass = proposal.actionClass, destructive = false, reason = "test allow"
        ),
        disposition = AgencyDisposition.ALLOWED,
        reason = "test allow"
    )
}
