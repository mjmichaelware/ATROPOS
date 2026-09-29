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
import androidx.compose.material3.TextButton
import androidx.compose.material3.icons.Icons
import androidx.compose.material3.icons.filled.Folder
import androidx.compose.material3.icons.filled.FolderOpen
import androidx.compose.material3.icons.filled.InsertDriveFile
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
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.icons.Icons
import androidx.compose.material3.icons.filled.Folder
import androidx.compose.material3.icons.filled.FolderOpen
import androidx.compose.material3.icons.filled.InsertDriveFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FileTreeSheet(
    isOpen: Boolean,
    onClose: () -> Unit,
    onFileSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(
        initialValue = if (isOpen) ModalBottomSheetValue.Expanded else ModalBottomSheetValue.Hidden,
    )
    val scope = rememberCoroutineScope()

    // Sync sheet state
    androidx.compose.runtime.LaunchedEffect(isOpen) {
        if (isOpen) scope.launch { sheetState.expand() } else scope.launch { sheetState.hide() }
    }

    val treeState = remember { mutableStateOf<FileTreeState>(FileTreeState.Loading) }
    val scope = rememberCoroutineScope()

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SheetContent() {
        ModalBottomSheet(
            onDismissRequest = onClose,
            sheetState = sheetState,
            sheetShape = MaterialTheme.shapes.largeTop.copy(
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

                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Project Files", style = MaterialTheme.typography.titleMedium)
                        Text("Read-only", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // Tree content
                    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        when (val state = treeState.value) {
                            is FileTreeState.Loading -> {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    androidx.compose.material3.CircularProgressIndicator()
                                    Text("Loading project tree…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            is FileTreeState.Error -> {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text("Failed to load tree", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                                    Text(state.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(state.remedy, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            },
        )
    }

    if (isOpen) SheetContent()
}

sealed class FileTreeState {
    data class Loading : FileTreeState()
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(nodes) { node ->
            FileTreeNodeView(node = node, depth = 0, onClick = { path ->
                // Would call repository.readFile(path) and open in editor
            })
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

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 12.dp)
                .padding(start = (16 + depth * 20).dp)
                .fillMaxWidth()
                .background(
                    Color.Transparent,
                    androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                )
                .clickable {
                    if (node.type == "directory") {
                        expanded.value = !expanded.value
                    } else {
                        // Handle file click
                    }
                }
        ) {
            androidx.compose.material3.Icon(
                imageVector = if (node.type == "directory") {
                    if (expanded.value) Icons.Default.FolderOpen else Icons.Default.Folder
                } else Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.foundation.layout.Modifier.width(12.dp))
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (node.type == "directory") {
                androidx.compose.material3.Icon(
                    imageVector = if (expanded.value) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (expanded.value && node.type == "directory") {
            node.children?.let { children ->
                LazyColumn(
                    modifier = Modifier.padding(start = 16.dp).fillMaxWidth()
                ) {
                    items(children) { child ->
                        FileTreeNodeView(node = child, depth = depth + 1, onClick = {})
                    }
                }
            }
        }
    }
}