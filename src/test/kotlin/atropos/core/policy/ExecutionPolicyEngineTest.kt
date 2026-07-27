package atropos.core.policy

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExecutionPolicyEngineTest {
    @Test
    fun deniesDangerousShellAndWritesAudit() {
        val repoRoot = Files.createTempDirectory("atropos-policy-test-")
        val auditStore = ExecutionPolicyAuditStore(repoRoot)
        val engine = ExecutionPolicyEngine(repoRoot, auditStore)

        val denied = engine.evaluate(
            ExecutionPolicyRequest(
                actionClass = PolicyActionClass.SHELL,
                command = listOf("rm", "-rf", "/")
            )
        )
        val allowed = engine.evaluate(
            ExecutionPolicyRequest(
                actionClass = PolicyActionClass.GIT,
                command = listOf("git", "status", "--short")
            )
        )

        assertEquals(PolicyDecisionType.DENY, denied.decision)
        assertEquals(PolicyDecisionType.ALLOW, allowed.decision)
        assertTrue(auditStore.latest().any { it.contains("decision=DENY") && it.contains("action=SHELL") })
        assertTrue(auditStore.latest().any { it.contains("decision=ALLOW") && it.contains("action=GIT") })
    }

    @Test
    fun deniesPaidProvidersAndForbiddenPaths() {
        val repoRoot = Files.createTempDirectory("atropos-policy-paid-")
        val engine = ExecutionPolicyEngine(repoRoot)

        val paid = engine.evaluate(
            ExecutionPolicyRequest(
                actionClass = PolicyActionClass.PROVIDER_CALL,
                providerId = "openai",
                paidProvider = true
            )
        )
        val forbidden = engine.evaluate(
            ExecutionPolicyRequest(
                actionClass = PolicyActionClass.PATCH_APPLY,
                command = listOf("git", "apply", "bad.diff"),
                targetPaths = listOf(".atropos/secrets/token.json")
            )
        )

        assertEquals(PolicyDecisionType.DENY, paid.decision)
        assertEquals(PolicyDecisionType.DENY, forbidden.decision)
    }

    @Test
    fun everyActionClassProducesAuditedBoundedDecisionWithRedaction() {
        val repoRoot = Files.createTempDirectory("atropos-policy-matrix-")
        val auditStore = ExecutionPolicyAuditStore(repoRoot)
        val engine = ExecutionPolicyEngine(repoRoot, auditStore)
        val secret = "fake-secret-value-12345"

        val requests = mapOf(
            PolicyActionClass.SHELL to ExecutionPolicyRequest(
                actionClass = PolicyActionClass.SHELL,
                command = listOf("pwd"),
                metadata = mapOf("token" to secret)
            ),
            PolicyActionClass.GIT to ExecutionPolicyRequest(
                actionClass = PolicyActionClass.GIT,
                command = listOf("git", "status", "--short")
            ),
            PolicyActionClass.FILE_MUTATION to ExecutionPolicyRequest(
                actionClass = PolicyActionClass.FILE_MUTATION,
                targetPaths = listOf("docs/ATROPOS_CANONICAL_PHASES_1_11_CLOSURE.md")
            ),
            PolicyActionClass.PATCH_APPLY to ExecutionPolicyRequest(
                actionClass = PolicyActionClass.PATCH_APPLY,
                command = listOf("git", "apply", "--check", ".atropos/agent/patches/example.diff"),
                targetPaths = listOf("docs/ATROPOS_CANONICAL_PHASES_1_11_CLOSURE.md")
            ),
            PolicyActionClass.BUILD_TEST to ExecutionPolicyRequest(
                actionClass = PolicyActionClass.BUILD_TEST,
                command = listOf("./gradlew", "test")
            ),
            PolicyActionClass.PROVIDER_CALL to ExecutionPolicyRequest(
                actionClass = PolicyActionClass.PROVIDER_CALL,
                providerId = "groq"
            ),
            PolicyActionClass.NETWORK to ExecutionPolicyRequest(
                actionClass = PolicyActionClass.NETWORK,
                networkTarget = "https://example.invalid"
            ),
            PolicyActionClass.DAEMON to ExecutionPolicyRequest(actionClass = PolicyActionClass.DAEMON),
            PolicyActionClass.QUEUE to ExecutionPolicyRequest(actionClass = PolicyActionClass.QUEUE),
            PolicyActionClass.SMOKE to ExecutionPolicyRequest(
                actionClass = PolicyActionClass.SMOKE,
                command = listOf("true")
            )
        )

        val decisions = requests.mapValues { (_, request) -> engine.evaluate(request) }

        assertEquals(PolicyDecisionType.APPROVAL_REQUIRED, decisions.getValue(PolicyActionClass.NETWORK).decision)
        assertTrue(decisions.values.all { it.id.startsWith("policy-") })
        assertTrue(decisions.values.all { it.reason.isNotBlank() })
        assertTrue(decisions.values.all { it.actionClass in PolicyActionClass.values() })
        val audit = auditStore.latest(100).joinToString("\n")
        PolicyActionClass.values().forEach { action ->
            assertTrue(audit.contains("action=${action.name}"))
        }
        assertTrue(audit.contains("<redacted"))
        assertTrue(!audit.contains(secret))
    }
}
