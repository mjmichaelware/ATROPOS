/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.projection

import atropos.bridge.http.JsonWriter
import atropos.core.artifact.export.ArtifactLanding
import atropos.core.artifact.export.ArtifactLandingResolver
import atropos.core.artifact.export.LandingResolution
import java.nio.file.Path

/**
 * Projects the landing zones an export may actually use.
 *
 * `SUP.ART.ROOT-OR-DOWNLOADS` gives the operator the choice of where artifacts
 * land, and this exists so the surface can only offer zones the engine has
 * already resolved. A picker built from a hard-coded list would present
 * Downloads on a platform that has none, and the failure would surface as a
 * write error after the operator committed to the export.
 *
 * A refused zone is emitted rather than dropped: the operator needs to know
 * Downloads exists as a concept and why it is unavailable here, which a silently
 * shorter list cannot tell them.
 */
class ExportProjection {

    fun render(resolver: ArtifactLandingResolver, grantedTerritory: List<Path>): String {
        val zones = listOf(
            "repository" to ArtifactLanding.RepositoryRoot,
            "downloads" to ArtifactLanding.PlatformDownloads
        )

        return JsonWriter.obj(
            "ok" to JsonWriter.bool(true),
            "grantedTerritory" to JsonWriter.strArr(grantedTerritory.map { it.toString() }),
            "zones" to JsonWriter.arr(
                zones.map { (id, landing) ->
                    when (val resolution = resolver.resolve(landing, grantedTerritory)) {
                        is LandingResolution.Resolved -> JsonWriter.obj(
                            "id" to JsonWriter.str(id),
                            "available" to JsonWriter.bool(true),
                            "directory" to JsonWriter.str(resolution.directory.toString()),
                            "zone" to JsonWriter.str(resolution.zone)
                        )
                        is LandingResolution.Refused -> JsonWriter.obj(
                            "id" to JsonWriter.str(id),
                            "available" to JsonWriter.bool(false),
                            "detail" to JsonWriter.str(resolution.reason),
                            "remedy" to JsonWriter.str(resolution.remedy)
                        )
                    }
                }
            )
        )
    }
}
