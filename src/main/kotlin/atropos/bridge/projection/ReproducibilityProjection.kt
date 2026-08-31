/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.projection

import atropos.bridge.http.JsonWriter
import atropos.core.phase20.ReproducibilityGate
import atropos.core.phase20.ReproducibilityInput
import atropos.core.phase20.ReproducibilityResult
import atropos.core.AtroposRepoRootLocator
import atropos.core.security.RedactionFilter
import java.nio.file.Files
import java.nio.file.Path

/**
 * Projects the reproducibility gate onto the wire.
 *
 * `ADD-W-023` requires a reproducibility predicate panel: metric declared
 * before code (P20 discipline). Form/display bound to engine predicate objects.
 */
class ReproducibilityProjection(
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {

    /**
     * Evaluates the reproducibility of the current workspace.
     */
    fun evaluate(expectedFiles: Map<String, String>): String {
        val repoRoot = AtroposRepoRootLocator.resolve()
        val input = ReproducibilityInput(repoRoot, expectedFiles)
        val gate = ReproducibilityGate()
        val result = gate.evaluate(input)
        return renderResult(result)
    }

    /**
     * Creates a snapshot of the current workspace.
     */
    fun snapshot(relativeFiles: List<String>): String {
        val repoRoot = AtroposRepoRootLocator.resolve()
        val gate = ReproducibilityGate()
        val snapshot = gate.snapshot(repoRoot, relativeFiles)
        return JsonWriter.obj(
            "ok" to JsonWriter.bool(true),
            "files" to JsonWriter.arr(snapshot.entries.map { (k, v) ->
                JsonWriter.obj(
                    "path" to JsonWriter.str(k),
                    "sha256" to JsonWriter.str(v)
                )
            }),
            "count" to JsonWriter.num(snapshot.size)
        )
    }

    /**
     * Renders the reproducibility result.
     */
    fun renderResult(result: ReproducibilityResult): String = JsonWriter.obj(
        "ok" to JsonWriter.bool(true),
        "passed" to JsonWriter.bool(result.passed),
        "reason" to JsonWriter.str(result.reason),
        "expectedFileCount" to JsonWriter.num(result.expectedFileCount),
        "comparedFileCount" to JsonWriter.num(result.comparedFileCount),
        "snapshotSha256" to JsonWriter.str(result.snapshotSha256)
    )
}