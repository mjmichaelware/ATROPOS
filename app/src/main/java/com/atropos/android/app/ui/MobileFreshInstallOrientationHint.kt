/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ATOM 16.3: Mobile Fresh-Install Orientation and Guidance Hints.
 *
 * Provides explanatory onboarding guidance on mobile viewports when transcripts or work lists are empty.
 * Guides the user to import a project or configure a bridge endpoint without presenting blank screens.
 *
 * ## Research: Predictive Onboarding Suggestions Based on Device Capabilities
 * Researches runtime capability probing (Android `ConnectivityManager.NetworkCapabilities`,
 * scoped storage URI grant status, and CPU thermal status) to predictively render actionable
 * onboarding steps matching the operator's exact runtime environment.
 */
@Composable
fun MobileFreshInstallOrientationHint(
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (isOnline) Color(0xFF064E3B) else Color(0xFF451A03))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (isOnline) "● ENGINE CONNECTED" else "○ ENGINE OFFLINE",
                color = if (isOnline) Color(0xFF34D399) else Color(0xFFFBBF24),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = conversationStatus(isOnline),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (!isOnline) {
            Text(
                text = "Launch the ATROPOS core engine in Termux or host terminal:\n" +
                        "ATROPOS_BRIDGE_PORT=8787 java -jar ATROPOS.jar",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(top = 10.dp)
            )
        } else {
            Text(
                text = "Type '/' to inspect available commands or describe a coding task to begin.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
