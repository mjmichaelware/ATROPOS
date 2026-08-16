/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import atropos.core.project.RepositoryBinding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
        val binding = RepositoryBinding(repoRoot = "/path/to/local", branch = "main")
        assertEquals("/path/to/local", binding.repoRoot)
        assertEquals("main", binding.branch)
    }

    @Test
    fun `ScheduledTaskScheduler schedules background tasks`() {
        val scheduler = ScheduledTaskScheduler()
        var run = false
        scheduler.scheduleTask("*/5 * * * *") { run = true }
        assertTrue(run)
        assertEquals(listOf("*/5 * * * *"), scheduler.getScheduledCrons())
        scheduler.shutdown()
    }

    @Test
    fun `ScheduledTaskScheduler executes delayed work on its bounded scheduler`() {
        val scheduler = ScheduledTaskScheduler()
        val completed = CountDownLatch(1)
        scheduler.scheduleBackgroundTask("background-once", 1) { completed.countDown() }
        assertTrue(completed.await(1, TimeUnit.SECONDS))
        assertEquals(listOf("background-once"), scheduler.getScheduledCrons())
        scheduler.shutdown()
    }

    @Test
    fun `ActivityMonitor logs unified pipeline parameters`() {
        val monitor = ActivityMonitor()
        monitor.recordActivity("p1", "prov1", "tool1", 42, true, "VERIFIED", "art1", "d1")
        assertEquals(1, monitor.getRecentActivities().size)
        assertTrue(monitor.getRecentActivities().first().contains("plan=p1"))
        assertEquals("prov1", monitor.getRecentRecords().single().providerId)
    }
}
