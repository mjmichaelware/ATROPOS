package atropos.core.autonomous

import atropos.core.agent.AgentVerificationRunResult
import atropos.core.agent.WorkerCodeProposal
import atropos.core.hierarchy.HierarchyRegistry
import atropos.core.provider.ProviderOnboardingService
import atropos.core.provider.ProviderPolicyGate
import atropos.core.provider.StaticProviderDescriptorRegistry
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProviderWorkerDirectorTest {
    @Test
    fun director_dispatches_disjoint_free_workers_and_merges_verified_proposals() {
        val root = Files.createTempDirectory("provider-worker-director")
        val patchA = Files.createFile(root.resolve("a.patch"))
        val patchB = Files.createFile(root.resolve("b.patch"))
        val onboarding = ProviderOnboardingService(
            root = root,
            environment = mapOf("GROQ_API_KEY" to "fixture", "GEMINI_API_KEY" to "fixture")
        )
        onboarding.refresh()
        val hierarchy = HierarchyRegistry()
        val director = ProviderWorkerDirector(
            hierarchy = hierarchy,
            onboarding = onboarding,
            descriptors = StaticProviderDescriptorRegistry(),
            proposalRunner = { task ->
                val path = if (task.territory.single() == "src/a") patchA else patchB
                WorkerCodeProposal(
                    workerId = task.workerId,
                    provider = task.providerId,
                    patchId = "patch-${task.workerId}",
                    patchPath = path,
                    territory = task.territory,
                    accepted = true,
                    proposalSha256 = "hash-${task.workerId}",
                    verification = AgentVerificationRunResult(
                        patchId = "patch-${task.workerId}",
                        verificationId = "verification-${task.workerId}",
                        patchFile = path,
                        passed = true
                    ),
                    reason = "fixture proposal"
                )
            }
        )

        val report = director.run(
            listOf(
                ProviderWorkerTask("worker-a", "groq", "draft A", listOf("src/a")),
                ProviderWorkerTask("worker-b", "gemini", "draft B", listOf("src/b"))
            )
        )

        assertTrue(report.merged, report.render())
        assertEquals(2, report.results.size)
        assertEquals(4, hierarchy.snapshot().dispatches.size)
    }

    @Test
    fun paid_provider_is_refused_before_hierarchy_dispatch() {
        val root = Files.createTempDirectory("provider-worker-paid")
        val onboarding = ProviderOnboardingService(
            root = root,
            environment = mapOf("OPENAI_API_KEY" to "fixture")
        )
        onboarding.refresh()
        val hierarchy = HierarchyRegistry()
        val director = ProviderWorkerDirector(hierarchy = hierarchy, onboarding = onboarding)

        val report = director.run(listOf(ProviderWorkerTask("worker", "openai", "draft", listOf("src/a"))))

        assertFalse(report.merged)
        assertTrue(report.refusal.orEmpty().contains("paid provider requires approval"))
        assertTrue(hierarchy.dispatchHistory().isEmpty())
    }

    @Test
    fun local_only_policy_refuses_remote_free_worker_before_hierarchy_dispatch() {
        val root = Files.createTempDirectory("provider-worker-local-only")
        val onboarding = ProviderOnboardingService(
            root = root,
            environment = mapOf("GROQ_API_KEY" to "fixture")
        )
        onboarding.refresh()
        val hierarchy = HierarchyRegistry()
        val descriptors = StaticProviderDescriptorRegistry()
        val director = ProviderWorkerDirector(
            hierarchy = hierarchy,
            onboarding = onboarding,
            descriptors = descriptors,
            policyGate = ProviderPolicyGate(
                registry = descriptors,
                healthy = onboarding::healthyProviderIds,
                localOnly = true
            )
        )

        val report = director.run(listOf(ProviderWorkerTask("worker", "groq", "draft", listOf("src/a"))))

        assertFalse(report.merged)
        assertTrue(report.refusal.orEmpty().contains("canonical policy"))
        assertTrue(hierarchy.dispatchHistory().isEmpty())
    }
}
