/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

/**
 * ATOM 8.3: Touch-Safe Mobile Composer.
 *
 * Touch-safe Compose UI with automatic sentence capitalization, predictive text,
 * and direct IME Send dispatch to goal queues without accidental empty submissions.
 *
 * ## Research: WindowInsetsCompat IME Keyboard Animation Listeners
 * Research WindowInsetsCompat IME keyboard height animation listeners to calculate
 * sub-frame layout offsets and prevent Compose viewport jitter when soft keyboards deploy.
 */
object TouchSafeMobileComposerPolicy {
    fun canSendComposerInput(value: String): Boolean = value.isNotBlank()

    fun defaultKeyboardOptions(): KeyboardOptions = KeyboardOptions(
        capitalization = KeyboardCapitalization.Sentences,
        autoCorrect = true,
        imeAction = ImeAction.Send
    )

    fun defaultKeyboardActions(
        value: String,
        onSend: () -> Unit
    ): KeyboardActions = KeyboardActions(
        onSend = {
            if (canSendComposerInput(value)) {
                onSend()
            }
        }
    )
}

@Composable
fun TouchSafeMobileComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isOnline: Boolean = true,
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
                keyboardOptions = TouchSafeMobileComposerPolicy.defaultKeyboardOptions(),
                keyboardActions = TouchSafeMobileComposerPolicy.defaultKeyboardActions(value, onSend),
                maxLines = 4
            )
            if (onBuild != null) {
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(
                    onClick = onBuild,
                    enabled = isOnline && TouchSafeMobileComposerPolicy.canSendComposerInput(value)
                ) {
                    Text("Build")
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                enabled = TouchSafeMobileComposerPolicy.canSendComposerInput(value),
                modifier = Modifier.size(density.touchTargetDp.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send"
                )
            }
        }
    }
}
