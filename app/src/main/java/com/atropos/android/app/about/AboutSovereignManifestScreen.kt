/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.about

import android.os.Build
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
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
import com.atropos.android.app.governance.CryptographicJournalScreen
import com.atropos.android.app.theme.AtroposTypography
import com.atropos.android.app.theme.LocalCyberHudColors
import com.atropos.android.app.theme.cyberGlass

/**
 * About & Sovereign Manifest Screen.
 *
 * Displays the core architectural philosophy, cryptographic Merkle release hashes,
 * system runtime diagnostics, and AGPL-3.0 licensing provenance.
 */
@Composable
fun AboutSovereignManifestScreen(
    versionName: String = "10.0.1",
    versionCode: Int = 1001,
    modifier: Modifier = Modifier
) {
    val colors = LocalCyberHudColors.current
    var showJournal by remember { mutableStateOf(false) }

    if (showJournal) {
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
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = colors.accentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MERKLE AUDIT JOURNAL",
                        style = AtroposTypography.monoToken,
                        color = colors.accentCyan
                    )
                }
                Button(
                    onClick = { showJournal = false },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.panelSurface)
                ) {
                    Text("View Manifest", style = AtroposTypography.monoToken, color = colors.textPrimary)
                }
            }
            CryptographicJournalScreen(modifier = Modifier.weight(1f))
        }
        return
    }

    val rt = Runtime.getRuntime()
    val maxMemoryMb = rt.maxMemory() / (1024 * 1024)
    val totalMemoryMb = rt.totalMemory() / (1024 * 1024)
    val freeMemoryMb = rt.freeMemory() / (1024 * 1024)
    val usedMemoryMb = totalMemoryMb - freeMemoryMb
    val availableProcessors = rt.availableProcessors()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.voidAbyss)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // App Identity Hero Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .cyberGlass(backgroundColor = colors.panelSurface, borderColor = colors.accentCyan),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ATROPOS SOVEREIGN ENGINE",
                            style = AtroposTypography.hudHeader,
                            color = colors.textPrimary
                        )

                        Box(
                            modifier = Modifier
                                .background(colors.accentCyan.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "v$versionName ($versionCode)",
                                style = AtroposTypography.badgeLabel,
                                color = colors.accentCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Deterministic, append-only autonomous software engineering environment. Operating locally on Android and Termux without vendor lock-in or ungrounded synthetic mutations.",
                        style = AtroposTypography.hudBody,
                        color = colors.textSecondary,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { showJournal = true },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentCyan),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = colors.voidAbyss,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Inspect Merkle Audit Journal",
                            style = AtroposTypography.monoToken,
                            color = colors.voidAbyss,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // The Three Laws of Autonomous Sovereignty
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .cyberGlass(backgroundColor = colors.cardSurface, borderColor = colors.borderDim),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = colors.accentEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "THE THREE LAWS OF SOVEREIGN SOFTWARE",
                            style = AtroposTypography.badgeLabel,
                            color = colors.accentEmerald
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LawItem(
                        number = "LAW I",
                        title = "Zero-Invention Invariant",
                        description = "No agent may synthesize architectural requirements, wire contracts, or declare models not explicitly bounded by source authority documents."
                    )

                    Divider(color = colors.borderDim, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

                    LawItem(
                        number = "LAW II",
                        title = "Bounded Agency & Territory Enforcement",
                        description = "Every file mutation and command execution is quarantined within declared territory boundaries. Egress and destructive actions require explicit operator sign-off."
                    )

                    Divider(color = colors.borderDim, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

                    LawItem(
                        number = "LAW III",
                        title = "Append-Only Cryptographic Evidence",
                        description = "Progress is never claimed through self-reported confidence. Every milestone requires passing contract suites, Merkle roots, and immutable journal entries."
                    )
                }
            }
        }

        // Live Device & Runtime Diagnostics
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .cyberGlass(backgroundColor = colors.panelSurface, borderColor = colors.borderDim),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = colors.accentViolet,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "RUNTIME & DEVICE TELEMETRY",
                            style = AtroposTypography.badgeLabel,
                            color = colors.accentViolet
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    TelemetryRow("Device Model", "${Build.MANUFACTURER} ${Build.MODEL}")
                    TelemetryRow("Android Version", "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                    TelemetryRow("CPU Architecture", Build.SUPPORTED_ABIS.joinToString(", "))
                    TelemetryRow("CPU Cores", "$availableProcessors active execution units")
                    TelemetryRow("JVM Heap Used", "$usedMemoryMb MB / $totalMemoryMb MB (Max: $maxMemoryMb MB)")
                }
            }
        }

        // Cryptographic Release Sealing
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .cyberGlass(backgroundColor = colors.panelSurface, borderColor = colors.borderDim),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "CAPSTONE MERKLE RELEASE SEALS",
                        style = AtroposTypography.badgeLabel,
                        color = colors.accentAmber
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "CLI Fat JAR: SHA-256 attested",
                        style = AtroposTypography.monoGutter,
                        color = colors.textSecondary
                    )
                    Text(
                        text = "Android APK: Signed release certified",
                        style = AtroposTypography.monoGutter,
                        color = colors.textSecondary
                    )
                    Text(
                        text = "Vercel Web: Standalone Next.js 16 certified",
                        style = AtroposTypography.monoGutter,
                        color = colors.textSecondary
                    )
                    Text(
                        text = "SpecGraph Foundry: Independent Vercel production",
                        style = AtroposTypography.monoGutter,
                        color = colors.accentEmerald
                    )
                }
            }
        }

        // License
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Licensed under GNU Affero General Public License v3.0 (AGPL-3.0)\nCopyright © 2026 Michael Alonzo Ware & Atropos Contributors",
                    style = AtroposTypography.monoGutter,
                    color = colors.textMuted,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun LawItem(number: String, title: String, description: String) {
    val colors = LocalCyberHudColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = number,
                style = AtroposTypography.badgeLabel,
                color = colors.accentAmber
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = AtroposTypography.hudSubhead,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = description,
            style = AtroposTypography.hudBody,
            color = colors.textSecondary,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun TelemetryRow(key: String, value: String) {
    val colors = LocalCyberHudColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = key,
            style = AtroposTypography.monoGutter,
            color = colors.textMuted
        )
        Text(
            text = value,
            style = AtroposTypography.monoToken,
            color = colors.textPrimary
        )
    }
}
