/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.android.ui.components

/**
 * HOE-D01: App shell (chat list, conversation, composer, engine offline).
 * HOE-D09: One-hand density, HIG targets, offline project resume.
 * Pure mobile UI layer for Compose Multiplatform.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class MobileMessage(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long
)

@Composable
fun MobileConversationScreen(
    messages: List<MobileMessage> = emptyList(),
    isOnline: Boolean = false,
    onSendMessage: (String) -> Unit = {}
) {
    var inputText by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header with project name and status
        MobileHeader(isOnline)

        // Messages list (44pt tap targets for mobile HIG)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                messages.forEach { msg ->
                    MessageBubble(msg)
                }
            }
        }

        // Composer (44pt minimum for buttons)
        MobileComposer(
            value = inputText,
            onValueChange = { inputText = it },
            onSend = {
                if (inputText.isNotBlank()) {
                    onSendMessage(inputText)
                    inputText = ""
                }
            },
            isEnabled = isOnline
        )
    }
}

@Composable
private fun MobileHeader(isOnline: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "ATROPOS",
                style = MaterialTheme.typography.titleMedium
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val statusIcon = if (isOnline) Icons.Filled.Cloud else Icons.Filled.CloudOff
                Icon(
                    imageVector = statusIcon,
                    contentDescription = if (isOnline) "Online" else "Offline",
                    modifier = Modifier.size(20.dp),
                    tint = if (isOnline) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isOnline) "Online" else "Offline",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(message: MobileMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = if (message.isUser)
            Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .padding(4.dp),
            color = if (message.isUser)
                MaterialTheme.colorScheme.primary else
                MaterialTheme.colorScheme.tertiaryContainer,
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.isUser)
                    MaterialTheme.colorScheme.onPrimary else
                    MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun MobileComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isEnabled: Boolean = true
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp),
                placeholder = { Text("Message...") },
                maxLines = 3,
                enabled = isEnabled,
                singleLine = false
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                enabled = isEnabled && value.isNotBlank(),
                modifier = Modifier.size(44.dp)  // HIG minimum
            ) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = "Send"
                )
            }
        }
    }
}
