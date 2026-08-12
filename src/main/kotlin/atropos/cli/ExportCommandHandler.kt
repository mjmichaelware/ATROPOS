/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.AtroposRepoRootLocator
import atropos.core.artifact.export.ArtifactLanding
import atropos.core.artifact.export.ArtifactLandingResolver
import atropos.core.artifact.export.ExportResult
import atropos.core.artifact.export.HandoffExporter
import atropos.core.artifact.export.HandoffType
import atropos.core.artifact.export.PlatformDownloadsLocator
import atropos.core.auth.AuthBootResult
import atropos.core.auth.AuthBootstrap
import atropos.core.storage.StorageSupervisor
import java.nio.file.Path

/**
 * `/export <type> [--to <path>|--downloads]`.
 *
 * Source Doc 5: artifacts and handoffs go "to repo root or anywhere else
 * specified by /../../path or to internal storage downloads using standard TUI
 * CLI terminal commands". `SUP.ART.ROOT-OR-DOWNLOADS` adds that the default
 * must be safe on Termux, which is why it is the repository and not the
 * device's shared storage: writing into shared storage by default would put
 * files where a backup service picks them up, without the operator asking.
 *
 * `--to` is territory-checked in [HandoffExporter], not here. A handler that
 * did its own path check would be a second implementation of the boundary, and
 * the bridge's export projection would then be checking something subtly
 * different from what the CLI checks.
 */
class ExportCommandHandler(
    private val uiEngine: AnsiTerminalEngine,
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val bootstrap: AuthBootstrap = AuthBootstrap(),
    /** Null means the handler supplies what it can reach itself. */
    private val content: ((HandoffType) -> String)? = null
) {
    fun execute(tokens: List<String>): RouterOutcome {
        val typeToken = tokens.getOrNull(1)
        if (typeToken.isNullOrBlank() || typeToken == "list") {
            uiEngine.renderNotice(
                "usage: /export <type> [--to <path>|--downloads]\n\n" + HandoffType.catalog()
            )
            return RouterOutcome.CONTINUE
        }

        val type = HandoffType.fromCanonical(typeToken)
        if (type == null) {
            uiEngine.renderError(
                "Unknown export type '$typeToken'. Available:\n" + HandoffType.catalog()
            )
            return RouterOutcome.CONTINUE
        }

        val landing = landingFrom(tokens)
        val exporter = HandoffExporter(
            resolver = ArtifactLandingResolver(repoRoot, PlatformDownloadsLocator.locate()),
            storage = StorageSupervisor()
        )

        val body = content ?: ::defaultContent
        when (val result = exporter.export(type, landing, grantedTerritory(), body)) {
            is ExportResult.Written -> uiEngine.renderNotice("Exported: ${result.render()}")
            is ExportResult.Refused -> uiEngine.renderError("${result.reason}\n${result.remedy}")
        }
        return RouterOutcome.CONTINUE
    }

    private fun landingFrom(tokens: List<String>): ArtifactLanding {
        if (tokens.any { it.equals("--downloads", ignoreCase = true) }) {
            return ArtifactLanding.PlatformDownloads
        }
        val at = tokens.indexOfFirst { it.equals("--to", ignoreCase = true) }
        val explicit = tokens.getOrNull(at + 1)?.takeIf { at >= 0 && !it.startsWith("--") }
        return if (explicit == null) ArtifactLanding.RepositoryRoot
        else ArtifactLanding.Explicit(Path.of(explicit))
    }

    /**
     * What this export may write to.
     *
     * The repository, always — an operator exporting from their own tree is
     * inside their own territory. An explicit `--to` outside it is refused by
     * the resolver, which is the behaviour Source Doc 5 asks for when it says
     * a path may be specified: specified, then checked, not specified and
     * trusted.
     */
    private fun grantedTerritory(): List<Path> = listOf(repoRoot)

    private fun defaultContent(type: HandoffType): String = when (type) {
        HandoffType.SWARM -> swarmContent()
        else -> "No ${type.canonical} content was supplied by this session."
    }

    private fun swarmContent(): String = when (val boot = bootstrap.boot()) {
        is AuthBootResult.Refused ->
            "The topology could not be read: ${boot.cause.reason}"

        is AuthBootResult.Booted -> {
            val swarm = boot.swarm
            if (swarm == null) {
                "This repository declares no Swarm.md, so there is no topology to report."
            } else {
                buildString {
                    appendLine("maxDepth: ${swarm.maxDepth}")
                    appendLine("escalation: ${swarm.escalationPath.joinToString(" -> ").ifBlank { "none" }}")
                    appendLine()
                    appendLine("| node | role | territory |")
                    appendLine("| --- | --- | --- |")
                    swarm.nodes.forEach { node ->
                        appendLine("| ${node.name} | ${node.role} | ${node.territoryGrants.joinToString(", ")} |")
                    }
                }
            }
        }
    }
}
