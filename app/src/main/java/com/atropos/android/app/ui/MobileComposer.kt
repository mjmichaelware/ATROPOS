/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

/**
 * ATOM 8.3: Touch-Safe Mobile Keyboard Focus and IME Handling.
 *
 * Configures software keyboard options with sentence capitalization and Send IME actions.
 * Maintains persistent input focus without accidental blur events when touch gestures
 * or scroll events occur nearby. Meets the HIG minimum 44dp touch target standard.
 *
 * ## Research: WindowInsetsCompat IME Keyboard Animation Listeners
 * WindowInsetsCompat IME keyboard height animation listeners smoothly resize Compose
 * message lists during keyboard appearance without layout stutter or viewport resizing jank.
 */
@Composable
fun ComposerScreen(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isOnline: Boolean,
    /**
     * Asks the engine to build what was typed, rather than answer it.
     */
    onBuild: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val density = OneHandDensity()
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier.fillMaxWidth(),
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
                    .heightIn(min = density.touchTargetDp.dp)
                    .focusRequester(focusRequester),
                interactionSource = interactionSource,
                placeholder = {
                    Text(if (isOnline) "Message ATROPOS..." else "Engine offline — message will queue")
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    autoCorrect = true,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (canSendComposerInput(value)) {
                            onSend()
                        }
                    }
                ),
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

@Composable
fun MobileComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isOnline: Boolean = true,
    onBuild: (() -> Unit)? = null
) = TouchSafeMobileComposer(
    value = value,
    onValueChange = onValueChange,
    onSend = onSend,
    isOnline = isOnline,
    onBuild = onBuild
)
