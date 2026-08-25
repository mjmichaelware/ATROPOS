/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.core.AtroposConfig
import atropos.cli.ui.FirstRunGuide
import atropos.core.provider.ProviderOnboardingService
import atropos.core.provider.StaticProviderDescriptorRegistry
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * What this install has actually done, for the first-run guide to mark.
 *
 * Every answer is read from the machine rather than from a counter. A guide
 * that ticked steps off as the operator scrolled past them would be a progress
 * bar wearing a checklist's clothes, and the one thing a checklist has to be
 * is true: a step marked done that was never done sends someone looking for a
 * problem in the wrong place.
 */
class FirstRunProbe(
    private val config: AtroposConfig,
    private val workspace: Path = Paths.get("").toAbsolutePath(),
    private val environment: (String) -> String? = System::getenv,
    private val onboarding: ProviderOnboardingService? = null
) {

    fun progress(): FirstRunGuide.Progress = FirstRunGuide.Progress(
        providerConfigured = providerConfigured(),
        documentAttached = documentAttached(),
        runCompleted = runCompleted()
    )

    /**
     * Whether any provider holds a key.
     *
     * Read from the descriptor registry rather than from the four fields the
     * config happens to name, so a provider added to the registry counts
     * without this having to be edited to know about it.
     */
    private fun providerConfigured(): Boolean = onboarding?.healthyProviderIds()?.isNotEmpty()
        ?: StaticProviderDescriptorRegistry().getAll().any { descriptor ->
            descriptor.isLocal || descriptor.requiredEnv.any { key ->
                !environment(key).isNullOrBlank()
            }
        }

    /** Whether anything has been ingested into this workspace. */
    private fun documentAttached(): Boolean =
        exists(".atropos/research") || exists(".atropos/cas")

    /** Whether a run has ever reached evidence here. */
    private fun runCompleted(): Boolean =
        exists(".atropos/runs") || exists(".atropos/evidence")

    private fun exists(relative: String): Boolean = runCatching {
        val path = workspace.resolve(relative)
        Files.isDirectory(path) && Files.list(path).use { it.findAny().isPresent }
    }.getOrDefault(false)
}
