/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.specgraph

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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

data class SpecGraphNode(
    val id: String,
    val title: String,
    val requirementStatement: String,
    val status: NodeStatus,
    val dependencies: List<String> = emptyList(),
    val merkleHash: String = ""
)

enum class NodeStatus(val label: String) {
    PENDING("Pending"),
    SYNTHESIZING("Synthesizing"),
    VERIFIED("Verified"),
    BLOCKED("Blocked")
}

/**
 * Mobile SpecGraph Visualizer Screen.
 * Renders the dependency DAG, requirement statements, and links to the SpecGraph Vercel production deployment.
 */
@Composable
fun SpecGraphMobileViewer(
    onOpenVercelProduction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalCyberHudColors.current
    var isCanvasMode by remember { mutableStateOf(false) }

    val sampleNodes = remember {
        listOf(
            SpecGraphNode(
                id = "SG-CORE-001",
                title = "Source Authority Compiler",
                requirementStatement = "AST parser must reject ungrounded hallucinations against the source document lattice.",
                status = NodeStatus.VERIFIED,
                merkleHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
            ),
            SpecGraphNode(
                id = "SG-DAG-002",
                title = "Execution Wave Synthesizer",
                requirementStatement = "DAG partition manager executes disjoint worktrees with zero cross-file lock contention.",
                status = NodeStatus.VERIFIED,
                dependencies = listOf("SG-CORE-001"),
                merkleHash = "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb"
            ),
            SpecGraphNode(
                id = "SG-HEAL-003",
                title = "Autonomous AST Self-Healing",
                requirementStatement = "Compiler diagnostics trigger candidate mutation synthesis until exit code is 0.",
                status = NodeStatus.SYNTHESIZING,
                dependencies = listOf("SG-DAG-002"),
                merkleHash = "4e07408562bedb8b60ce05c1decfe3ad16b72230967de01f640b7e4729b49fce"
            ),
            SpecGraphNode(
                id = "SG-CERT-004",
                title = "Cryptographic Merkle Release Gate",
                requirementStatement = "Seals JAR, APK, and Web builds under unified immutable release root.",
                status = NodeStatus.PENDING,
                dependencies = listOf("SG-HEAL-003"),
                merkleHash = "4b227777d4dd1fc61c6f884f48641d02b4d121d3fd328cb08b5531fcacdabf8a"
            )
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.voidAbyss)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // SpecGraph Header & Vercel Production Link
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .cyberGlass(backgroundColor = colors.panelSurface, borderColor = colors.accentViolet),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccountTree,
                                contentDescription = null,
                                tint = colors.accentViolet,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SPECGRAPH FOUNDRY",
                                style = AtroposTypography.hudHeader,
                                color = colors.textPrimary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .background(colors.accentEmerald.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "INDEPENDENT VERCEL PROD",
                                style = AtroposTypography.badgeLabel,
                                color = colors.accentEmerald
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "SpecGraph Foundry compiles requirements into execution lattices. Deployed independently to Vercel with Python compiler support and interactive graph visualization.",
                        style = AtroposTypography.hudBody,
                        color = colors.textSecondary,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onOpenVercelProduction,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentViolet,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = "Open",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Open SpecGraph Vercel Web Portal",
                            style = AtroposTypography.hudSubhead,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { isCanvasMode = !isCanvasMode },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.panelSurface,
                            contentColor = colors.accentCyan
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountTree,
                            contentDescription = "Toggle DAG View",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isCanvasMode) "Show Specification Table" else "Show Interactive DAG Canvas",
                            style = AtroposTypography.hudSubhead,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (isCanvasMode) {
            item {
                SpecGraphDagCanvas(
                    onTriggerCompile = {
                        // Triggers DAG compile pass via bridge
                    }
                )
            }
        } else {
            // Lattice Nodes Section
            item {
                Text(
                    text = "ACTIVE SPECIFICATION LATTICE",
                    style = AtroposTypography.badgeLabel,
                    color = colors.accentCyan
                )
            }

            items(sampleNodes) { node ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .cyberGlass(backgroundColor = colors.cardSurface, borderColor = colors.borderDim),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = node.id,
                            style = AtroposTypography.monoToken,
                            color = colors.accentCyan
                        )
                        NodeStatusBadge(status = node.status)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = node.title,
                        style = AtroposTypography.hudSubhead,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = node.requirementStatement,
                        style = AtroposTypography.hudBody,
                        color = colors.textSecondary
                    )

                    if (node.dependencies.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Depends on: ",
                                style = AtroposTypography.monoGutter,
                                color = colors.textMuted
                            )
                            node.dependencies.forEach { dep ->
                                Box(
                                    modifier = Modifier
                                        .background(colors.panelSurface, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = dep,
                                        style = AtroposTypography.monoGutter,
                                        color = colors.accentViolet
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                        }
                    }

                    if (node.merkleHash.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "SHA-256: ${node.merkleHash.take(16)}...",
                            style = AtroposTypography.monoGutter,
                            color = colors.textMuted
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun NodeStatusBadge(status: NodeStatus) {
    val colors = LocalCyberHudColors.current
    val (bg, fg, icon) = when (status) {
        NodeStatus.VERIFIED -> Triple(colors.accentEmerald.copy(alpha = 0.15f), colors.accentEmerald, Icons.Default.CheckCircle)
        NodeStatus.SYNTHESIZING -> Triple(colors.accentCyan.copy(alpha = 0.15f), colors.accentCyan, Icons.Default.Pending)
        NodeStatus.PENDING -> Triple(colors.borderDim, colors.textMuted, Icons.Default.Pending)
        NodeStatus.BLOCKED -> Triple(colors.accentRuby.copy(alpha = 0.15f), colors.accentRuby, Icons.Default.Warning)
    }

    Row(
        modifier = Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = status.label,
            style = AtroposTypography.badgeLabel,
            color = fg
        )
    }
}
