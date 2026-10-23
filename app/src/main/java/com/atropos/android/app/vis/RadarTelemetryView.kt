/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.vis

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atropos.android.app.bridge.MobileSixAnswers
import com.atropos.android.app.theme.AtroposTypography
import com.atropos.android.app.theme.LocalCyberHudColors
import com.atropos.android.app.theme.cyberGlass
import kotlin.math.cos
import kotlin.math.sin

data class RadarAxis(
    val label: String,
    val valueNormalized: Float, // 0f to 1f
    val rawValue: String,
    val health: String // OPTIMAL, DEGRADED, BLOCKED
)

/**
 * Continuous Six Answers Multi-Axis Hexagonal Polar Radar HUD.
 *
 * Visualizes the 6 dimensions of autonomous state:
 * 1. Objective (Goal Alignment)
 * 2. Doing (Active AST Phase)
 * 3. Why (Supervisory Justification)
 * 4. Progress (Verified Ratio)
 * 5. Next (Planned Step)
 * 6. Evidence (Cryptographic Attestation)
 */
@Composable
fun RadarTelemetryView(
    sixAnswers: MobileSixAnswers?,
    modifier: Modifier = Modifier
) {
    val colors = LocalCyberHudColors.current
    var selectedAxis by remember { mutableStateOf<RadarAxis?>(null) }

    val axes = remember(sixAnswers) {
        listOf(
            RadarAxis("Objective", 0.95f, sixAnswers?.objective?.value ?: "SWE-Bench Target", sixAnswers?.objective?.health ?: "OPTIMAL"),
            RadarAxis("Doing", 0.70f, sixAnswers?.doing?.value ?: "Compiling AST", sixAnswers?.doing?.health ?: "OPTIMAL"),
            RadarAxis("Why", 0.85f, sixAnswers?.why?.value ?: "Invariant Preservation", sixAnswers?.why?.health ?: "OPTIMAL"),
            RadarAxis("Progress", 0.68f, sixAnswers?.progress?.value ?: "68% Complete", sixAnswers?.progress?.health ?: "OPTIMAL"),
            RadarAxis("Next", 0.80f, sixAnswers?.next?.value ?: "Emit Merkle Seal", sixAnswers?.next?.health ?: "OPTIMAL"),
            RadarAxis("Evidence", 1.0f, sixAnswers?.evidence?.value ?: "Attested SHA-256", sixAnswers?.evidence?.health ?: "OPTIMAL")
        )
    }

    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .cyberGlass(backgroundColor = colors.cardSurface, borderColor = colors.borderDim),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CONTINUOUS SIX ANSWERS RADAR",
                    style = AtroposTypography.monoToken,
                    color = colors.accentCyan
                )

                Text(
                    text = "HEXAGONAL HUD",
                    style = AtroposTypography.monoGutter,
                    color = colors.textMuted
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Canvas Polar Chart
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clickable { selectedAxis = null },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = (size.minDimension / 2f) * 0.82f
                    val angleStep = (2f * Math.PI / axes.size).toFloat()

                    // Draw Background Rings (3 concentric hexagons)
                    for (step in 1..3) {
                        val r = radius * (step / 3f)
                        val ringPath = Path()
                        for (i in axes.indices) {
                            val angle = i * angleStep - (Math.PI / 2f).toFloat()
                            val x = center.x + r * cos(angle)
                            val y = center.y + r * sin(angle)
                            if (i == 0) ringPath.moveTo(x, y) else ringPath.lineTo(x, y)
                        }
                        ringPath.close()
                        drawPath(
                            path = ringPath,
                            color = colors.borderDim.copy(alpha = 0.6f),
                            style = Stroke(width = 1f)
                        )
                    }

                    // Draw Spokes from center
                    for (i in axes.indices) {
                        val angle = i * angleStep - (Math.PI / 2f).toFloat()
                        val endX = center.x + radius * cos(angle)
                        val endY = center.y + radius * sin(angle)
                        drawLine(
                            color = colors.borderDim,
                            start = center,
                            end = Offset(endX, endY),
                            strokeWidth = 1f
                        )
                    }

                    // Draw Data Polygon
                    val dataPath = Path()
                    val animatedFactor = animProgress.value

                    axes.forEachIndexed { i, axis ->
                        val r = radius * axis.valueNormalized * animatedFactor
                        val angle = i * angleStep - (Math.PI / 2f).toFloat()
                        val x = center.x + r * cos(angle)
                        val y = center.y + r * sin(angle)
                        if (i == 0) dataPath.moveTo(x, y) else dataPath.lineTo(x, y)
                    }
                    dataPath.close()

                    // Fill Data Polygon
                    drawPath(
                        path = dataPath,
                        color = colors.accentCyan.copy(alpha = 0.25f)
                    )

                    // Stroke Data Polygon
                    drawPath(
                        path = dataPath,
                        color = colors.accentCyan,
                        style = Stroke(width = 2.5f)
                    )

                    // Draw Vertices
                    axes.forEachIndexed { i, axis ->
                        val r = radius * axis.valueNormalized * animatedFactor
                        val angle = i * angleStep - (Math.PI / 2f).toFloat()
                        val x = center.x + r * cos(angle)
                        val y = center.y + r * sin(angle)
                        drawCircle(
                            color = colors.accentCyan,
                            radius = 4f,
                            center = Offset(x, y)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Axis Pill Selectors
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                axes.forEach { axis ->
                    val isSelected = selectedAxis?.label == axis.label
                    Text(
                        text = axis.label.take(4).uppercase(),
                        style = AtroposTypography.monoGutter,
                        color = if (isSelected) colors.accentCyan else colors.textMuted,
                        modifier = Modifier
                            .clickable { selectedAxis = axis }
                            .padding(4.dp)
                    )
                }
            }

            // Selected Vertex Detail Drawer
            selectedAxis?.let { axis ->
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.panelSurface, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "${axis.label.uppercase()} [${axis.health}]",
                        style = AtroposTypography.monoToken,
                        color = colors.accentCyan
                    )
                    Text(
                        text = axis.rawValue,
                        style = AtroposTypography.hudBody,
                        color = colors.textPrimary
                    )
                }
            }
        }
    }
}
