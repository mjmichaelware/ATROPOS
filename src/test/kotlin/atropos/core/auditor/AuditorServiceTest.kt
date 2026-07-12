package atropos.core.auditor

import atropos.core.territory.TerritoryAssignment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuditorServiceTest {
    @Test
    fun territoryAuditDetectsExpiredAndBlank() {
        val auditor = AuditorService()
        val valid = TerritoryAssignment(id = "t1", ownerId = "o1", ownerRole = "WORKER", allowedPrefix = "src/")
        val expired = TerritoryAssignment(id = "t2", ownerId = "o2", ownerRole = "WORKER", allowedPrefix = "old/",
            expiresAt = java.time.Instant.now().minus(java.time.Duration.ofHours(1)))
        val blank = TerritoryAssignment(id = "t3", ownerId = "o3", ownerRole = "WORKER", allowedPrefix = "")

        val findings = auditor.auditTerritories(listOf(valid, expired, blank))
        val failures = findings.filter { it.severity == AuditSeverity.FAILURE }
        val warnings = findings.filter { it.severity == AuditSeverity.WARNING }
        assertEquals(1, failures.size)
        assertTrue(warnings.any { it.check == "territory-expiry" })
    }

    @Test
    fun reportSummarizesFindings() {
        val auditor = AuditorService()
        auditor.auditTerritories(listOf(
            TerritoryAssignment(id = "t1", ownerId = "o1", ownerRole = "WORKER", allowedPrefix = "src/")
        ))
        val report = auditor.report()
        assertTrue(report.passed >= 1)
        assertEquals(0, report.failures)
    }
}
