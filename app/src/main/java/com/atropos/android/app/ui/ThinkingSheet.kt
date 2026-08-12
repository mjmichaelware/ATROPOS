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
import com.atropos.android.app.bridge.MobileThinking

/** Collapsed-by-default thinking surface with explicit L1-L3 disclosure. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThinkingSheet(
    thinking: MobileThinking?,
    onDepthRequested: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (thinking == null) return
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }, modifier = modifier) {
        Text("Thinking · L${thinking.depth}")
    }
    if (open) {
        ModalBottomSheet(
            onDismissRequest = { open = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text("Thinking L${thinking.depth}", style = MaterialTheme.typography.titleMedium)
                thinking.lines.forEach { line -> Text(line, modifier = Modifier.padding(top = 8.dp)) }
                if (thinking.hasMore && thinking.depth < 3) {
                    TextButton(onClick = { onDepthRequested(thinking.depth + 1) }) {
                        Text("Expand to L${thinking.depth + 1}")
                    }
                }
            }
        }
    }
}
