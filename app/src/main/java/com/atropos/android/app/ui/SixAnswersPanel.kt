/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atropos.android.app.bridge.MobileAnswer
import com.atropos.android.app.bridge.MobileSixAnswers

/**
 * §0.1: the six questions the operator should never have to go looking for.
 *
 * The engine has served these at `/v1/answers` since the bridge existed and no
 * client ever asked for them, which is why the phone showed an empty column
 * next to an engine that could describe its own state in six lines.
 *
 * Each answer arrives with a `health` and a `signal`, and both are used. The
 * previous version rendered the value alone, discarding the two fields that
 * make the answer readable — which mattered most for exactly the answer worth
 * noticing: "unreadable · .atropos/agent/queue" rendered identically to a
 * healthy one, so a broken queue looked like a working one.
 *
 * Source Doc 3 Section E: status colour "pairs with a redundant non-color
 * signal". [MobileAnswer.signal] is that channel, and it is also what the
 * screen reader announces, so the health of an answer survives both a
 * monochrome display and a person who cannot see the display at all.
 */
@Composable
fun SixAnswersPanel(answers: MobileSixAnswers?, modifier: Modifier = Modifier) {
    if (answers == null) return
    Card(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            AnswerRow("Objective", answers.objective)
            AnswerRow("Doing", answers.doing)
            AnswerRow("Why", answers.why)
            AnswerRow("Progress", answers.progress)
            AnswerRow("Next", answers.next)
            AnswerRow("Evidence", answers.evidence)
        }
    }
}

@Composable
private fun AnswerRow(label: String, answer: MobileAnswer) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            // One description per row rather than per fragment: a reader that
            // announced "Progress", "check", "3 of 8 complete" as three
            // separate nodes makes the operator reassemble the sentence.
            .semantics { contentDescription = "$label: ${answer.value}, ${answer.health}" }
    ) {
        Text(
            text = answer.signal.ifBlank { healthGlyph(answer.health) },
            style = MaterialTheme.typography.bodyMedium,
            color = healthColor(answer.health)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(84.dp)
        )
        Text(
            text = answer.value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Fallback shape for a health the engine did not send a signal for.
 *
 * An unrecognised health renders as `?`, not as a tick. The one thing this
 * panel must never do is show a state it does not understand as a healthy one.
 */
internal fun healthGlyph(health: String): String = when (health.lowercase()) {
    "verified" -> "✔"
    "pending" -> "◐"
    "error" -> "✖"
    "unknown" -> "○"
    else -> "?"
}

/**
 * Colour is the redundant channel here, not the primary one — [healthGlyph]
 * and the label carry the signal on their own.
 */
@Composable
private fun healthColor(health: String): Color = when (health.lowercase()) {
    "verified" -> MaterialTheme.colorScheme.primary
    "pending" -> MaterialTheme.colorScheme.tertiary
    "error" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.outline
}
