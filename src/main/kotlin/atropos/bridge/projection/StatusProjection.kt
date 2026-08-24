/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.projection

import atropos.bridge.http.JsonWriter

/**
 * Composite projection of the engine's current state.
 */
class StatusProjection {

    fun render(
        answersJson: String,
        checkpointJson: String,
        queueDepth: Int,
        activeProvider: String,
        engineIdentity: String,
        quotaJson: String = JsonWriter.obj(
            "readable" to JsonWriter.bool(false),
            "reason" to JsonWriter.str("quota-ledger-not-wired")
        )
    ): String = JsonWriter.obj(
        "ok" to JsonWriter.bool(true),
        "engine" to JsonWriter.str(engineIdentity),
        "activeProvider" to JsonWriter.str(activeProvider),
        "queueDepth" to JsonWriter.num(queueDepth.toLong()),
        "answers" to answersJson,
        "checkpoint" to checkpointJson,
        "quota" to quotaJson
    )
}
