/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * F-AND-001: Settings screen.
 *
 * Shows configuration options, provider status, and local-only mode.
 */
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    androidx.compose.material3.Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Settings",
            fontSize = 24.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )

        androidx.compose.material3.Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Engine",
                    style = MaterialTheme.typography.titleMedium
                )
                androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.foundation.layout.Modifier.padding(top = 8.dp))
                Text(
                    text = "Bridge Port: ${System.getenv("ATROPOS_BRIDGE_PORT") ?: "4317"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Local-only: ${System.getenv("ATROPOS_LOCAL_ONLY") ?: "false"}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        androidx.compose.material3.Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Providers",
                    style = MaterialTheme.typography.titleMedium
                )
                androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.foundation.layout.Modifier.padding(top = 8.dp))
                Text(
                    text = "Configure API keys in Termux environment variables",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        androidx.compose.material3.Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Local-only Mode",
                    style = MaterialTheme.typography.titleMedium
                )
                androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.foundation.layout.Modifier.padding(top = 8.dp))
                Text(
                    text = "Disables all network research planes. Local providers still work.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Back button
        androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.foundation.layout.Modifier.padding(top = 16.dp))
        Button(
            onClick = onNavigateBack,
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            shape = androidx.compose.material3.RoundedCornerShape(28.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = "Back",
                fontSize = 16.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}