/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.swarm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.atropos.android.app.theme.AtroposTypography
import com.atropos.android.app.theme.LocalCyberHudColors
import com.atropos.android.app.theme.cyberGlass

enum class SwarmRole {
    SUPERVISOR,
    PLANNER,
    CODER,
    VERIFIER
}

data class SwarmAgent(
    val id: String,
    val name: String,
    val role: SwarmRole,
    val model: String,
    val status: String, // WORKING, IDLE, DEGRADED
    val cpuPercent: Int,
    val memoryMb: Int,
    val tokensBurned: Long,
    val activeTask: String,
    val permissions: List<String>
)

val INITIAL_SWARM_AGENTS = listOf(
    SwarmAgent(
        id = "agent-sup-01",
        name = "Autonomous Supervisor",
        role = SwarmRole.SUPERVISOR,
        model = "sovereign-alpha-3.7",
        status = "WORKING",
        cpuPercent = 28,
        memoryMb = 512,
        tokensBurned = 14200L,
        activeTask = "Orchestrating multi-model lattice verification and Merkle attestation",
        permissions = listOf("fs_read", "fs_write", "terminal_exec", "merkle_seal")
    ),
    SwarmAgent(
        id = "agent-plan-02",
        name = "SpecGraph Planner",
        role = SwarmRole.PLANNER,
        model = "gemini-2.5-pro",
        status = "WORKING",
        cpuPercent = 14,
        memoryMb = 380,
        tokensBurned = 9800L,
        activeTask = "Decomposing human intent into deterministic DAG node specifications",
        permissions = listOf("fs_read", "specgraph_export")
    ),
    SwarmAgent(
        id = "agent-coder-03",
        name = "AST Synthesis Specialist",
        role = SwarmRole.CODER,
        model = "llama-3.3-70b-versatile",
        status = "WORKING",
        cpuPercent = 45,
        memoryMb = 640,
        tokensBurned = 23400L,
        activeTask = "Generating Kotlin implementations and resolving AST compiler invariants",
        permissions = listOf("fs_read", "fs_write")
    ),
    SwarmAgent(
        id = "agent-veri-04",
        name = "Contract Verification Sentinel",
        role = SwarmRole.VERIFIER,
        model = "deepseek-r1:14b-local",
        status = "IDLE",
        cpuPercent = 5,
        memoryMb = 290,
        tokensBurned = 6100L,
        activeTask = "Awaiting AST compilation pass to execute test suite assertion bounds",
        permissions = listOf("fs_read", "terminal_exec")
    )
)

/**
 * Sovereign Android Swarm Operations & Mesh Radar Screen.
 *
 * Provides real-time control, heartbeat inspection, and task dispatching
 * across the autonomous agent hierarchy.
 */
