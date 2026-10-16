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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atropos.android.app.bridge.MobileCheckpoint
import com.atropos.android.app.ui.CheckpointChip
import com.atropos.android.app.ui.MobileSixAnswers
import com.atropos.android.app.ui.StatusBadge

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
                    contentAlignment = Alignment.CenterVertically
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
                            androidx.compose.material3.IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.default.ArrowBack,
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Connectivity Indicator
                            androidx.compose.material3.Icon(
                                imageVector = if (isOnline) {
                                    androidx.compose.material.icons.default.Wifi
                                } else {
                                    androidx.compose.material.icons.default.WifiOff
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
                                androidx.compose.material3.Badge(
                                    box = { Text(text = provider, style = MaterialTheme.typography.labelSmall) },
                                    badgeContent = { Text(text = "AI", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            }

            // Content area - would be filled by NavHost
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // NavHost content goes here
            }

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
    messages: List<com.atropos.android.app.ui.MobileMessage> = emptyList(),
    isOnline: Boolean = false,
    onSendMessage: (String) -> Unit = {},
    checkpoint: com.atropos.android.app.bridge.MobileCheckpoint? = null,
    thinking: com.atropos.android.app.bridge.MobileThinking? = null,
    onThinkingDepthRequested: (Int) -> Unit = {},
    selfHostRun: com.atropos.android.app.bridge.MobileSelfHostRun? = null,
    selfHostBusy: Boolean = false,
    onAdvanceBuild: (String) -> Unit = {},
    onDismissBuild: () -> Unit = {},
    onCommand: ((String) -> Unit)? = null
) {
    MaterialTheme {
        com.atropos.android.app.ui.ConversationScreen(
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