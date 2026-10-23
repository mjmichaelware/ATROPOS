/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atropos.android.app.theme.AtroposTypography
import com.atropos.android.app.theme.LocalCyberHudColors
import com.atropos.android.app.theme.cyberGlass

enum class VsCodeTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    EXPLORER("Explorer", Icons.Default.Folder),
    EDITOR("Editor", Icons.Default.Code),
    DIFF("Diff Viewer", Icons.Default.Difference),
    DIAGNOSTICS("Problems", Icons.Default.BugReport)
}

/**
 * Mobile VS Code Main Screen.
 * Unites the File Tree, Code Editor, Diff Viewer, and Diagnostics into a cohesive IDE.
 */
@Composable
fun MobileVsCodeScreen(
    currentFile: String,
    fileContent: String,
    originalContent: String,
    fileTree: List<VirtualFileEntry>,
    onOpenFile: (VirtualFileEntry) -> Unit,
    onSaveFile: (String, String) -> Unit,
    onRefreshTree: () -> Unit,
    diagnosticsList: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    val colors = LocalCyberHudColors.current
    var currentTab by remember { mutableStateOf(if (currentFile.isNotBlank()) VsCodeTab.EDITOR else VsCodeTab.EXPLORER) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.voidAbyss)
    ) {
        // VS Code Tab Bar
        ScrollableTabRow(
            selectedTabIndex = currentTab.ordinal,
            containerColor = colors.panelSurface,
            contentColor = colors.accentCyan,
            edgePadding = 8.dp,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[currentTab.ordinal]),
                    color = colors.accentCyan,
                    height = 2.dp
                )
            },
            divider = {}
        ) {
            VsCodeTab.values().forEach { tab ->
                val selected = currentTab == tab
                Tab(
                    selected = selected,
                    onClick = { currentTab = tab },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(16.dp),
                                tint = if (selected) colors.accentCyan else colors.textMuted
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tab.title,
                                style = AtroposTypography.monoToken,
                                color = if (selected) colors.textPrimary else colors.textSecondary
                            )
                            if (tab == VsCodeTab.DIAGNOSTICS && diagnosticsList.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .background(colors.accentRuby.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "${diagnosticsList.size}",
                                        style = AtroposTypography.badgeLabel,
                                        color = colors.accentRuby
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }

        // Active Tab Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (currentTab) {
                VsCodeTab.EXPLORER -> {
                    FileTreeExplorer(
                        rootEntries = fileTree,
                        selectedPath = currentFile,
                        onSelectFile = { entry ->
                            onOpenFile(entry)
                            currentTab = VsCodeTab.EDITOR
                        },
                        onRefresh = onRefreshTree
                    )
                }
                VsCodeTab.EDITOR -> {
                    MobileCodeEditor(
                        filePath = currentFile,
                        initialContent = fileContent,
                        onSaveContent = { newContent ->
                            onSaveFile(currentFile, newContent)
                        },
                        onViewDiff = { _, _ ->
                            currentTab = VsCodeTab.DIFF
                        },
                        onRunDiagnostics = {
                            currentTab = VsCodeTab.DIAGNOSTICS
                        }
                    )
                }
                VsCodeTab.DIFF -> {
                    CodeDiffViewer(
                        filePath = currentFile,
                        originalText = originalContent,
                        modifiedText = fileContent,
                        onClose = { currentTab = VsCodeTab.EDITOR }
                    )
                }
                VsCodeTab.DIAGNOSTICS -> {
                    DiagnosticsPanel(
                        diagnostics = diagnosticsList,
                        onClear = {},
                        onOpenFile = {
                            currentTab = VsCodeTab.EDITOR
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsPanel(
    diagnostics: List<String>,
    onClear: () -> Unit,
    onOpenFile: () -> Unit
) {
    val colors = LocalCyberHudColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.voidAbyss)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PROBLEMS & AST DIAGNOSTICS",
                style = AtroposTypography.badgeLabel,
                color = colors.accentViolet
            )

            Text(
                text = "${diagnostics.size} issues detected",
                style = AtroposTypography.monoGutter,
                color = if (diagnostics.isEmpty()) colors.accentEmerald else colors.accentRuby
            )
        }

        if (diagnostics.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .cyberGlass(backgroundColor = colors.panelSurface, borderColor = colors.borderDim),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "✓ Zero Compiler Diagnostics",
                        style = AtroposTypography.hudSubhead,
                        color = colors.accentEmerald
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "All AST symbol types and import bindings resolve cleanly.",
                        style = AtroposTypography.hudCaption,
                        color = colors.textMuted
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                diagnostics.forEach { diag ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenFile() }
                            .cyberGlass(backgroundColor = colors.cardSurface, borderColor = colors.borderDim),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "✕",
                                style = AtroposTypography.monoToken,
                                color = colors.accentRuby,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = diag,
                                style = AtroposTypography.monoCode,
                                color = colors.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
