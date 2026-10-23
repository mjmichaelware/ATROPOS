/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.console

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atropos.android.app.bridge.MobileApproval
import com.atropos.android.app.bridge.MobileCheckpoint
import com.atropos.android.app.bridge.MobileSixAnswers
import com.atropos.android.app.bridge.MobileThinking
import com.atropos.android.app.theme.AtroposTypography
import com.atropos.android.app.theme.LocalCyberHudColors
import com.atropos.android.app.theme.cyberGlass
import com.atropos.android.app.ui.MobileMessage

/**
 * Claude Code Workstation & Interactive Autonomous Console for Android.
 *
 * Implements:
 * - High-density streaming conversational transcript
 * - Collapsible <thinking> internal reasoning drawer
 * - Six continuous answers continuous HUD radar
 * - Interactive Human-in-the-Loop approval cards
 * - Quick-tap '/' command taxonomy launcher
 * - Emergency preemption and interrupt coordinator
 */
@Composable
fun ClaudeCodeWorkstationScreen(
    messages: List<MobileMessage>,
    isOnline: Boolean,
    onSendMessage: (String) -> Unit,
    onOpenCommandPalette: () -> Unit,
    onInterrupt: () -> Unit,
    answers: MobileSixAnswers?,
    thinking: MobileThinking?,
    approvals: List<MobileApproval>,
    onApprove: (String, Boolean) -> Unit,
    activeProvider: String = "claude-3-7-sonnet",
    isBusy: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = LocalCyberHudColors.current
    var promptInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.voidAbyss)
    ) {
        // Six Answers Radar HUD Strip
        SixAnswersRadarStrip(answers = answers, activeProvider = activeProvider, isOnline = isOnline)

        // Pending Human Approvals (gated cards)
        if (approvals.isNotEmpty()) {
            ApprovalCardsSection(approvals = approvals, onApprove = onApprove)
        }

        // Conversational Transcript Stream
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 16.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    WorkstationWelcomeCard(
                        onSelectSamplePrompt = { promptInput = it },
                        onOpenPalette = onOpenCommandPalette
                    )
                }
            }

            items(messages) { msg ->
                WorkstationMessageBubble(msg = msg)
            }

            // Real-time Thinking Drawer (if engine is currently reasoning)
            if (thinking != null && thinking.text.isNotBlank()) {
                item {
                    ThinkingDrawerCard(thinking = thinking)
                }
            }

            if (isBusy) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = colors.accentCyan,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Atropos Autonomous Engine running trajectory...",
                            style = AtroposTypography.monoGutter,
                            color = colors.accentCyan
                        )
                    }
                }
            }
        }

        // Quick Slash (/) Command Pill Suggestions
        QuickCommandPillsBar(
            onOpenPalette = onOpenCommandPalette,
            onQuickCommand = { cmd ->
                promptInput = "$cmd "
            }
        )

        // Bottom Composer Bar
        WorkstationComposerBar(
            value = promptInput,
            onValueChange = {
                promptInput = it
                if (it.startsWith("/")) {
                    // Open command palette if user types '/' as first character
                    if (it == "/") {
                        onOpenCommandPalette()
                    }
                }
            },
            onSend = {
                val trimmed = promptInput.trim()
                if (trimmed.isNotBlank()) {
                    onSendMessage(trimmed)
                    promptInput = ""
                }
            },
            onInterrupt = onInterrupt,
            isBusy = isBusy,
            isOnline = isOnline
        )
    }
}

@Composable
private fun SixAnswersRadarStrip(
    answers: MobileSixAnswers?,
    activeProvider: String,
    isOnline: Boolean
) {
    val colors = LocalCyberHudColors.current
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
            .cyberGlass(backgroundColor = colors.panelSurface, borderColor = colors.borderDim),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(if (isOnline) colors.accentEmerald else colors.accentRuby, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isOnline) "ENGINE ONLINE" else "OFFLINE",
                        style = AtroposTypography.badgeLabel,
                        color = if (isOnline) colors.accentEmerald else colors.accentRuby
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = activeProvider,
                        style = AtroposTypography.monoToken,
                        color = colors.accentViolet
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "SIX ANSWERS HUD",
                        style = AtroposTypography.badgeLabel,
                        color = colors.accentCyan
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Divider(color = colors.borderDim, thickness = 0.5.dp)
                    AnswerRow(label = "Problem", value = answers?.problem ?: "Waiting for autonomous goal")
                    AnswerRow(label = "Doing", value = answers?.doing ?: "Idle / Listening on Bridge")
                    AnswerRow(label = "Why", value = answers?.why ?: "Operator supervisory session")
                    AnswerRow(label = "Progress", value = answers?.progress ?: "Ready for execution")
                    AnswerRow(label = "Next", value = answers?.next ?: "Awaiting user command or prompt")
                    AnswerRow(label = "Evidence", value = answers?.evidence ?: "Cryptographic journal clean")
                }
            }
        }
    }
}