@Composable
fun SwarmMeshRadarScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalCyberHudColors.current
    var agents by remember { mutableStateOf(INITIAL_SWARM_AGENTS) }
    var selectedAgent by remember { mutableStateOf<SwarmAgent?>(null) }
    var dispatchQuery by remember { mutableStateOf("") }
    var dispatchFeedback by remember { mutableStateOf<String?>(null) }

    fun pingAgent(agentId: String) {
        agents = agents.map {
            if (it.id == agentId) {
                it.copy(
                    cpuPercent = (it.cpuPercent + 3).coerceIn(5, 95),
                    tokensBurned = it.tokensBurned + 150L
                )
            } else it
        }
    }

    fun handleDispatchTask(targetAgentId: String) {
        if (dispatchQuery.isBlank()) return
        agents = agents.map {
            if (it.id == targetAgentId) {
                it.copy(
                    status = "WORKING",
                    activeTask = dispatchQuery.trim(),
                    tokensBurned = it.tokensBurned + 400L
                )
            } else it
        }
        dispatchFeedback = "Task dispatched to $targetAgentId successfully."
        dispatchQuery = ""
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .size(36.dp)
                            .background(colors.surfaceRaised, RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.accentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "SWARM MESH OPERATIONS",
                            style = AtroposTypography.hudHeader,
                            color = colors.textPrimary,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Hierarchical Agent Network & Telemetry",
                            style = AtroposTypography.hudSubHeader,
                            color = colors.textMuted,
                            fontSize = 10.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .background(colors.accentCyan.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .border(1.dp, colors.accentCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${agents.size} AGENTS ACTIVE",
                        style = AtroposTypography.monoToken,
                        color = colors.accentCyan,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Swarm Overview Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .cyberGlass(backgroundColor = colors.cardSurface, borderColor = colors.borderDim),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("TOTAL BURN", style = AtroposTypography.hudLabel, color = colors.textMuted, fontSize = 9.sp)
                        val totalTokens = agents.sumOf { it.tokensBurned }
                        Text("${totalTokens / 1000}k tok", style = AtroposTypography.monoToken, color = colors.accentAmber, fontSize = 14.sp)
                    }
                }

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .cyberGlass(backgroundColor = colors.cardSurface, borderColor = colors.borderDim),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("AVG CPU", style = AtroposTypography.hudLabel, color = colors.textMuted, fontSize = 9.sp)
                        val avgCpu = agents.map { it.cpuPercent }.average().toInt()
                        Text("$avgCpu%", style = AtroposTypography.monoToken, color = colors.accentCyan, fontSize = 14.sp)
                    }
                }

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .cyberGlass(backgroundColor = colors.cardSurface, borderColor = colors.borderDim),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("SWARM HEALTH", style = AtroposTypography.hudLabel, color = colors.textMuted, fontSize = 9.sp)
                        Text("OPTIMAL", style = AtroposTypography.monoToken, color = colors.accentEmerald, fontSize = 14.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Feedback banner
            if (dispatchFeedback != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.accentEmerald.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .border(1.dp, colors.accentEmerald.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dispatchFeedback ?: "",
                            style = AtroposTypography.monoToken,
                            color = colors.accentEmerald,
                            fontSize = 11.sp
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = colors.textMuted,
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { dispatchFeedback = null }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Agents List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(agents, key = { it.id }) { agent ->
                    val isSelected = selectedAgent?.id == agent.id

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedAgent = if (isSelected) null else agent }
                            .cyberGlass(
                                backgroundColor = if (isSelected) colors.panelSurface else colors.cardSurface,
                                borderColor = if (isSelected) colors.accentCyan else colors.borderDim
                            ),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(
                                                if (agent.status == "WORKING") colors.accentEmerald else colors.textMuted,
                                                CircleShape
                                            )
                                    )
                                    Text(
                                        text = agent.name,
                                        style = AtroposTypography.hudHeader,
                                        color = colors.textPrimary,
                                        fontSize = 13.sp
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(colors.accentViolet.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = agent.role.name,
                                            style = AtroposTypography.monoToken,
                                            color = colors.accentViolet,
                                            fontSize = 9.sp
                                        )
                                    }

                                    IconButton(
                                        onClick = { pingAgent(agent.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Ping",
                                            tint = colors.accentCyan,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Model: ${agent.model}",
                                style = AtroposTypography.monoGutter,
                                color = colors.textMuted,
                                fontSize = 10.sp
                            )

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = agent.activeTask,
                                style = AtroposTypography.monoToken,
                                color = colors.textSecondary,
                                fontSize = 11.sp
                            )

                            // Telemetry bar
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("CPU: ${agent.cpuPercent}%", style = AtroposTypography.monoGutter, color = colors.textMuted, fontSize = 10.sp)
                                Text("RAM: ${agent.memoryMb}MB", style = AtroposTypography.monoGutter, color = colors.textMuted, fontSize = 10.sp)
                                Text("Tokens: ${agent.tokensBurned}", style = AtroposTypography.monoGutter, color = colors.accentAmber, fontSize = 10.sp)
                            }

                            // Expanded detail when selected
                            AnimatedVisibility(visible = isSelected) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp)
                                        .border(1.dp, colors.borderDim, RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = "MCP TOOL PRIVILEGES",
                                        style = AtroposTypography.hudLabel,
                                        color = colors.accentCyan,
                                        fontSize = 9.sp
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        agent.permissions.forEach { perm ->
                                            Box(
                                                modifier = Modifier
                                                    .background(colors.surfaceRaised, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(perm, style = AtroposTypography.monoGutter, color = colors.textPrimary, fontSize = 9.sp)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "DISPATCH SUB-GOAL",
                                        style = AtroposTypography.hudLabel,
                                        color = colors.accentCyan,
                                        fontSize = 9.sp
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = dispatchQuery,
                                            onValueChange = { dispatchQuery = it },
                                            placeholder = {
                                                Text("e.g. Audit Kotlin contract tests", style = AtroposTypography.monoGutter, color = colors.textMuted, fontSize = 10.sp)
                                            },
                                            modifier = Modifier.weight(1f),
                                            textStyle = AtroposTypography.monoToken.copy(fontSize = 11.sp, color = colors.textPrimary),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = colors.accentCyan,
                                                unfocusedBorderColor = colors.borderDim,
                                                focusedContainerColor = colors.surface,
                                                unfocusedContainerColor = colors.surface
                                            ),
                                            singleLine = true
                                        )

                                        IconButton(
                                            onClick = { handleDispatchTask(agent.id) },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(colors.accentCyan, RoundedCornerShape(8.dp))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Send,
                                                contentDescription = "Dispatch",
                                                tint = Color.Black,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
