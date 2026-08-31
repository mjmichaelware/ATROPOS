/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * F-AND-001: Offline screen shown when engine is unreachable.
 *
 * Provides a clear path to go back online.
 */
@Composable
fun OfflineScreen(
    onGoOnline: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Engine Offline",
            fontSize = 24.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
        androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.foundation.layout.Modifier.padding(top = 16.dp))
        Text(
            text = "Start the engine in Termux:\nATROPOS_BRIDGE_PORT=8787 java -jar ATROPOS.jar",
            textAlign = androidx.compose.ui.text.TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.foundation.layout.Modifier.padding(top = 24.dp))
        Button(
            onClick = onGoOnline,
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            shape = androidx.compose.material3.RoundedCornerShape(28.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = "Go to Conversations",
                fontSize = 16.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}