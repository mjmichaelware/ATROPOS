/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.projection

import atropos.bridge.http.JsonWriter
import atropos.core.thinking.ThinkingDepth
import atropos.core.thinking.ThinkingRecord
import atropos.core.security.RedactionFilter

/**
 * Projects stored reasoning at the depth a surface asked for.
 *
 * The filter runs here, on the full stored record, rather than being applied
 * when the reasoning was produced. `HOE-B03` requires exactly that — "thinking
 * depth is UI filter only; never change provider task payload" — because a
 * shallower request would produce different reasoning for the collapsed surface
 * instead of a shallower view of the same reasoning, and the two surfaces would
 * then disagree about what the system actually considered.
 *
 * `hasMore` travels with the payload so the surface knows whether an expand
 * control should exist. A drawer that opens onto nothing is worse than no
 * drawer: it teaches the operator the gesture is meaningless.
 */
class ThinkingProjection(
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {

    fun render(record: ThinkingRecord?, depth: ThinkingDepth): String {
        if (record == null || record.isEmpty()) {
            // Absence stated as absence. An empty line list rendered as a
            // successful read would show a node that thought about nothing.
            return JsonWriter.obj(
                "ok" to JsonWriter.bool(true),
                "present" to JsonWriter.bool(false),
                "detail" to JsonWriter.str("No reasoning was recorded for this node."),
                "remedy" to JsonWriter.str("Reasoning is captured as a node runs; there is nothing to expand.")
            )
        }

        return JsonWriter.obj(
            "ok" to JsonWriter.bool(true),
            "present" to JsonWriter.bool(true),
            "nodeId" to JsonWriter.str(redactionFilter.redact(record.nodeId)),
            "depth" to JsonWriter.num(depth.level),
            "depthLabel" to JsonWriter.str(depth.label),
            // Whether expanding would reveal anything, not how much is hidden.
            "hasMore" to JsonWriter.bool(record.hasMoreThan(depth)),
            "deepestAvailable" to JsonWriter.num(record.deepestAvailable()?.level ?: depth.level),
            "levels" to JsonWriter.arr(
                ThinkingDepth.entries.map {
                    JsonWriter.obj(
                        "level" to JsonWriter.num(it.level),
                        "label" to JsonWriter.str(it.label)
                    )
                }
            ),
            "lines" to JsonWriter.arr(
                record.at(depth).map { line ->
                    JsonWriter.obj(
                        "id" to JsonWriter.str(redactionFilter.redact(line.id)),
                        "minDepth" to JsonWriter.num(line.minDepth.level),
                        "text" to JsonWriter.str(redactionFilter.redact(line.text))
                    )
                }
            )
        )
    }
}
