/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * File Tree Sheet (F-AND-012).
 *
 * Read-only project file browser using `/v1/workspace/tree` and
 * `/v1/workspace/file`. Opens as a secondary sheet alongside the
 * tools/timeline sheet.
 *
 * Read-only: no writes, no edits, no deletions. The engine serves
 * the project tree; this surface only renders it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileTreeSheet(
    isOpen: Boolean,
    onClose: () -> Unit,
    onFileSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!isOpen) return

    val sheetState = rememberModalBottomSheetState()
    val treeState = remember { mutableStateOf<FileTreeState>(FileTreeState.Loaded(emptyList())) }

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

            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Project Files", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Read-only",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Tree content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                when (val state = treeState.value) {
                    is FileTreeState.Loading -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Loading project tree…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    is FileTreeState.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                "Failed to load tree",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                state.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                state.remedy,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    is FileTreeState.Loaded -> {
                        FileTreeView(
                            nodes = state.tree,
                            onFileClick = onFileSelected,
                        )
                    }
                }
            }
        }
    }
}

sealed class FileTreeState {
    data object Loading : FileTreeState()
    data class Error(val detail: String, val remedy: String) : FileTreeState()
    data class Loaded(val tree: List<FileTreeNode>) : FileTreeState()
}

data class FileTreeNode(
    val name: String,
    val path: String,
    val type: String, // "file" | "directory"
    val children: List<FileTreeNode>? = null,
)

@Composable
private fun FileTreeView(
    nodes: List<FileTreeNode>,
    onFileClick: (String) -> Unit,
) {
    if (nodes.isEmpty()) {
        Text(
            text = "No files available",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(nodes) { node ->
                FileTreeNodeView(node = node, depth = 0, onClick = onFileClick)
            }
        }
    }
}

@Composable
private fun FileTreeNodeView(
    node: FileTreeNode,
    depth: Int,
    onClick: (String) -> Unit,
) {
    val expanded = remember { mutableStateOf(node.type == "directory") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 12.dp)
                .padding(start = (depth * 16).dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable {
                    if (node.type == "directory") {
                        expanded.value = !expanded.value
                    } else {
                        onClick(node.path)
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (node.type == "directory") {
                    if (expanded.value) Icons.Default.FolderOpen else Icons.Default.Folder
                } else Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                overflow = TextOverflow.Ellipsis,
            )
            if (node.type == "directory") {
                Icon(
                    imageVector = if (expanded.value) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (expanded.value && node.type == "directory") {
            node.children?.forEach { child ->
                FileTreeNodeView(node = child, depth = depth + 1, onClick = onClick)
            }
        }
    }
}