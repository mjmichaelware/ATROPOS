/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.artifact.export

import java.nio.file.Path

/**
 * Where an exported artifact is allowed to land.
 *
 * `SUP.ART.ROOT-OR-DOWNLOADS`: "User controls where durable artifacts land; no
 * surprise writes into system directories." Competitors hard-code a landing
 * zone, which on a phone means writing into whichever directory the packager
 * happened to pick.
 *
 * The resolver refuses rather than falling back. A landing zone that silently
 * becomes the repository root when the configured one is unavailable is a
 * surprise write by another name — the operator asked for somewhere specific,
 * and quietly choosing elsewhere is the behaviour this atom exists to remove.
 */
sealed class ArtifactLanding {
    /** Inside the workspace, alongside the work that produced the artifact. */
    object RepositoryRoot : ArtifactLanding()

    /** The platform's downloads location, resolved by the caller's platform. */
    object PlatformDownloads : ArtifactLanding()

    /** An explicit operator choice. */
    data class Explicit(val path: Path) : ArtifactLanding()
}

sealed class LandingResolution {
    data class Resolved(val directory: Path, val zone: String) : LandingResolution()
    data class Refused(val reason: String, val remedy: String) : LandingResolution()
}

/**
 * Resolves a landing zone against the territory the export is permitted.
 *
 * Territory is checked here rather than at the writer because
 * `SUP.ART.HANDOFF-EXPORT` requires an export to leave the system "only through
 * an explicit, territory-bounded, redacted channel" — a writer that trusted its
 * caller would be that channel's hole.
 */
class ArtifactLandingResolver(
    private val repoRoot: Path,
    /** Null when the platform exposes no downloads location. */
    private val downloadsDir: Path? = null
) {
    fun resolve(landing: ArtifactLanding, grantedTerritory: List<Path>): LandingResolution =
        when (landing) {
            is ArtifactLanding.RepositoryRoot ->
                LandingResolution.Resolved(repoRoot.resolve(DEFAULT_SUBDIR).normalize(), "repository")

            is ArtifactLanding.PlatformDownloads -> downloadsDir?.let {
                LandingResolution.Resolved(it.normalize(), "downloads")
            } ?: LandingResolution.Refused(
                "This platform exposes no downloads directory.",
                "Choose an explicit landing path, or export to the repository root."
            )

            is ArtifactLanding.Explicit -> {
                val target = landing.path.toAbsolutePath().normalize()
                val permitted = grantedTerritory.any { granted ->
                    target.startsWith(granted.toAbsolutePath().normalize())
                }
                if (!permitted) {
                    LandingResolution.Refused(
                        "$target lies outside the territory granted for this export.",
                        "Export inside a granted path, or widen the grant deliberately."
                    )
                } else {
                    LandingResolution.Resolved(target, "explicit")
                }
            }
        }

    private companion object {
        const val DEFAULT_SUBDIR = ".atropos/exports"
    }
}
