/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import atropos.core.project.RepositoryBinding
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

enum class DeploymentEnvironment { HOSTING, PREVIEW, LIVE }

data class Deployment(
    val id: String,
    val environment: DeploymentEnvironment,
    val domain: String,
    val gitCommitHash: String,
    val active: Boolean
)

class DeploymentService {
    private val deployments = ConcurrentHashMap<String, Deployment>()

    fun deploy(env: DeploymentEnvironment, domain: String, gitCommitHash: String): Deployment {
        require(
            atropos.core.integration.CloudDeploymentGuard.isRemoteDeploymentSecure(
                deploymentUrl = domain,
                hasUiStrip = env != DeploymentEnvironment.PREVIEW
            )
        ) { "deployment target is not secure for a UI-stripped environment" }
        val id = "deploy-${System.nanoTime()}"
        val d = Deployment(id, env, domain, gitCommitHash, active = true)
        deployments[id] = d
        return d
    }

    fun getActive(env: DeploymentEnvironment): Deployment? {
        return deployments.values.firstOrNull { it.environment == env && it.active }
    }

    internal fun activateOnly(target: Deployment): Boolean {
        if (deployments[target.id] == null) return false
        deployments.replaceAll { _, deployment ->
            if (deployment.environment == target.environment) {
                deployment.copy(active = deployment.id == target.id)
            } else {
                deployment
            }
        }
        return true
    }
}

object DomainService {
    fun configureHttps(domain: String): String {
        return "https://$domain"
    }
}

class RollbackService(private val service: DeploymentService) {
    private val history = java.util.concurrent.CopyOnWriteArrayList<Deployment>()

    fun recordDeployment(d: Deployment) {
        history.removeIf { it.id == d.id }
        history.add(d)
    }

    fun rollbackTo(deploymentId: String): Boolean {
        val target = history.firstOrNull { it.id == deploymentId } ?: return false
        // Make target active, make other ones in the same environment inactive.
        return service.activateOnly(target)
    }
}

class ScheduledTaskScheduler {
    private val tasks = mutableListOf<String>()
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "atropos-scheduled-task").apply { isDaemon = true }
    }

    fun scheduleTask(cronExpr: String, job: () -> Unit) {
        tasks.add(cronExpr)
        job()
    }

    fun scheduleBackgroundTask(
        scheduleName: String,
        delayMillis: Long,
        job: () -> Unit
    ): ScheduledFuture<*> {
        require(delayMillis >= 0) { "scheduled task delay must not be negative" }
        tasks.add(scheduleName)
        return executor.schedule(job, delayMillis, TimeUnit.MILLISECONDS)
    }

    fun getScheduledCrons(): List<String> = tasks.toList()

    fun shutdown() {
        executor.shutdownNow()
    }
}

data class ActivityRecord(
    val planId: String,
    val providerId: String,
    val toolName: String,
    val diffSize: Int,
    val testPassed: Boolean,
    val verifierVerdict: String,
    val artifactId: String,
    val deployId: String
) {
    fun asLogLine(): String =
        "plan=$planId provider=$providerId tool=$toolName diff=$diffSize " +
            "test=$testPassed verifier=$verifierVerdict artifact=$artifactId deploy=$deployId"
}

class ActivityMonitor {
    private val activities = CopyOnWriteArrayList<ActivityRecord>()

    fun record(activity: ActivityRecord) {
        activities.add(activity)
    }

    fun recordActivity(
        planId: String,
        providerId: String,
        toolName: String,
        diffSize: Int,
        testPassed: Boolean,
        verifierVerdict: String,
        artifactId: String,
        deployId: String
    ) {
        record(
            ActivityRecord(
                planId,
                providerId,
                toolName,
                diffSize,
                testPassed,
                verifierVerdict,
                artifactId,
                deployId
            )
        )
    }

    fun getRecentActivities(): List<String> = activities.map(ActivityRecord::asLogLine)

    fun getRecentRecords(): List<ActivityRecord> = activities.toList()
}
