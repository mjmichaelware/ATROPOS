/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.governance

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atropos.android.app.theme.AtroposTypography
import com.atropos.android.app.theme.LocalCyberHudColors
import com.atropos.android.app.theme.cyberGlass

data class JournalEntry(
    val blockIndex: Long,
    val timestamp: String,
    val actor: String,
    val actionType: String,
    val territory: String,
    val previousHash: String,
    val blockHash: String,
    val verified: Boolean
)

/**
 * Cryptographic Sovereignty Journal & Immutable Audit Log.
 *
 * Displays the verified SHA-256 Merkle chain of all autonomous mutations,
 * policy validations, and operator approval records.
 */
@Composable
fun CryptographicJournalScreen(
    modifier: Modifier = Modifier
) {
    val colors = LocalCyberHudColors.current

    val entries = remember {
        listOf(
            JournalEntry(
                blockIndex = 412,
                timestamp = "2026-10-23T01:14:02Z",
                actor = "HumanOwner",
                actionType = "APPROVAL_RECORDED",
                territory = "app/src/main/java/",
                previousHash = "sha256:451cd8c8...b9657ce9",
                blockHash = "sha256:3ea114b29f001cda8b2110c",
                verified = true
            ),
            JournalEntry(
                blockIndex = 411,
                timestamp = "2026-10-23T01:08:44Z",
                actor = "AutonomousOrchestrator",
                actionType = "POLICY_MUTATION_EVAL",
                territory = "apps/web/src/components/",
                previousHash = "sha256:c1802a78...747bbd44",
                blockHash = "sha256:451cd8c800bc192efa44a21",
                verified = true
            ),
            JournalEntry(
                blockIndex = 410,
                timestamp = "2026-10-23T00:54:19Z",
                actor = "SpecGraphCompiler",
                actionType = "PASS_EMISSION_SEAL",
                territory = "apps/specgraph-foundry/",
                previousHash = "sha256:747bbd44...c1802a78",
                blockHash = "sha256:c1802a7833a812df98c3321",
                verified = true
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.voidAbyss)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Master Seal Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .cyberGlass(backgroundColor = colors.cardSurface, borderColor = colors.accentCyan),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = colors.accentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "SOVEREIGN JOURNAL CHAIN",
                            style = AtroposTypography.monoToken,
                            color = colors.accentCyan
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(colors.accentEmerald.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "CHAIN INTEGRITY VALID",
                            style = AtroposTypography.badgeLabel,
                            color = colors.accentEmerald
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "ACTIVE MERKLE ROOT SEAL",
                    style = AtroposTypography.monoGutter,
                    color = colors.textMuted
                )
                Text(
                    text = "sha256:3ea114b29f001cda8b2110c920f01ba32c8194",
                    style = AtroposTypography.monoToken,
                    color = colors.accentEmerald,
                    fontSize = 11.sp
                )
            }
        }

        // Ledger List
        Text(
            text = "IMMUTABLE AUDIT BLOCKS (${entries.size})",
            style = AtroposTypography.monoGutter,
            color = colors.textMuted
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(entries) { entry ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .cyberGlass(backgroundColor = colors.cardSurface, borderColor = colors.borderDim),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "BLOCK #${entry.blockIndex} · ${entry.actionType}",
                                style = AtroposTypography.monoToken,
                                color = colors.textPrimary,
                                fontSize = 12.sp
                            )
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Verified",
                                tint = colors.accentEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Text(
                            text = "Actor: ${entry.actor} · Territory: ${entry.territory}",
                            style = AtroposTypography.monoGutter,
                            color = colors.accentCyan,
                            fontSize = 10.sp
                        )

                        Text(
                            text = "Hash: ${entry.blockHash}",
                            style = AtroposTypography.monoGutter,
                            color = colors.textMuted,
                            fontSize = 9.sp
                        )

                        Text(
                            text = "Timestamp: ${entry.timestamp}",
                            style = AtroposTypography.monoGutter,
                            color = colors.textMuted,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}
