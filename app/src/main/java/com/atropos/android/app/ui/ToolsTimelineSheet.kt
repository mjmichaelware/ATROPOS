/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.icons.Icons
import androidx.compose.material3.icons.filled.Build
import androidx.compose.material3.icons.filled.Checklist
import androidx.compose.material3.icons.filled.History
import androidx.compose.material3.icons.filled.Info
import androidx.compose.material3.icons.filled.MoreVert
import androidx.compose.material3.icons.filled.PlayArrow
import androidx.compose.material3.icons.filled.Schedule
import androidx.compose.material3.icons.filled.Speed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import com.atropos.android.app.bridge.MobileCheckpoint
import com.atropos.android.app.bridge.MobileSelfHostRun
import com.atropos.android.app.bridge.MobileThinking
import kotlinx.coroutines.delay

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
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ToolsTimelineSheet(
    checkpoint: com.atropos.android.app.bridge.MobileCheckpoint? = null,
    selfHostRun: com.atropos.android.app.bridge.MobileSelfHostRun? = null,
    thinking: com.atropos.android.app.bridge.MobileThinking? = null,
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val sheetState = rememberModalBottomSheetState(
        initialValue = if (isOpen) ModalBottomSheetValue.Expanded else ModalBottomSheetValue.Hidden,
        confirmStateChange = { it != ModalBottomSheetValue.HalfExpanded },
    )
    val scope = rememberCoroutineScope()

    // Sync sheet state with isOpen
    androidx.compose.runtime.LaunchedEffect(isOpen) {
        if (isOpen) {
            scope.launch { sheetState.expand() }
        } else {
            scope.launch { sheetState.hide() }
        }
    }

    // Handle sheet dismissal
    val scope = rememberCoroutineScope()
    val sheetContent = @OptIn(ExperimentalMaterial3Api::class) @Composable {
        ModalBottomSheet(
            onDismissRequest = onClose,
            sheetState = sheetState,
            sheetShape = androidx.compose.material3.MaterialTheme.shapes.largeTop.copy(
                topLeft = androidx.compose.foundation.shape.CornerSize(16.dp),
                topRight = androidx.compose.foundation.shape.CornerSize(16.dp),
            ),
            sheetContent = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Drag handle
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .padding(top = 8.dp)
                            .background(Color.Gray.copy(alpha = 0.3f))
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .width(48.dp)
                                .height(4.dp)
                                .align(Alignment.Center)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                        )
                    }
                }

                // Tools section
                ToolsSection(onBuildRequested, onAdvanceBuild, onDismissBuild)

                // Timeline section
                TimelineSection()

                // File Tree section (F-AND-012)
                FileTreeSection()

                // Thinking section
                ThinkingSection()
            }
        },
    )

    if (isOpen) sheetContent
}

@Composable
private fun ToolsSection(
    onBuildRequested: (() -> Unit)? = null,
    onAdvanceBuild: (() -> Unit)? = null,
    onDismissBuild: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
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
                Text("Build & Run", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))

            // Primary build actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ToolButton(
                    icon = androidx.compose.material3.icons.filled.PlayArrow,
                    label = "Build",
                    onClick = onBuildRequested,
                    primary = true,
                )
                ToolButton(
                    icon = androidx.compose.material3.icons.filled.Speed,
                    label = "Advance",
                    onClick = onAdvanceBuild,
                )
                ToolButton(
                    icon = androidx.compose.material3.icons.filled.Checklist,
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
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Timeline", style = MaterialTheme.typography.titleMedium)
                Text("Execution history", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))

            // Timeline items (placeholder - would connect to engine)
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
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Project Files", style = MaterialTheme.typography.titleMedium)
                Text("Read-only", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))

            // File tree (placeholder - would connect to /v1/workspace/tree)
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
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Thinking", style = MaterialTheme.typography.titleMedium)
                Text("L1-L3 disclosure", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))

            Column {
                ThinkingLevelItem(1, "Analyzing requirements and constraints", true)
                ThinkingLevelItem(2, "Planning DAG structure and dependencies", false)
                ThinkingLevelItem(3, "Generating implementation plan", false)
            }
        }
    }
}

@Composable
private fun ToolButton(
    icon: androidx.compose.material3.icons.filled.Icon,
    label: String,
    onClick: (() -> Unit)?,
    primary: Boolean = false,
) {
    androidx.compose.material3.Button(
        onClick = onClick ?? {},
        modifier = Modifier.weight(1f).height(44.dp),
        enabled = onClick != null,
        colors = if (primary) androidx.compose.material3.ButtonDefaults.filledButtonColors(
            containerColor = MaterialTheme.colorScheme.primary
        ) else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = null)
        androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.foundation.layout.Modifier.height(4.dp))
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
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.material3.Chip(
            onClick = {},
            label = { Text(status) },
            modifier = Modifier.align(Alignment.CenterVertically),
            colors = when (status) {
                "completed" -> androidx.compose.material3.ChipDefaults.chipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                "running" -> androidx.compose.material3.ChipDefaults.chipColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                "pending" -> androidx.compose.material3.ChipDefaults.chipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                else -> androidx.compose.material3.ChipDefaults.chipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            }
        )
    }
}

@Composable
private fun FileTreeItem(name: String, isDirectory: Boolean, depth: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 16.dp)
            .padding(start = (16 + depth * 24).dp),
    ) {
        androidx.compose.material3.Icon(
            imageVector = if (isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(12.dp))
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable
private fun ThinkingLevelItem(level: Int, description: String, isActive: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("L$level", style = MaterialTheme.typography.labelLarge, color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isActive) {
            androidx.compose.material3.Chip(
                onClick = {},
                label = { Text("Active") },
                modifier = Modifier.align(Alignment.CenterVertically),
                colors = androidx.compose.material3.ChipDefaults.chipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    }
}