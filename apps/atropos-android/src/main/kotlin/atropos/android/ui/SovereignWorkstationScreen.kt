/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class ApprovalItem(
    val id: String,
    val toolName: String,
    val reason: String,
    val riskLevel: String
)

@Composable
fun SovereignWorkstationScreen(
    projectId: String,
    onNavigateBack: () -> Unit
) {
    AtroposHudTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF07100D))
                .padding(16.dp)
        ) {
            Text(
                text = "ATROPOS SOVEREIGN WORKSTATION: $projectId",
                color = Color(0xFF10B981)
            )
            MobileSplitEditor(
                fileContent = "// Mobile Sovereign Workstation Editor",
                onContentChange = {}
            )
        }
    }
}

@Composable
fun MobileSplitEditor(
    fileContent: String,
    onContentChange: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp)
            .background(Color(0xFF0F172A))
            .padding(12.dp)
    ) {
        Text(text = fileContent, color = Color(0xFFE2E8F0))
    }
}

@Composable
fun HapticApprovalBanner(
    pendingApproval: ApprovalItem,
    onDecision: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF451A03))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "APPROVAL REQUIRED: ${pendingApproval.toolName}", color = Color(0xFFFBBF24))
            Text(text = pendingApproval.reason, color = Color(0xFFFDE68A))
        }
        Button(
            onClick = { onDecision(true) },
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text("Approve")
        }
    }
}
