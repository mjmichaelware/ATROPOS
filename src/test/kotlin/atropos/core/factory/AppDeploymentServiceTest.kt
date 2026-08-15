/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AppDeploymentServiceTest {

    @Test
    fun `DeploymentService deploys and retrieves active environment`() {
        val service = DeploymentService()
        val d = service.deploy(DeploymentEnvironment.LIVE, "atropos.app", "git-123")
        assertEquals(DeploymentEnvironment.LIVE, d.environment)
        assertEquals("atropos.app", d.domain)
        assertEquals(d, service.getActive(DeploymentEnvironment.LIVE))
    }

    @Test
    fun `DomainService configures HTTPS scheme`() {
        assertEquals("https://example.com", DomainService.configureHttps("example.com"))
    }

    @Test
    fun `RollbackService tracks rollback history`() {
        val service = DeploymentService()
        val rollback = RollbackService(service)
        val d1 = service.deploy(DeploymentEnvironment.LIVE, "atropos.app", "git-1")
        rollback.recordDeployment(d1)
        assertTrue(rollback.rollbackTo(d1.id))
    }

    @Test
    fun `RepositoryBinding stores binding configuration`() {
        val binding = RepositoryBinding("git://repo", "main", "/path/to/local")
        assertEquals("git://repo", binding.repoUri)
        assertEquals("main", binding.targetBranch)
    }

    @Test
    fun `ScheduledTaskScheduler schedules background tasks`() {
        val scheduler = ScheduledTaskScheduler()
        var run = false
        scheduler.scheduleTask("*/5 * * * *") { run = true }
        assertTrue(run)
        assertEquals(listOf("*/5 * * * *"), scheduler.getScheduledCrons())
    }

    @Test
    fun `ActivityMonitor logs unified pipeline parameters`() {
        val monitor = ActivityMonitor()
        monitor.recordActivity("p1", "prov1", "tool1", 42, true, "VERIFIED", "art1", "d1")
        assertEquals(1, monitor.getRecentActivities().size)
        assertTrue(monitor.getRecentActivities().first().contains("plan=p1"))
    }
}