@Composable
private fun AnswerRow(label: String, value: String) {
    val colors = LocalCyberHudColors.current
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$label: ",
            style = AtroposTypography.monoToken,
            color = colors.accentCyan,
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = value,
            style = AtroposTypography.monoGutter,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ApprovalCardsSection(
    approvals: List<MobileApproval>,
    onApprove: (String, Boolean) -> Unit
) {
    val colors = LocalCyberHudColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        approvals.forEach { app ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .cyberGlass(backgroundColor = colors.cardSurface, borderColor = colors.accentAmber),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = colors.accentAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "APPROVAL REQUIRED",
                                style = AtroposTypography.badgeLabel,
                                color = colors.accentAmber
                            )
                        }

                        Text(
                            text = app.id,
                            style = AtroposTypography.monoGutter,
                            color = colors.textMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = app.detail,
                        style = AtroposTypography.hudBody,
                        color = colors.textPrimary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { onApprove(app.id, false) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accentRuby.copy(alpha = 0.2f),
                                contentColor = colors.accentRuby
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Deny",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Deny", style = AtroposTypography.badgeLabel)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { onApprove(app.id, true) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accentEmerald,
                                contentColor = colors.voidAbyss
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Approve",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Approve", style = AtroposTypography.badgeLabel, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinkingDrawerCard(thinking: MobileThinking) {
    val colors = LocalCyberHudColors.current
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
            .cyberGlass(backgroundColor = colors.panelSurface, borderColor = colors.accentViolet.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = "Thinking",
                        tint = colors.accentViolet,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "INTERNAL REASONING",
                        style = AtroposTypography.badgeLabel,
                        color = colors.accentViolet
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isExpanded) "Hide" else "Expand trace",
                        style = AtroposTypography.monoGutter,
                        color = colors.textMuted
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Text(
                    text = thinking.text,
                    style = AtroposTypography.monoCode,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun WorkstationMessageBubble(msg: MobileMessage) {
    val colors = LocalCyberHudColors.current
    val isUser = msg.sender.equals("user", ignoreCase = true)

    val bg = if (isUser) colors.cardSurface else colors.panelSurface
    val border = if (isUser) colors.borderDim else colors.borderSubtle

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .cyberGlass(backgroundColor = bg, borderColor = border),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isUser) "OPERATOR" else "ATROPOS ENGINE",
                    style = AtroposTypography.badgeLabel,
                    color = if (isUser) colors.accentAmber else colors.accentCyan
                )

                Text(
                    text = msg.sender,
                    style = AtroposTypography.monoGutter,
                    color = colors.textMuted
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = msg.body,
                style = AtroposTypography.hudBody,
                color = colors.textPrimary,
                lineHeight = 19.sp
            )
        }
    }
}

@Composable
private fun QuickCommandPillsBar(
    onOpenPalette: () -> Unit,
    onQuickCommand: (String) -> Unit
) {
    val colors = LocalCyberHudColors.current
    val quickPills = listOf("/goal", "/plan", "/heal", "/files", "/git status", "/doctor", "/specgraph")

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.voidAbyss)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            Box(
                modifier = Modifier
                    .background(colors.accentCyan, RoundedCornerShape(16.dp))
                    .clickable { onOpenPalette() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "/",
                        style = AtroposTypography.monoToken,
                        color = colors.voidAbyss,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "All Commands",
                        style = AtroposTypography.badgeLabel,
                        color = colors.voidAbyss
                    )
                }
            }
        }

        items(quickPills) { pill ->
            Box(
                modifier = Modifier
                    .background(colors.cardSurface, RoundedCornerShape(16.dp))
                    .clickable { onQuickCommand(pill) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = pill,
                    style = AtroposTypography.monoToken,
                    color = colors.accentCyan
                )
            }
        }
    }
}

@Composable
private fun WorkstationComposerBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onInterrupt: () -> Unit,
    isBusy: Boolean,
    isOnline: Boolean
) {
    val colors = LocalCyberHudColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.panelSurface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            placeholder = {
                Text(
                    text = "Message Atropos or type '/' for commands...",
                    style = AtroposTypography.hudBody,
                    color = colors.textMuted
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accentCyan,
                unfocusedBorderColor = colors.borderDim,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary
            )
        )

        Spacer(modifier = Modifier.width(8.dp))

        if (isBusy) {
            IconButton(
                onClick = onInterrupt,
                modifier = Modifier
                    .size(44.dp)
                    .background(colors.accentRuby, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Interrupt",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        } else {
            IconButton(
                onClick = onSend,
                enabled = isOnline && value.isNotBlank(),
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (isOnline && value.isNotBlank()) colors.accentCyan else colors.borderDim,
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = if (isOnline && value.isNotBlank()) colors.voidAbyss else colors.textMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun WorkstationWelcomeCard(
    onSelectSamplePrompt: (String) -> Unit,
    onOpenPalette: () -> Unit
) {
    val colors = LocalCyberHudColors.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .cyberGlass(backgroundColor = colors.cardSurface, borderColor = colors.borderGlow),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = colors.accentCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ATROPOS SOVEREIGN WORKSTATION",
                    style = AtroposTypography.hudHeader,
                    color = colors.textPrimary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Welcome to the mobile autonomous software development environment. Type naturally to launch goal trajectories, explore code in the mobile VS Code editor, or use the '/' command palette to control the engine.",
                style = AtroposTypography.hudBody,
                color = colors.textSecondary,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "SAMPLE ACTIONS:",
                style = AtroposTypography.badgeLabel,
                color = colors.accentViolet
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SampleActionRow(
                    prompt = "/goal audit codebase for null pointer safety and run tests",
                    onClick = { onSelectSamplePrompt("/goal audit codebase for null pointer safety and run tests") }
                )
                SampleActionRow(
                    prompt = "/heal compile gate diagnostics",
                    onClick = { onSelectSamplePrompt("/heal") }
                )
                SampleActionRow(
                    prompt = "Browse all / commands and categories",
                    onClick = onOpenPalette
                )
            }
        }
    }
}

@Composable
private fun SampleActionRow(prompt: String, onClick: () -> Unit) {
    val colors = LocalCyberHudColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .cyberGlass(backgroundColor = colors.panelSurface, borderColor = colors.borderDim)
            .padding(10.dp)
    ) {
        Text(
            text = "› $prompt",
            style = AtroposTypography.monoCode,
            color = colors.accentCyan
        )
    }
}
