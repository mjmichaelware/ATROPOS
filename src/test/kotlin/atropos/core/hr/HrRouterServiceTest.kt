package atropos.core.hr

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class HrRouterServiceTest {
    @Test
    fun lowRiskRequestApproved() {
        val svc = service()
        val resp = svc.request("src-agent", "terr-a", "dest-agent", "terr-b",
            InformationKind.SOURCE_CODE, "file line 42")
        assertTrue(resp.approved)
        assertEquals(CrossBoundaryRisk.LOW, resp.risk)
    }

    @Test
    fun secretKeywordTriggersHighRisk() {
        val svc = service()
        val resp = svc.request("agent-1", "terr-x", "agent-2", "terr-y",
            InformationKind.MEMORY_QUERY, "what is the API token for groq?")
        assertTrue(resp.approved)
        assertEquals(CrossBoundaryRisk.HIGH, resp.risk)
        assertEquals(HrRouteAction.NARROWED, resp.action)
        assertTrue(resp.redactedContent?.contains("token") != true)
    }

    @Test
    fun credentialPathTriggersCriticalRisk() {
        val svc = service()
        val resp = svc.request("agent-a", "terr-1", "agent-b", "terr-2",
            InformationKind.CONFIGURATION, "need .env values",
            paths = listOf(".env.production"))
        assertFalse(resp.approved)
        assertEquals(CrossBoundaryRisk.CRITICAL, resp.risk)
        assertEquals(HrRouteAction.DENIED, resp.action)
    }

    @Test
    fun mediumRiskConfigurationEscalatesToHumanOwner() {
        val svc = service()
        val resp = svc.request(
            "agent-a",
            "terr-1",
            "agent-b",
            "terr-2",
            InformationKind.CONFIGURATION,
            "need routing config only"
        )

        assertFalse(resp.approved)
        assertEquals(CrossBoundaryRisk.MEDIUM, resp.risk)
        assertEquals(HrRouteAction.ESCALATED, resp.action)
        assertTrue(resp.reason.contains("Human Owner"))
    }

    @Test
    fun auditLogTracksAllRequests() {
        val svc = service()
        svc.request("a1", "t1", "a2", "t2", InformationKind.SOURCE_CODE, "hello")
        svc.request("a3", "t3", "a4", "t4", InformationKind.CREDENTIAL_REFERENCE, "file content",
            paths = listOf(".env.production"))
        assertEquals(2, svc.auditLog().size)
        assertEquals(1, svc.auditLog().count { it.approved })
        assertTrue(svc.auditLog().all { it.action in HrRouteAction.entries })
    }

    @Test
    fun auditLogSurvivesServiceRestart() {
        val root = Files.createTempDirectory("atropos-hr-router-durable-")
        val first = service(root)
        first.request("a1", "t1", "a2", "t2", InformationKind.SOURCE_CODE, "hello", paths = listOf("src/main/kotlin/atropos/core/agent/Foo.kt"))

        val second = service(root)

        assertEquals(1, second.auditLog().size)
        val entry = second.auditLog().single()
        assertEquals(HrRouteAction.APPROVED, entry.action)
        assertEquals("t1", entry.sourceTerritoryId)
        assertEquals("t2", entry.targetTerritoryId)
        assertEquals(listOf("src/main/kotlin/atropos/core/agent/Foo.kt"), entry.requestedPaths)
    }

    private fun service(root: java.nio.file.Path = Files.createTempDirectory("atropos-hr-router-")): HrRouterService =
        HrRouterService(auditStore = HrRouterAuditStore(root))
}
