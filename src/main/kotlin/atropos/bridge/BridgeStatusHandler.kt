/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.http.HttpResponse
import atropos.bridge.projection.StatusProjection
import atropos.bridge.projection.CheckpointProjection
import atropos.bridge.projection.SixAnswersProjection
import atropos.bridge.queue.ConversationWorkRunner
import atropos.cli.ui.HomeStateProvider
import atropos.core.checkpoint.CheckpointSummary
import java.time.Instant

internal class BridgeStatusHandler(
    private val homeState: HomeStateProvider,
    private val activeProvider: () -> String,
    private val sixAnswers: SixAnswersProjection,
    private val checkpoint: () -> CheckpointSummary?,
    private val checkpointView: CheckpointProjection,
    private val work: ConversationWorkRunner?,
    private val statusView: StatusProjection = StatusProjection(),
    private val quotaSummary: () -> String = { statusViewQuotaUnavailable() },
    private val clock: () -> Instant = { Instant.now() }
) {
    fun getStatus(): HttpResponse {
        val provider = activeProvider()
        val answersJson = sixAnswers.render(homeState.capture(provider))
        val checkpointSummary = checkpoint()
        val checkpointJson = checkpointView.render(checkpointSummary, clock())
        val queueDepth = work?.list(100)?.size ?: 0
        val json = statusView.render(
            answersJson = answersJson,
            checkpointJson = checkpointJson,
            queueDepth = queueDepth,
            activeProvider = provider,
            engineIdentity = "atropos",
            quotaJson = quotaSummary()
        )
        return HttpResponse.json(json)
    }

    private companion object {
        fun statusViewQuotaUnavailable(): String =
            atropos.bridge.http.JsonWriter.obj(
                "readable" to atropos.bridge.http.JsonWriter.bool(false),
                "reason" to atropos.bridge.http.JsonWriter.str("quota-ledger-not-wired")
            )
    }
}
