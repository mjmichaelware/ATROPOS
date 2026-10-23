/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.quota

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atropos.android.app.theme.AtroposTypography
import com.atropos.android.app.theme.LocalCyberHudColors
import com.atropos.android.app.theme.cyberGlass

data class ProviderUsage(
    val name: String,
    val tier: String,
    val promptTokens: Long,
    val completionTokens: Long,
    val costDollars: Double,
    val percentageOfTotal: Float
)

/**
 * High-Density Autonomous Token Burn & Cost Telemetry Screen.
 *
 * Visualizes:
 * - Real-time token consumption rate (tok/sec)
 * - Active Context Window capacity utilization
 * - Monetary cost accumulator
 * - Multi-provider quota breakdown
 */
@Composable
fun TokenBurnTelemetryScreen(
    currentBurnRate: Int = 420,
    activeContextTokens: Int = 42890,
    maxContextTokens: Int = 200000,
    sessionCost: Double = 0.14,
    modifier: Modifier = Modifier
) {
    val colors = LocalCyberHudColors.current
    val scrollState = rememberScrollState()

    val providerUsages = remember {
        listOf(
            ProviderUsage("Sovereign Local LPU", "Free/Local", 124000, 38000, 0.00, 0.65f),
            ProviderUsage("Fast Inference Engine", "Standard", 45000, 12000, 0.04, 0.23f),
            ProviderUsage("Frontier Reasoning Engine", "Frontier", 18000, 6500, 0.10, 0.12f)
        )
    }

    val contextRatio = (activeContextTokens.toFloat() / maxContextTokens.toFloat()).coerceIn(0f, 1f)
    val animatedBurnRate = remember { Animatable(0f) }

    LaunchedEffect(currentBurnRate) {
        animatedBurnRate.animateTo(
            targetValue = currentBurnRate.toFloat(),
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.voidAbyss)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "TOKEN BURN & QUOTA TELEMETRY",
                    style = AtroposTypography.monoToken,
                    color = colors.accentCyan
                )
                Text(
                    text = "High-frequency consumption ledger",
                    style = AtroposTypography.monoGutter,
                    color = colors.textMuted
                )
            }

            Box(
                modifier = Modifier
                    .background(colors.accentEmerald.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "HEADROOM SAFE",
                    style = AtroposTypography.badgeLabel,
                    color = colors.accentEmerald
                )
            }
        }

        // Top Metric Cards Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Speedometer Gauge Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .cyberGlass(backgroundColor = colors.cardSurface, borderColor = colors.borderDim),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = colors.accentAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "BURN SPEED",
                        style = AtroposTypography.monoGutter,
                        color = colors.textMuted
                    )
                    Text(
                        text = "${animatedBurnRate.value.toInt()}",
                        style = AtroposTypography.monoToken,
                        color = colors.accentAmber,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "tok/sec",
                        style = AtroposTypography.monoGutter,
                        color = colors.textMuted,
                        fontSize = 9.sp
                    )
                }
            }

            // Cost Accumulator Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .cyberGlass(backgroundColor = colors.cardSurface, borderColor = colors.borderDim),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Payments,
                        contentDescription = null,
                        tint = colors.accentEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "SESSION COST",
                        style = AtroposTypography.monoGutter,
                        color = colors.textMuted
                    )
                    Text(
                        text = "$${String.format("%.2f", sessionCost)}",
                        style = AtroposTypography.monoToken,
                        color = colors.textPrimary,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "USD estimated",
                        style = AtroposTypography.monoGutter,
                        color = colors.textMuted,
                        fontSize = 9.sp
                    )
                }
            }

            // Subsidized Ratio Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .cyberGlass(backgroundColor = colors.cardSurface, borderColor = colors.borderDim),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = colors.accentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "SUBSIDIZED",
                        style = AtroposTypography.monoGutter,
                        color = colors.textMuted
                    )
                    Text(
                        text = "65%",
                        style = AtroposTypography.monoToken,
                        color = colors.accentCyan,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "via local LPU",
                        style = AtroposTypography.monoGutter,
                        color = colors.textMuted,
                        fontSize = 9.sp
                    )
                }
            }
        }

        // Context Window Gauge Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .cyberGlass(backgroundColor = colors.cardSurface, borderColor = colors.borderDim),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CONTEXT WINDOW UTILIZATION",
                        style = AtroposTypography.monoToken,
                        color = colors.textPrimary
                    )

                    Text(
                        text = "${activeContextTokens} / ${maxContextTokens} tok",
                        style = AtroposTypography.monoGutter,
                        color = colors.accentCyan
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = contextRatio,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = colors.accentCyan,
                    trackColor = colors.borderDim
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Saturation: ${(contextRatio * 100).toInt()}%",
                        style = AtroposTypography.monoGutter,
                        color = colors.textMuted
                    )
                    Text(
                        text = "Remaining: ${maxContextTokens - activeContextTokens} tokens",
                        style = AtroposTypography.monoGutter,
                        color = colors.textMuted
                    )
                }
            }
        }

        // Provider Breakdown Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .cyberGlass(backgroundColor = colors.cardSurface, borderColor = colors.borderDim),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "NEURAL PROVIDER DISPATCH MATRIX",
                    style = AtroposTypography.monoToken,
                    color = colors.textPrimary
                )

                providerUsages.forEach { usage ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = usage.name,
                                    style = AtroposTypography.monoToken,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "[${usage.tier}]",
                                    style = AtroposTypography.monoGutter,
                                    color = colors.accentViolet
                                )
                            }
                            Text(
                                text = "$${String.format("%.2f", usage.costDollars)}",
                                style = AtroposTypography.monoToken,
                                color = colors.accentEmerald
                            )
                        }

                        LinearProgressIndicator(
                            progress = usage.percentageOfTotal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            color = colors.accentViolet,
                            trackColor = colors.borderDim
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Prompt: ${usage.promptTokens} · Completion: ${usage.completionTokens}",
                                style = AtroposTypography.monoGutter,
                                color = colors.textMuted
                            )
                            Text(
                                text = "${(usage.percentageOfTotal * 100).toInt()}% of load",
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
