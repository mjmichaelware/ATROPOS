/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class ChatListEntry(val id: String, val title: String, val updatedAt: String)

/** Selection stays explicit; an unknown session can never become active. */
fun selectedChatId(sessions: List<ChatListEntry>, requestedId: String): String? =
    sessions.firstOrNull { it.id == requestedId }?.id

/** Thin Compose projection of the bridge-owned session list. */
@Composable
fun ChatListScreen(
    sessions: List<ChatListEntry>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (sessions.isEmpty()) return
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(sessions, key = { it.id }) { session ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        selectedChatId(sessions, session.id)?.let(onSelected)
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(session.title, style = MaterialTheme.typography.titleSmall)
                Text(session.updatedAt, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
