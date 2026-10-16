/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * The composer: thumb-zone anchored, 44dp targets, IME aware.
 *
 * HOE-D04 requires the input field to sit in the bottom thumb zone and
 * expose 44dp touch targets. The IME is handled explicitly so the field
 * does not jump or obscure the transcript when the keyboard appears.
 *
 * Copy/share are built-in: copy the draft or share it. The "send" button is a 44dp target.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposerScreen(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isOnline: Boolean,
    onBuild: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var showCopyToast by remember { mutableStateOf(false) }

    // Handle IME actions
    val keyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Text,
        imeAction = ImeAction.Send,
        capitalization = KeyboardCapitalization.Sentences,
    )

    // Copy to clipboard handler
    val copyToClipboard = { str: String ->
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("composer-draft", str)
        clipboardManager.setPrimaryClip(clip)
        showCopyToast = true
    }

    // Ensure IME doesn't cause layout jumps - use windowSoftInputMode
    DisposableEffect(keyboardController) {
        if (context is android.app.Activity) {
            val activity = context as android.app.Activity
            val originalMode = activity.window.attributes.softInputMode
            activity.window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            onDispose {
                activity.window.attributes.softInputMode = originalMode
            }
        } else {
            onDispose { }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .semantics { contentDescription = "Composer area" }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Build button (optional)
                onBuild?.let { build ->
                    IconButton(
                        onClick = build,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Default.Build, contentDescription = "Start build")
                    }
                }

                // Text field
                TextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = "Message input" },
                    keyboardOptions = keyboardOptions,
                    keyboardActions = KeyboardActions(
                        onDone = { onSend() },
                        onGo = { onSend() },
                    ),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    placeholder = {
                        Text(
                            "Ask ATROPOS anything…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                )

                // Copy button
                IconButton(
                    onClick = { copyToClipboard(value) },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy draft")
                }

                // Send button - 44dp target
                Button(
                    onClick = {
                        if (value.isNotBlank()) onSend()
                    },
                    modifier = Modifier.size(44.dp),
                    contentPadding = PaddingValues(0.dp),
                    enabled = value.isNotBlank() && isOnline,
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send message")
                }
            }
        }

        // Copy toast
        if (showCopyToast) {
            Snackbar(
                modifier = Modifier.padding(16.dp),
                action = { TextButton(onClick = { showCopyToast = false }) { Text("OK") } }
            ) {
                Text("Copied to clipboard", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
