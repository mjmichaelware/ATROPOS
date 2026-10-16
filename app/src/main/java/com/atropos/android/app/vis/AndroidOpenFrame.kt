/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.vis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.atropos.android.app.bridge.MobileCheckpoint
import com.atropos.android.app.bridge.MobileSelfHostRun
import com.atropos.android.app.bridge.MobileThinking
import com.atropos.android.app.ui.CheckpointChip
import com.atropos.android.app.ui.ConversationScreen
import com.atropos.android.app.ui.MobileMessage

/**
 * F-VIS-008: Android Open Frame
 *
 * Top-left: project/session title
 * Right: connectivity + provider badge
 *
 * Depends on: F-AND-001 (Android NavHost)
 */
@Composable
fun AndroidOpenFrame(
    modifier: Modifier = Modifier,
    projectName: String = "ATROPOS",
    sessionName: String = "default",
    isOnline: Boolean = false,
    activeProvider: String? = null,
    checkpoint: MobileCheckpoint? = null,
    onNavigateUp: (() -> Unit)? = null
) {
    MaterialTheme {
        Surface(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top
            ) {
                // Top Bar - Project/Session Title + Connectivity + Provider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.CenterStart
                ) {
                    // Left: Navigate Up + Title
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Navigate Up / Back
                        onNavigateUp?.let { onBack ->
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Navigate back"
                                )
                            }
                        }

                        // Project / Session Title
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = projectName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = sessionName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Right: Connectivity + Provider
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Connectivity Indicator
                            Icon(
                                imageVector = if (isOnline) {
                                    Icons.Default.Wifi
                                } else {
                                    Icons.Default.WifiOff
                                },
                                contentDescription = if (isOnline) "Online" else "Offline",
                                tint = if (isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = if (isOnline) "Online" else "Offline",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )

                            // Provider Badge
                            activeProvider?.let { provider ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "$provider (AI)",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }

                // Content area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp)
                )

                // Bottom: Checkpoint chip if available
                checkpoint?.let { cp ->
                    CheckpointChip(
                        checkpoint = cp,
                        onAction = { _ -> }
                    )
                }
            }
        }
    }
}

/**
 * F-VIS-009: Android Hero Stream
 *
 * Full-width stream is the hero; tools in a bottom sheet.
 *
 * Depends on: F-AND-004 (Primary stream)
 */
@Composable
fun AndroidHeroStream(
    modifier: Modifier = Modifier,
    messages: List<MobileMessage> = emptyList(),
    isOnline: Boolean = false,
    onSendMessage: (String) -> Unit = {},
    checkpoint: MobileCheckpoint? = null,
    thinking: MobileThinking? = null,
    onThinkingDepthRequested: (Int) -> Unit = {},
    selfHostRun: MobileSelfHostRun? = null,
    selfHostBusy: Boolean = false,
    onAdvanceBuild: (String) -> Unit = {},
    onDismissBuild: () -> Unit = {},
    onCommand: ((String) -> Unit)? = null
) {
    MaterialTheme {
        ConversationScreen(
            messages = messages,
            isOnline = isOnline,
            onSendMessage = onSendMessage,
            checkpoint = checkpoint,
            thinking = thinking,
            onThinkingDepthRequested = onThinkingDepthRequested,
            selfHostRun = selfHostRun,
            selfHostBusy = selfHostBusy,
            onAdvanceBuild = onAdvanceBuild,
            onDismissBuild = onDismissBuild,
            onCommand = onCommand
        )
    }
}

/**
 * F-VIS-010: Android Footer Composer
 *
 * Thumb-zone composer above nav/IME:
 * - 48dp min touch target
 * - Send always visible when focused
 * - Expandable for multi-line
 *
 * Depends on: F-AND-005 (Composer thumb zone)
 */
@Composable
fun AndroidFooterComposer(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    onSend: (String) -> Unit,
    isOnline: Boolean = false,
    onExpand: (() -> Unit)? = null
) {
    MaterialTheme {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Input field
                TextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp, max = 150.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (value.trim().isNotEmpty()) {
                                onSend(value)
                            }
                        }
                    ),
                    singleLine = false,
                    maxLines = 5,
                    placeholder = { Text(text = if (isOnline) "Message..." else "Engine offline") }
                )

                // Send button - always visible when focused
                Button(
                    onClick = { if (value.trim().isNotEmpty()) onSend(value) },
                    enabled = value.trim().isNotEmpty(),
                    modifier = Modifier
                        .height(48.dp)
                        .width(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}