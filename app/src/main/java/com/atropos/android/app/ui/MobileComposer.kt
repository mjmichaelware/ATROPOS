/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send

/**
 * Where the operator types. Natural language, not a command line.
 *
 * Send stays enabled while the engine is unreachable so a message can still be
 * composed and queued rather than lost; the caller decides what to do with it.
 * Touch targets are 44dp to meet the HIG minimum (HOE-D07).
 */
@Composable
fun MobileComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isOnline: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp),
                placeholder = {
                    Text(if (isOnline) "Message ATROPOS..." else "Engine offline — message will queue")
                },
                maxLines = 4
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                enabled = value.isNotBlank(),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = sendIcon(),
                    contentDescription = "Send"
                )
            }
        }
    }
}

private fun sendIcon(): ImageVector = Icons.AutoMirrored.Filled.Send
