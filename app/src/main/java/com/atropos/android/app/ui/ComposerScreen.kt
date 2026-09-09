/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.dragAndDropModifier
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.icons.Icons
import androidx.compose.material3.icons.filled.ContentCopy
import androidx.compose.material3.icons.filled.Send
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.FocusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.PlatformUtils
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.IntrinsicSize
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.KeyboardOptionsProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * The composer: thumb-zone anchored, 44dp targets, IME aware.
 *
 * HOE-D04 requires the input field to sit in the bottom thumb zone and
 * expose 44dp touch targets. The IME is handled explicitly so the field
 * does not jump or obscure the transcript when the keyboard appears.
 *
 * Copy/share are built-in: long-press the field to copy the draft or
 * share it. The "send" button is a 44dp target.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
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
    val focusRequester = remember { FocusRequester() }
    val view = LocalView.current
    var text by remember { mutableStateOf(value) }
    val textFieldValue = remember { mutableStateOf(TextFieldValue(text)) }
    var showCopyToast by remember { mutableStateOf(false) }

    // Sync with external value
    if (text != value) {
        text = value
        textFieldValue.value = TextFieldValue(text)
    }

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

    // IME controller for keyboard management
    val imeController = LocalSoftwareKeyboardController.current

    // Ensure IME doesn't cause layout jumps - use windowSoftInputMode
    DisposableEffect(keyboardController) {
        if (PlatformUtils.isAndroid) {
            val activity = (context as android.app.Activity)
            val originalMode = activity.window.attributes.softInputMode
            activity.window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            onDispose {
                activity.window.attributes.softInputMode = originalMode
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun ComposerField() {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp) // 44dp target
                    .padding(horizontal = 12.dp)
                    .align(Alignment.CenterVertically),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Build button (optional)
                onBuild?.let { build ->
                    IconButton(
                        onClick = build,
                        contentDescription = "Start build",
                        modifier = Modifier.size(44.dp) // 44dp target
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null)
                    }
                }

                // Text field with 44dp height
                TextField(
                    value = textFieldValue.value,
                    onValueChange = { newValue ->
                        textFieldValue.value = newValue
                        onValueChange(newValue.annotatedString.text)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp) // 44dp target
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .semantics { contentDescription = "Message input" },
                    keyboardOptions = keyboardOptions,
                    keyboardActions = androidx.compose.material3.TextFieldKeyboardActions(
                        onDone = { onSend() },
                        onNext = { keyboardController?.hide() },
                        onPrevious = { keyboardController?.hide() },
                        onGo = { onSend() },
                    ),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    placeholder = { Text("Ask ATROPOS anything…", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                    visualTransformation = VisualTransformation.None,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    onValueChange = { newText ->
                        textFieldValue.value = newText
                        onValueChange(newText.annotatedString.text)
                    },
                )

                // Copy button
                IconButton(
                    onClick = { copyToClipboard(text) },
                    contentDescription = "Copy draft",
                    modifier = Modifier.size(44.dp) // 44dp target
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                }

                // Send button - 44dp target
                Button(
                    onClick = {
                        if (text.isNotBlank()) onSend()
                    },
                    modifier = Modifier
                        .size(44.dp) // 44dp target
                        .padding(start = 4.dp),
                    enabled = text.isNotBlank() && isOnline,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send message")
                }
            }
        }
    }

    Column(modifier = modifier
        .fillMaxWidth()
        .padding(bottom = 8.dp)
        .semantics { contentDescription = "Composer area" }
    ) {
        // Long press to copy/share
        val longPressContext = androidx.compose.ui.input.pointer.nestedScrollConnection
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitPointerEventScope {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val timeout = awaitTimeoutOrNull(500_000_000) { awaitPointerEvent() }
                            if (timeout == null) {
                                // Long press detected
                                copyToClipboard(text)
                            }
                        }
                    }
                }
                .semantics {
                    customActions = listOf(
                        androidx.compose.ui.semantics.CustomAccessibilityAction(
                            "Copy draft",
                            { copyToClipboard(text); true }
                        ),
                        androidx.compose.ui.semantics.CustomAccessibilityAction(
                            "Share draft",
                            {
                                val intent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                                    type = "text/plain"
                                }
                                val chooser = android.content.Intent.createChooser(intent, "Share draft")
                                context.startActivity(chooser)
                                true
                            }
                        )
                    )
                }
            ) {
                ComposerField()
            }
        }

        // Copy toast
        if (showCopyToast) {
            androidx.compose.material3.Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter),
                action = { showCopyToast = false },
                visual = {
                    Text("Copied to clipboard", style = MaterialTheme.typography.bodySmall)
                },
                dismissAction = { showCopyToast = false },
                duration = androidx.compose.material3.SnackbarDuration.Short
            )
        }
    }
}