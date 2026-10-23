/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.command

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atropos.android.app.theme.AtroposTypography
import com.atropos.android.app.theme.LocalCyberHudColors
import com.atropos.android.app.theme.cyberGlass

/**
 * Deep, comprehensive command specification and documentation view.
 * Shows detailed prose, argument schemas, flags, and executable examples.
 */
@Composable
fun CommandDetailSheetContent(
    command: AtroposCommandSpec,
    onInsert: () -> Unit,
    onExecute: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalCyberHudColors.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Command Title & Safety
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .cyberGlass(backgroundColor = colors.cardSurface, borderColor = colors.borderGlow),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = command.name,
                            style = AtroposTypography.commandPrompt,
                            fontSize = 20.sp,
                            color = colors.accentCyan,
                            fontWeight = FontWeight.Bold
                        )
                        SafetyBadge(tier = command.safetyTier)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = command.synopsis,
                        style = AtroposTypography.hudSubhead,
                        color = colors.textPrimary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Category: ${command.category.title}" +
                                if (command.subcategory != null) " › ${command.subcategory}" else "",
                        style = AtroposTypography.hudCaption,
                        color = colors.accentViolet
                    )
                }
            }
        }

        // Usage Syntax Block
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "SYNTAX",
                    style = AtroposTypography.badgeLabel,
                    color = colors.textMuted
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .cyberGlass(backgroundColor = colors.gutterBackground, borderColor = colors.borderDim)
                        .padding(12.dp)
                ) {
                    Text(
                        text = command.usage,
                        style = AtroposTypography.monoCode,
                        color = colors.accentEmerald
                    )
                }
            }
        }

        // Prose Description
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "SPECIFICATION & ARCHITECTURE",
                    style = AtroposTypography.badgeLabel,
                    color = colors.textMuted
                )
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .cyberGlass(backgroundColor = colors.panelSurface, borderColor = colors.borderDim),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Text(
                        text = command.proseDescription,
                        style = AtroposTypography.hudBody,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(14.dp),
                        lineHeight = 20.sp
                    )
                }
            }
        }

        // Arguments Table
        if (command.arguments.isNotEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "ARGUMENTS",
                        style = AtroposTypography.badgeLabel,
                        color = colors.textMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .cyberGlass(backgroundColor = colors.cardSurface, borderColor = colors.borderDim),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            command.arguments.forEachIndexed { idx, arg ->
                                if (idx > 0) {
                                    Divider(
                                        color = colors.borderDim,
                                        thickness = 0.5.dp,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = arg.name,
                                        style = AtroposTypography.monoToken,
                                        color = colors.accentCyan
                                    )
                                    Text(
                                        text = if (arg.required) "REQUIRED" else "OPTIONAL",
                                        style = AtroposTypography.badgeLabel,
                                        color = if (arg.required) colors.accentRuby else colors.textMuted
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = arg.description,
                                    style = AtroposTypography.hudCaption,
                                    color = colors.textSecondary
                                )
                                if (arg.defaultValue != null) {
                                    Text(
                                        text = "Default: ${arg.defaultValue}",
                                        style = AtroposTypography.monoGutter,
                                        color = colors.accentAmber
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Flags Table
        if (command.flags.isNotEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "FLAGS & OPTIONS",
                        style = AtroposTypography.badgeLabel,
                        color = colors.textMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .cyberGlass(backgroundColor = colors.cardSurface, borderColor = colors.borderDim),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            command.flags.forEachIndexed { idx, flag ->
                                if (idx > 0) {
                                    Divider(
                                        color = colors.borderDim,
                                        thickness = 0.5.dp,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                                Text(
                                    text = flag.flag + (flag.alias?.let { " ($it)" } ?: ""),
                                    style = AtroposTypography.monoToken,
                                    color = colors.accentAmber
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = flag.description,
                                    style = AtroposTypography.hudCaption,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Example Usages
        if (command.exampleUsages.isNotEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "EXAMPLES",
                        style = AtroposTypography.badgeLabel,
                        color = colors.textMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        command.exampleUsages.forEach { ex ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .cyberGlass(backgroundColor = colors.gutterBackground, borderColor = colors.borderDim)
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = ex,
                                    style = AtroposTypography.monoCode,
                                    color = colors.textPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Action Buttons
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onInsert,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.cardSurface,
                        contentColor = colors.accentCyan
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "Insert",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Insert into Composer",
                        style = AtroposTypography.hudSubhead
                    )
                }

                Button(
                    onClick = onExecute,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentCyan,
                        contentColor = colors.voidAbyss
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Run",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Execute Now",
                        style = AtroposTypography.hudSubhead,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
