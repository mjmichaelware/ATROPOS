/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileCode
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.atropos.android.app.theme.AtroposTypography
import com.atropos.android.app.theme.LocalCyberHudColors
import com.atropos.android.app.theme.cyberGlass

/**
 * Mobile Fuzzy File Finder & Quick Open Modal.
 *
 * Provides instant workspace file resolution with fuzzy matching,
 * recent file history, and file path breadcrumbs.
 */
@Composable
fun FileSearchModal(
    isOpen: Boolean,
    onClose: () -> Unit,
    allFiles: List<VirtualFileEntry>,
    recentFiles: List<String> = emptyList(),
    onSelectFile: (VirtualFileEntry) -> Unit
) {
    if (!isOpen) return

    val colors = LocalCyberHudColors.current
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Flatten all files recursively
    val flattenedFiles = remember(allFiles) {
        fun flatten(entry: VirtualFileEntry): List<VirtualFileEntry> {
            val list = mutableListOf<VirtualFileEntry>()
            if (!entry.isDirectory) list.add(entry)
            entry.children.forEach { list.addAll(flatten(it)) }
            return list
        }
        allFiles.flatMap { flatten(it) }
    }

    val filteredFiles = remember(searchQuery, flattenedFiles) {
        if (searchQuery.isBlank()) {
            flattenedFiles.take(15)
        } else {
            val q = searchQuery.lowercase()
            flattenedFiles.filter { it.path.lowercase().contains(q) || it.name.lowercase().contains(q) }
        }
    }

    Dialog(onDismissRequest = onClose) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .cyberGlass(backgroundColor = colors.panelSurface, borderColor = colors.accentCyan),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = colors.accentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "QUICK OPEN FILE",
                            style = AtroposTypography.monoToken,
                            color = colors.accentCyan
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = colors.textMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search file path or name...",
                            style = AtroposTypography.monoGutter,
                            color = colors.textMuted
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accentCyan,
                        unfocusedBorderColor = colors.borderDim,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        cursorColor = colors.accentCyan
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Results List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (filteredFiles.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No matching workspace files found",
                                    style = AtroposTypography.monoGutter,
                                    color = colors.textMuted
                                )
                            }
                        }
                    } else {
                        items(filteredFiles) { entry ->
                            val isRecent = recentFiles.contains(entry.path)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(colors.cardSurface, RoundedCornerShape(8.dp))
                                    .clickable {
                                        onSelectFile(entry)
                                        onClose()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (isRecent) Icons.Default.History else Icons.Default.FileCode,
                                        contentDescription = null,
                                        tint = if (isRecent) colors.accentAmber else colors.accentCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column {
                                        Text(
                                            text = entry.name,
                                            style = AtroposTypography.monoToken,
                                            color = colors.textPrimary,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = entry.path,
                                            style = AtroposTypography.monoGutter,
                                            color = colors.textMuted,
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                Text(
                                    text = "${entry.sizeBytes} B",
                                    style = AtroposTypography.monoGutter,
                                    color = colors.textMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
