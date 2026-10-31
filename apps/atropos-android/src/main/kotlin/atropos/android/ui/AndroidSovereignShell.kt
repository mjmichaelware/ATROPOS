/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ATOM 14.2: Explicit Enter Button Gate and Sovereign Workstation Shell (F-AND-001).
 *
 * Provides complete executive control of the engine on mobile.
 * Concludes the startup sequence with an accessible Enter button gate, requiring explicit
 * user confirmation to enter the active workspace.
 *
 * ## Research: Jetpack Compose SharedTransitionLayout APIs
 * Researches Compose SharedTransitionLayout APIs to animate the Enter button organically
 * into the primary command bar during the workspace entrance transition.
 */
data class AndroidShellViewState(
    val activeProjectName: String?,
    val isEngineRunning: Boolean,
    val answers: EngineSixAnswers,
    val queueEntriesCount: Int,
    val isQueueFrozen: Boolean,
    val hasConfirmedEntrance: Boolean = true
)

@Composable
fun AndroidSovereignShell(
    viewState: AndroidShellViewState,
    onExecuteCommand: (String) -> Unit,
    onSelectProject: (String) -> Unit,
    onConfirmEntrance: () -> Unit = {}
) {
    var localEntered by remember { mutableStateOf(viewState.hasConfirmedEntrance) }

    if (!localEntered) {
        WorkspaceEntranceGate(
            isEngineRunning = viewState.isEngineRunning,
            onEnterConfirmed = {
                localEntered = true
                onConfirmEntrance()
            }
        )
        return
    }

    AtroposHudTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF07100D))
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ATROPOS SOVEREIGN SHELL",
                        color = Color(0xFF10B981),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = viewState.activeProjectName ?: "No Active Project",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable {
                            onSelectProject(viewState.activeProjectName ?: "default")
                        }
                    )
                }
                Box(
                    modifier = Modifier
                        .background(
                            if (viewState.isEngineRunning) Color(0xFF065F46) else Color(0xFF7F1D1D)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (viewState.isEngineRunning) "ENGINE ONLINE" else "ENGINE STOPPED",
                        color = if (viewState.isEngineRunning) Color(0xFF34D399) else Color(0xFFFCA5A5),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Command Strip
            Text(
                text = "COMMAND STRIP",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            QuickCommandStrip(
                enabled = viewState.isEngineRunning,
                onCommandClick = onExecuteCommand
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Continuous 6 Answers Display
            Text(
                text = "SIX CONTINUOUS ANSWERS",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            ContinuousAnswersGrid(
                answers = viewState.answers,
                onAnswerClick = onExecuteCommand
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Embedded Terminal Mirror Viewport (port 8787 IPC)
            Text(
                text = "LIVE IPC TERMINAL MIRROR (PORT 8787)",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color(0xFF0F172A))
                    .padding(12.dp)
            ) {
                NativeTerminalViewport(
                    terminalSocketUrl = "ws://127.0.0.1:8787/terminal"
                )
            }
        }
    }
}

@Composable
fun QuickCommandStrip(
    enabled: Boolean,
    onCommandClick: (String) -> Unit
) {
    val commands = listOf(
        "/home",
        "/status",
        "/agent",
        "/queue",
        "/verify",
        "/mcp",
        "/benchmark"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        commands.forEach { cmd ->
            Button(
                onClick = { onCommandClick(cmd) },
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1E293B),
                    contentColor = Color(0xFF38BDF8)
                )
            ) {
                Text(
                    text = cmd,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun ContinuousAnswersGrid(
    answers: EngineSixAnswers,
    onAnswerClick: (String) -> Unit
) {
    val items = listOf(
        "PROBLEM" to answers.problem,
        "APPROACH" to answers.approach,
        "INVARIANTS" to answers.invariants,
        "CHANGES" to answers.changes,
        "VERIFY" to answers.verification,
        "CONFIDENCE" to "${(answers.confidence * 100).toInt()}%"
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { rowPair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowPair.forEach { (label, value) ->
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onAnswerClick(label) },
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF0F172A)
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = label,
                                color = Color(0xFF10B981),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (value.isBlank()) "—" else value,
                                color = Color(0xFFF1F5F9),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 2
                            )
                        }
                    }
                }
                if (rowPair.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
