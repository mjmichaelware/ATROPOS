/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.settings

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atropos.android.app.quota.TokenBurnTelemetryScreen
import com.atropos.android.app.swarm.SwarmMeshRadarScreen
import com.atropos.android.app.editor.HexBinaryViewerScreen
import com.atropos.android.app.theme.AtroposTypography
import com.atropos.android.app.theme.LocalCyberHudColors
import com.atropos.android.app.theme.cyberGlass

/**
 * Mobile Settings Matrix Screen with Columns and Rows.
 *
 * Implements:
 * - Engine Bridge & Network Host/Port configuration
 * - Neural Provider API Keys & Cascade Prioritization
 * - Sovereign Autonomous Execution Policies
 * - MCP Host & Tool Sandbox Limits
 * - Display Density & Cyber-HUD Aesthetics
 */
@Composable
fun SettingsMatrixScreen(
    currentHost: String = "127.0.0.1",
    currentPort: Int = 4317,
    onSaveBridgeSettings: (String, Int) -> Unit,
    onTestPing: (String, Int, (Boolean, Long) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalCyberHudColors.current

    var bridgeHost by remember { mutableStateOf(currentHost) }
    var bridgePortText by remember { mutableStateOf(currentPort.toString()) }
    var pingStatus by remember { mutableStateOf<String?>(null) }
    var isTestingPing by remember { mutableStateOf(false) }
    var showTokenBurn by remember { mutableStateOf(false) }
    var showSwarmRadar by remember { mutableStateOf(false) }
    var showHexViewer by remember { mutableStateOf(false) }

    if (showSwarmRadar) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(colors.voidAbyss)
        ) {
            SwarmMeshRadarScreen(
                onNavigateBack = { showSwarmRadar = false },
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    if (showHexViewer) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(colors.voidAbyss)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = colors.accentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "BYTECODE & MEMORY INSPECTOR",
                        style = AtroposTypography.monoToken,
                        color = colors.accentCyan
                    )
                }
                Button(
                    onClick = { showHexViewer = false },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.panelSurface)
                ) {
                    Text("View Settings", style = AtroposTypography.monoToken, color = colors.textPrimary)
                }
            }
            HexBinaryViewerScreen(modifier = Modifier.weight(1f))
        }
        return
    }

    if (showTokenBurn) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(colors.voidAbyss)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = colors.accentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "TOKEN BURN & QUOTA TELEMETRY",
                        style = AtroposTypography.monoToken,
                        color = colors.accentCyan
                    )
                }
                Button(
                    onClick = { showTokenBurn = false },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.panelSurface)
                ) {
                    Text("View Settings", style = AtroposTypography.monoToken, color = colors.textPrimary)
                }
            }
            TokenBurnTelemetryScreen(modifier = Modifier.weight(1f))
        }
        return
    }

    // Policy States
    var localOnlyMode by remember { mutableStateOf(false) }
    var paidGateLocked by remember { mutableStateOf(true) }
    var zeroRetentionResearch by remember { mutableStateOf(true) }
    var maxFileByteLimit by remember { mutableStateOf("524288") } // 512 KB

    // Provider Keys
    val providerKeys = remember {
        mutableStateMapOf(
            "ANTHROPIC_API_KEY" to "",
            "OPENAI_API_KEY" to "",
            "GROQ_API_KEY" to "",
            "GEMINI_API_KEY" to "",
            "DEEPSEEK_API_KEY" to "",
            "OPENROUTER_API_KEY" to ""
        )
    }

    val keyVisibility = remember {
        mutableStateMapOf<String, Boolean>()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.voidAbyss)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Section 1: Engine Bridge & Network Configuration
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .cyberGlass(backgroundColor = colors.panelSurface, borderColor = colors.accentCyan),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                tint = colors.accentCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ENGINE BRIDGE NETWORKING",
                                style = AtroposTypography.badgeLabel,
                                color = colors.accentCyan
                            )
                        }

                        if (pingStatus != null) {
                            Text(
                                text = pingStatus!!,
                                style = AtroposTypography.monoGutter,
                                color = if (pingStatus!!.contains("OK")) colors.accentEmerald else colors.accentRuby
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = bridgeHost,
                            onValueChange = { bridgeHost = it },
                            label = { Text("Bridge Host / IP", style = AtroposTypography.hudCaption) },
                            modifier = Modifier.weight(2f),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.accentCyan,
                                unfocusedBorderColor = colors.borderDim,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary
                            )
                        )

                        OutlinedTextField(
                            value = bridgePortText,
                            onValueChange = { bridgePortText = it },
                            label = { Text("Port", style = AtroposTypography.hudCaption) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.accentCyan,
                                unfocusedBorderColor = colors.borderDim,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { showSwarmRadar = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.cardSurface,
                                contentColor = colors.accentPurple
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Hub,
                                contentDescription = "Swarm Radar",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Swarm", style = AtroposTypography.badgeLabel)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { showHexViewer = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.cardSurface,
                                contentColor = colors.accentCyan
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = "Bytecode Hex Viewer",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Hex", style = AtroposTypography.badgeLabel)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { showTokenBurn = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.cardSurface,
                                contentColor = colors.accentAmber
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Token Quota",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Quota", style = AtroposTypography.badgeLabel)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                isTestingPing = true
                                val port = bridgePortText.toIntOrNull() ?: 4317
                                onTestPing(bridgeHost, port) { success, latencyMs ->
                                    isTestingPing = false
                                    pingStatus = if (success) "Ping OK (${latencyMs}ms)" else "Ping Failed"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.cardSurface,
                                contentColor = colors.accentCyan
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NetworkCheck,
                                contentDescription = "Test Ping",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Ping", style = AtroposTypography.badgeLabel)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                val port = bridgePortText.toIntOrNull() ?: 4317
                                onSaveBridgeSettings(bridgeHost, port)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accentCyan,
                                contentColor = colors.voidAbyss
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Save",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save", style = AtroposTypography.badgeLabel, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Section 2: Sovereign Execution Policy Matrix
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .cyberGlass(backgroundColor = colors.panelSurface, borderColor = colors.accentEmerald),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = colors.accentEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SOVEREIGN EXECUTION POLICIES",
                            style = AtroposTypography.badgeLabel,
                            color = colors.accentEmerald
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    PolicySwitchRow(
                        title = "Local-Only Mode (Air-Gap)",
                        subtitle = "Disables all remote network research planes. Core engine operates strictly offline.",
                        checked = localOnlyMode,
                        onCheckedChange = { localOnlyMode = it }
                    )

                    Divider(color = colors.borderDim, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

                    PolicySwitchRow(
                        title = "Paid Provider Emergency Gate",
                        subtitle = "Locks paid API calls upon exhausting free/local cascade tier unless manually approved.",
                        checked = paidGateLocked,
                        onCheckedChange = { paidGateLocked = it }
                    )

                    Divider(color = colors.borderDim, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

                    PolicySwitchRow(
                        title = "Zero-Retention Research Mode",
                        subtitle = "Forbids storing unverified remote Lakehouse tokens or research artifacts to disk.",
                        checked = zeroRetentionResearch,
                        onCheckedChange = { zeroRetentionResearch = it }
                    )
                }
            }
        }

        // Section 3: Neural Providers & API Key Matrix (Rows)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .cyberGlass(backgroundColor = colors.panelSurface, borderColor = colors.accentViolet),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = colors.accentViolet,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PROVIDER API CREDENTIALS MATRIX",
                            style = AtroposTypography.badgeLabel,
                            color = colors.accentViolet
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    providerKeys.keys.forEach { keyName ->
                        val isVisible = keyVisibility[keyName] ?: false
                        val currentVal = providerKeys[keyName] ?: ""

                        OutlinedTextField(
                            value = currentVal,
                            onValueChange = { providerKeys[keyName] = it },
                            label = { Text(keyName, style = AtroposTypography.hudCaption) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            singleLine = true,
                            visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { keyVisibility[keyName] = !isVisible }) {
                                    Icon(
                                        imageVector = if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle visibility",
                                        tint = colors.textMuted
                                    )
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.accentViolet,
                                unfocusedBorderColor = colors.borderDim,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PolicySwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = LocalCyberHudColors.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = AtroposTypography.hudSubhead,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = AtroposTypography.hudCaption,
                color = colors.textSecondary
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.accentEmerald,
                checkedTrackColor = colors.accentEmerald.copy(alpha = 0.3f),
                uncheckedThumbColor = colors.textMuted,
                uncheckedTrackColor = colors.cardSurface
            )
        )
    }
}
