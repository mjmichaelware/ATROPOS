/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atropos.android.app.bridge.MobileCheckpoint

/** Checkpoint projection; resume remains an explicit operator action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckpointChip(
    checkpoint: MobileCheckpoint?,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (checkpoint == null) return
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }, modifier = modifier) {
        Text(
            text = "Checkpoint · ${checkpoint.phase.ifBlank { checkpoint.goalId }}",
            style = MaterialTheme.typography.labelLarge
        )
    }
    if (open) {
        ModalBottomSheet(
            onDismissRequest = { open = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp)
            ) {
                Text("Checkpoint ${checkpoint.goalId}", style = MaterialTheme.typography.titleMedium)
                checkpoint.actions.forEach { action ->
                    TextButton(onClick = { open = false; onAction(action) }) {
                        Text(action.replace('-', ' ').replace('_', ' ').replaceFirstChar(Char::uppercase))
                    }
                }
            }
        }
    }
}
