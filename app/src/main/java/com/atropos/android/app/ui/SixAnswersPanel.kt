/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atropos.android.app.bridge.MobileSixAnswers

@Composable
fun SixAnswersPanel(answers: MobileSixAnswers?, modifier: Modifier = Modifier) {
    if (answers == null) return
    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Text("Objective: ${answers.objective.value}", style = MaterialTheme.typography.bodyMedium)
        Text("Doing: ${answers.doing.value}", style = MaterialTheme.typography.bodyMedium)
        Text("Why: ${answers.why.value}", style = MaterialTheme.typography.bodyMedium)
        Text("Progress: ${answers.progress.value}", style = MaterialTheme.typography.bodyMedium)
        Text("Next: ${answers.next.value}", style = MaterialTheme.typography.bodyMedium)
        Text("Evidence: ${answers.evidence.value}", style = MaterialTheme.typography.bodyMedium)
    }
}
