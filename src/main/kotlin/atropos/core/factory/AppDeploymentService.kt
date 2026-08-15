/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import java.util.concurrent.ConcurrentHashMap

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
        val id = "deploy-${System.nanoTime()}"
        val d = Deployment(id, env, domain, gitCommitHash, active = true)
        deployments[id] = d
        return d
    }

    fun getActive(env: DeploymentEnvironment): Deployment? {
        return deployments.values.firstOrNull { it.environment == env && it.active }
    }
}

object DomainService {
    fun configureHttps(domain: String): String {
        return "https://$domain"
    }
}

class RollbackService(private val service: DeploymentService) {
    private val history = mutableListOf<Deployment>()

    fun recordDeployment(d: Deployment) {
        history.add(d)
    }

    fun rollbackTo(deploymentId: String): Boolean {
        val target = history.firstOrNull { it.id == deploymentId } ?: return false
        // Make target active, make other ones in the same environment inactive
        return true
    }
}

data class RepositoryBinding(
    val repoUri: String,
    val targetBranch: String,
    val localDirectoryPath: String
)

class ScheduledTaskScheduler {
    private val tasks = mutableListOf<String>()

    fun scheduleTask(cronExpr: String, job: () -> Unit) {
        tasks.add(cronExpr)
        job()
    }

    fun getScheduledCrons(): List<String> = tasks.toList()
}

class ActivityMonitor {
    private val activities = mutableListOf<String>()

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
        activities.add("plan=$planId provider=$providerId tool=$toolName diff=$diffSize test=$testPassed verifier=$verifierVerdict artifact=$artifactId deploy=$deployId")
    }

    fun getRecentActivities(): List<String> = activities.toList()
}
