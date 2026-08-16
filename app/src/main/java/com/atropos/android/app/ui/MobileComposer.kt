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
import androidx.compose.material3.TextButton
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
fun ComposerScreen(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isOnline: Boolean,
    /**
     * Asks the engine to build what was typed, rather than answer it.
     *
     * A separate action from Send, not a mode toggle. Sending is a question and
     * building mutates the source tree; a toggle makes those one keystroke
     * apart and remembers its position, so the destructive one eventually
     * happens because the operator forgot which state they left it in.
     */
    onBuild: (() -> Unit)? = null
) {
    val density = OneHandDensity()
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
                    .heightIn(min = density.touchTargetDp.dp),
                placeholder = {
                    Text(if (isOnline) "Message ATROPOS..." else "Engine offline — message will queue")
                },
                maxLines = 4
            )
            if (onBuild != null) {
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(
                    onClick = onBuild,
                    enabled = isOnline && canSendComposerInput(value)
                ) {
                    Text("Build")
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                enabled = canSendComposerInput(value),
                modifier = Modifier.size(density.touchTargetDp.dp)
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

fun canSendComposerInput(value: String): Boolean = value.isNotBlank()
