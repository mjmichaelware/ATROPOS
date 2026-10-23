/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atropos.android.app.theme.AtroposTypography
import com.atropos.android.app.theme.LocalCyberHudColors
import com.atropos.android.app.theme.cyberGlass

data class VirtualFileEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0,
    val children: List<VirtualFileEntry> = emptyList()
)

/**
 * Sovereign Mobile Interactive File Tree Explorer.
 * Supports directory folding, search filtering, file metadata badges, and click-to-edit.
 */
@Composable
fun FileTreeExplorer(
    rootEntries: List<VirtualFileEntry>,
    selectedPath: String?,
    onSelectFile: (VirtualFileEntry) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalCyberHudColors.current
    var searchQuery by remember { mutableStateOf("") }
    val expandedFolders = remember { mutableStateMapOf<String, Boolean>() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.voidAbyss)
            .padding(12.dp)
    ) {
        // Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "WORKSPACE EXPLORER",
                style = AtroposTypography.badgeLabel,
                color = colors.accentCyan,
                letterSpacing = 1.sp
            )

            Row {
                IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Workspace",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Search Filter
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            placeholder = {
                Text(
                    text = "Filter files...",
                    style = AtroposTypography.hudCaption,
                    color = colors.textMuted
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = colors.accentCyan,
                    modifier = Modifier.size(16.dp)
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accentCyan,
                unfocusedBorderColor = colors.borderDim,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary
            )
        )

        // File List
        val flattenedList = remember(rootEntries, searchQuery, expandedFolders.toMap()) {
            flattenFileTree(rootEntries, searchQuery, expandedFolders, depth = 0)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .cyberGlass(backgroundColor = colors.panelSurface, borderColor = colors.borderDim)
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            if (flattenedList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No files match query" else "Workspace tree empty",
                            style = AtroposTypography.hudCaption,
                            color = colors.textMuted
                        )
                    }
                }
            } else {
                items(flattenedList) { item ->
                    FileTreeRow(
                        item = item,
                        isSelected = item.entry.path == selectedPath,
                        isExpanded = expandedFolders[item.entry.path] == true,
                        onToggleExpand = {
                            val current = expandedFolders[item.entry.path] ?: false
                            expandedFolders[item.entry.path] = !current
                        },
                        onSelectFile = { onSelectFile(item.entry) }
                    )
                }
            }
        }
    }
}

data class FlatTreeItem(
    val entry: VirtualFileEntry,
    val depth: Int
)

private fun flattenFileTree(
    entries: List<VirtualFileEntry>,
    query: String,
    expanded: Map<String, Boolean>,
    depth: Int
): List<FlatTreeItem> {
    val result = mutableListOf<FlatTreeItem>()
    for (entry in entries) {
        if (query.isNotBlank()) {
            if (entry.name.contains(query, ignoreCase = true) || entry.path.contains(query, ignoreCase = true)) {
                result.add(FlatTreeItem(entry, depth))
            }
            if (entry.isDirectory) {
                result.addAll(flattenFileTree(entry.children, query, expanded, depth + 1))
            }
        } else {
            result.add(FlatTreeItem(entry, depth))
            if (entry.isDirectory && expanded[entry.path] == true) {
                result.addAll(flattenFileTree(entry.children, query, expanded, depth + 1))
            }
        }
    }
    return result
}

@Composable
private fun FileTreeRow(
    item: FlatTreeItem,
    isSelected: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSelectFile: () -> Unit
) {
    val colors = LocalCyberHudColors.current
    val entry = item.entry

    val rowBg = if (isSelected) {
        colors.accentCyan.copy(alpha = 0.15f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg, RoundedCornerShape(4.dp))
            .clickable {
                if (entry.isDirectory) {
                    onToggleExpand()
                } else {
                    onSelectFile()
                }
            }
            .padding(
                start = (item.depth * 16 + 6).dp,
                end = 8.dp,
                top = 5.dp,
                bottom = 5.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (entry.isDirectory) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                contentDescription = null,
                tint = colors.accentAmber,
                modifier = Modifier.size(16.dp)
            )
        } else {
            Spacer(modifier = Modifier.width(20.dp))
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = getFileIconColor(entry.name, colors),
                modifier = Modifier.size(15.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = entry.name,
            style = AtroposTypography.monoCode,
            color = if (isSelected) colors.accentCyan else colors.textPrimary,
            fontWeight = if (entry.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )

        if (!entry.isDirectory && entry.sizeBytes > 0) {
            Text(
                text = formatBytes(entry.sizeBytes),
                style = AtroposTypography.monoGutter,
                color = colors.textMuted
            )
        }
    }
}

private fun getFileIconColor(fileName: String, colors: com.atropos.android.app.theme.CyberHudColors): Color {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "kt", "kts" -> colors.accentViolet
        "tsx", "ts", "js" -> colors.accentCyan
        "json" -> colors.accentAmber
        "md", "txt" -> colors.textSecondary
        "gradle" -> colors.accentEmerald
        "sh", "bash" -> colors.accentRuby
        else -> colors.textMuted
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    return "%.1f MB".format(mb)
}
