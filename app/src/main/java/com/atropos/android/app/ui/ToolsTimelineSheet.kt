/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.atropos.android.app.bridge.MobileCheckpoint
import com.atropos.android.app.bridge.MobileSelfHostRun
import com.atropos.android.app.bridge.MobileThinking

/**
 * Tools/Timeline Secondary Sheet (F-AND-007).
 *
 * A secondary bottom sheet that slides up to reveal:
 * - Tools: Build, Run, Debug, Test actions
 * - Timeline: Execution history with timestamps and outcomes
 * - File Tree: Read-only project file browser (F-AND-012)
 *
 * This sheet is separate from the primary conversation stream per
 * HOE-D01/HOE-D03: "Tools and timeline belong in a secondary sheet,
 * not here, which is why this file knows nothing about them."
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsTimelineSheet(
    checkpoint: MobileCheckpoint? = null,
    selfHostRun: MobileSelfHostRun? = null,
    thinking: MobileThinking? = null,
    onBuildRequested: (() -> Unit)? = null,
    onAdvanceBuild: (() -> Unit)? = null,
    onDismissBuild: (() -> Unit)? = null,
    onThinkingDepthRequested: ((Int) -> Unit)? = null,
    onCheckpointAction: ((String) -> Unit)? = null,
    onThinkingAction: ((Int) -> Unit)? = null,
    isOpen: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isOpen) return

    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Tools section
            ToolsSection(onBuildRequested, onAdvanceBuild, onDismissBuild)

            // Timeline section
            TimelineSection()

            // File Tree section (F-AND-012)
            FileTreeSection()

            // Thinking section
            ThinkingSection()
        }
    }
}

@Composable
private fun ToolsSection(
    onBuildRequested: (() -> Unit)? = null,
    onAdvanceBuild: (() -> Unit)? = null,
    onDismissBuild: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Tools", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Build & Run",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary build actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ToolButton(
                    icon = Icons.Default.PlayArrow,
                    label = "Build",
                    onClick = onBuildRequested,
                    primary = true,
                )
                ToolButton(
                    icon = Icons.Default.Speed,
                    label = "Advance",
                    onClick = onAdvanceBuild,
                )
                ToolButton(
                    icon = Icons.Default.Checklist,
                    label = "Test",
                    onClick = null,
                )
            }
        }
    }
}

@Composable
private fun TimelineSection() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Timeline", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Execution history",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Column(modifier = Modifier.padding(top = 8.dp)) {
                TimelineItem("Build started", "2m ago", "started")
                TimelineItem("Test phase", "1m ago", "running")
                TimelineItem("Verification", "30s ago", "completed")
                TimelineItem("Approval required", "now", "pending")
            }
        }
    }
}

@Composable
private fun FileTreeSection() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Project Files", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Read-only",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Column(modifier = Modifier.padding(top = 8.dp)) {
                FileTreeItem("src/", true, 0)
                FileTreeItem("src/main/", true, 1)
                FileTreeItem("src/main/kotlin/", true, 2)
                FileTreeItem("src/main/kotlin/App.kt", false, 3)
                FileTreeItem("src/test/", true, 1)
                FileTreeItem("build.gradle.kts", false, 0)
                FileTreeItem("README.md", false, 0)
            }
        }
    }
}

@Composable
private fun ThinkingSection() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Thinking", style = MaterialTheme.typography.titleMedium)
                Text(
                    "L1-L3 disclosure",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Column {
                ThinkingLevelItem(1, "Analyzing requirements and constraints", true)
                ThinkingLevelItem(2, "Planning DAG structure and dependencies", false)
                ThinkingLevelItem(3, "Generating implementation plan", false)
            }
        }
    }
}

@Composable
private fun RowScope.ToolButton(
    icon: ImageVector,
    label: String,
    onClick: (() -> Unit)?,
    primary: Boolean = false,
) {
    Button(
        onClick = { onClick?.invoke() },
        modifier = Modifier
            .weight(1f)
            .height(44.dp),
        enabled = onClick != null,
        colors = if (primary) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        } else {
            ButtonDefaults.outlinedButtonColors()
        }
    ) {
        Icon(icon, contentDescription = null)
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun TimelineItem(title: String, time: String, status: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = when (status) {
                "completed" -> MaterialTheme.colorScheme.primaryContainer
                "running" -> MaterialTheme.colorScheme.tertiaryContainer
                "pending" -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            },
            modifier = Modifier.padding(start = 8.dp)
        ) {
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun FileTreeItem(name: String, isDirectory: Boolean, depth: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 16.dp)
            .padding(start = (16 + depth * 24).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ThinkingLevelItem(level: Int, description: String, isActive: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "L$level",
                style = MaterialTheme.typography.labelLarge,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isActive) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = "Active",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}