/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ATOM 14.2: Explicit Enter Button Gate and Application Transition.
 *
 * Renders touch-confirmed entrance gate concluding the startup sequence
 * and transitioning fluidly into the main sovereign workstation shell.
 *
 * ## Research: Jetpack Compose SharedTransitionLayout APIs
 * Researches Android Compose `SharedTransitionLayout` and `sharedElement` transitions
 * to morph the glowing ENTER WORKSPACE button seamlessly into the primary command input
 * bar at the top of the workstation without jarring layout cuts.
 */
@Composable
fun WorkspaceEntranceGate(
    isEngineRunning: Boolean,
    onEnterConfirmed: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07100D))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "ATROPOS SOVEREIGN RUNTIME",
                color = Color(0xFF10B981),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isEngineRunning) "STATUS: ALL SYSTEMS ONLINE" else "STATUS: STANDALONE OFFLINE READY",
                color = if (isEngineRunning) Color(0xFF34D399) else Color(0xFF94A3B8),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onEnterConfirmed,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF065F46),
                    contentColor = Color(0xFF34D399)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .border(1.5.dp, Color(0xFF10B981), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "ENTER WORKSPACE ➔",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
