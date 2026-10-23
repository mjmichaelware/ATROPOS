/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.specgraph

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atropos.android.app.theme.AtroposTypography
import com.atropos.android.app.theme.LocalCyberHudColors
import com.atropos.android.app.theme.cyberGlass

data class DagNode(
    val id: String,
    val label: String,
    val stage: String,
    val position: Offset,
    val status: String, // COMPLETED, RUNNING, PENDING
    val latencyMs: Int
)

data class DagEdge(
    val fromId: String,
    val toId: String
)

/**
 * Mobile Interactive SpecGraph Compiler DAG Canvas.
 *
 * Renders graph node topologies, bezier flow curves, and stage progress.
 */
@Composable
fun SpecGraphDagCanvas(
    onTriggerCompile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalCyberHudColors.current
    var selectedNode by remember { mutableStateOf<DagNode?>(null) }

    val nodes = remember {
        listOf(
            DagNode("n1", "Lexer", "Frontend", Offset(80f, 60f), "COMPLETED", 4),
            DagNode("n2", "Parser", "Frontend", Offset(240f, 60f), "COMPLETED", 8),
            DagNode("n3", "AST Lower", "Frontend", Offset(160f, 150f), "COMPLETED", 14),
            DagNode("n4", "DCE Pass", "MiddleEnd", Offset(80f, 240f), "COMPLETED", 6),
            DagNode("n5", "Opt Pass", "MiddleEnd", Offset(240f, 240f), "COMPLETED", 11),
            DagNode("n6", "Emitter", "Backend", Offset(160f, 330f), "COMPLETED", 10)
        )
    }

    val edges = remember {
        listOf(
            DagEdge("n1", "n3"),
            DagEdge("n2", "n3"),
            DagEdge("n3", "n4"),
            DagEdge("n3", "n5"),
            DagEdge("n4", "n6"),
            DagEdge("n5", "n6")
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .cyberGlass(backgroundColor = colors.cardSurface, borderColor = colors.borderDim),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.AccountTree,
                        contentDescription = null,
                        tint = colors.accentViolet,
                        modifier = Modifier.padding(2.dp)
                    )
                    Text(
                        text = "COMPILER DAG TOPOLOGY",
                        style = AtroposTypography.monoToken,
                        color = colors.accentViolet
                    )
                }

                Button(
                    onClick = onTriggerCompile,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentViolet.copy(alpha = 0.2f),
                        contentColor = colors.accentViolet
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(
                        text = "Execute Pass",
                        style = AtroposTypography.monoGutter,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Canvas Graph Rendering
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(colors.gutterBackground, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { tapOffset ->
                                val clicked = nodes.find {
                                    val dx = it.position.x - tapOffset.x
                                    val dy = it.position.y - tapOffset.y
                                    (dx * dx + dy * dy) < 1200f
                                }
                                selectedNode = clicked
                            }
                        }
                ) {
                    // Draw Directed Bezier Edges
                    edges.forEach { edge ->
                        val from = nodes.find { it.id == edge.fromId }?.position ?: Offset.Zero
                        val to = nodes.find { it.id == edge.toId }?.position ?: Offset.Zero

                        val path = Path().apply {
                            moveTo(from.x, from.y)
                            cubicTo(
                                from.x, (from.y + to.y) / 2f,
                                to.x, (from.y + to.y) / 2f,
                                to.x, to.y
                            )
                        }

                        drawPath(
                            path = path,
                            color = colors.borderDim,
                            style = Stroke(width = 2f)
                        )
                    }

                    // Draw Nodes
                    nodes.forEach { node ->
                        val isSelected = selectedNode?.id == node.id
                        val nodeColor = if (isSelected) colors.accentCyan else colors.accentViolet

                        // Node Circle Body
                        drawCircle(
                            color = colors.panelSurface,
                            radius = 24f,
                            center = node.position
                        )

                        // Node Border Ring
                        drawCircle(
                            color = nodeColor,
                            radius = 24f,
                            center = node.position,
                            style = Stroke(width = if (isSelected) 3f else 1.5f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Selected Node Details
            selectedNode?.let { node ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.panelSurface, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "NODE: ${node.label} [${node.stage}]",
                            style = AtroposTypography.monoToken,
                            color = colors.accentCyan
                        )
                        Text(
                            text = "${node.latencyMs}ms",
                            style = AtroposTypography.monoGutter,
                            color = colors.accentEmerald
                        )
                    }
                    Text(
                        text = "Status: ${node.status} · State verified in Merkle root",
                        style = AtroposTypography.monoGutter,
                        color = colors.textMuted
                    )
                }
            }
        }
    }
}
