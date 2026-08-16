/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.atropos.android.app.bridge.MobileSelfHostRun

/**
 * The build the operator asked for, and what it is doing.
 *
 * The phone could watch the engine and talk to it, and had no way to ask it to
 * build anything — the one thing the engine exists to do. This is the surface
 * for that: it shows the goal, the DAG's progress, and one button to take the
 * next step.
 *
 * Advancing is manual, one step per press. The engine's route runs a single
 * advance per call because a phone on a dropped connection cannot interrupt a
 * long run; that constraint is honest here rather than hidden behind an
 * auto-runner, so the operator is always one press away from stopping.
 *
 * Progress is only drawn when the engine reported a graph. A run whose DAG has
 * not been synthesised shows the message and no bar, because a bar at zero
 * looks like a run that planned and achieved nothing, which is a different and
 * much worse situation than one that has not planned yet.
 */
@Composable
fun SelfHostPanel(
    run: MobileSelfHostRun?,
    busy: Boolean,
    onAdvance: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (run == null) return
    Card(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Build · ${run.goalId}",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = selfHostHeadline(run), style = MaterialTheme.typography.bodyMedium)

            run.dag?.let { dag ->
                val fraction = dag.fraction()
                if (fraction != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { fraction.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription =
                                    "${dag.complete} of ${dag.total} steps complete"
                            }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = dagSummary(dag), style = MaterialTheme.typography.labelSmall)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text(if (run.finished) "Close" else "Hide") }
                if (!run.finished) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onAdvance(run.goalId) }, enabled = !busy) {
                        Text(if (busy) "Working…" else "Next step")
                    }
                }
            }
        }
    }
}

/**
 * One line for the run's state.
 *
 * A finished run says whether it succeeded, never only that it ended. A client
 * that showed "finished" for a terminal failure would report a failed build as
 * a completed one, which is the single most misleading thing this panel could
 * say.
 */
internal fun selfHostHeadline(run: MobileSelfHostRun): String = when {
    run.succeeded -> "Completed and verified."
    run.finished -> "Stopped: ${run.terminalCondition?.replace('_', ' ')}. ${run.message}".trim()
    run.currentNodeId != null -> "Running ${run.currentNodeId}. ${run.message}".trim()
    else -> run.message.ifBlank { "Planning." }
}

/**
 * The node counts, with failures and blocks named rather than folded into a
 * total. A run that is 6/8 complete with 2 failed is finished and broken; a run
 * that is 6/8 with 2 pending is still going, and the bar looks identical.
 */
internal fun dagSummary(dag: com.atropos.android.app.bridge.MobileDagProgress): String = buildString {
    append("${dag.complete}/${dag.total} steps")
    if (dag.running > 0) append(" · ${dag.running} running")
    if (dag.pending > 0) append(" · ${dag.pending} pending")
    if (dag.failed > 0) append(" · ${dag.failed} failed")
    if (dag.blocked > 0) append(" · ${dag.blocked} blocked")
}
