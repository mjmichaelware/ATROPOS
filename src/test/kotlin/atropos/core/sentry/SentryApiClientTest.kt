package atropos.core.sentry

import atropos.core.policy.ActionProposal
import atropos.core.policy.AgencyDecision
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.ExecutionPolicyDecision
import atropos.core.policy.PolicyDecisionType
import atropos.core.security.MapSecretSource
import atropos.core.security.SecretSinkKind
import atropos.core.security.SecretSinkMatrix
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SentryApiClientTest {
    @AfterEach
    fun resetSinks() = SecretSinkMatrix.resetDefaults()

    @Test
    fun `fetch uses injected transport and parses top frame without logging token`() {
        SecretSinkMatrix.setPermitted(SecretSinkKind.EGRESS_URL, true)
        var capturedToken = ""
        val client = SentryApiClient(
            secretSource = MapSecretSource(mapOf("SENTRY_AUTH_TOKEN" to "sentry-secret-token")),
            gate = ::allow,
            transport = { request ->
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
