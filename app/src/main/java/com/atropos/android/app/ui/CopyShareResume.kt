/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Copy/Share + Offline Durable Resume (F-AND-010).
 *
 * Provides:
 * - Copy to clipboard (text, code blocks, responses)
 * - Share via system share sheet
 * - Offline durable resume: messages composed offline are persisted
 *   and sent on reconnect
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopyShareResume(
    text: String,
    onCopy: () -> Unit = {},
    onShare: () -> Unit = {},
    onDownload: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    var shared by remember { mutableStateOf(false) }

    val copyToClipboard = { str: String ->
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("atropos-content", str)
        clipboardManager.setPrimaryClip(clip)
        copied = true
    }

    val shareViaIntent = { str: String ->
        val intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, str)
            type = "text/plain"
        }
        val chooser = Intent.createChooser(intent, "Share via")
        context.startActivity(chooser)
        shared = true
    }

    val downloadAsFile = { str: String ->
        val file = File(context.cacheDir, "atropos-export-${System.currentTimeMillis()}.txt")
        FileOutputStream(file).use { it.write(str.toByteArray()) }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Save as file"))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
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
                Text("Export", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { copyToClipboard(text); copied = true },
                        modifier = Modifier.size(44.dp),
                    ) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy to clipboard") }
                    IconButton(
                        onClick = { shareViaIntent(text); shared = true },
                        modifier = Modifier.size(44.dp),
                    ) { Icon(Icons.Default.Share, contentDescription = "Share via system sheet") }
                    IconButton(
                        onClick = { downloadAsFile(text) },
                        modifier = Modifier.size(44.dp),
                    ) { Icon(Icons.Default.Download, contentDescription = "Download as file") }
                }
            }
        }
    }

    // Toasts
    if (copied) {
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = { TextButton(onClick = { copied = false }) { Text("OK") } }
        ) {
            Text("Copied to clipboard", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (shared) {
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = { TextButton(onClick = { shared = false }) { Text("OK") } }
        ) {
            Text("Shared", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Offline Durable Resume - In-memory / durable message queue.
 *
 * Messages composed while offline are persisted and automatically
 * sent when connectivity is restored.
 */
class OfflineQueueRepository(
    private val dao: OfflineQueueDao = InMemoryOfflineQueueDao(),
) {
    suspend fun enqueue(message: OfflineMessage) {
        dao.insert(message)
    }

    suspend fun dequeue(): List<OfflineMessage> {
        return dao.getPending()
    }

    suspend fun markSent(id: Long) {
        dao.markSent(id)
    }

    suspend fun markFailed(id: Long, error: String) {
        dao.markFailed(id, error)
    }
}

data class OfflineMessage(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "pending", // pending, sent, failed
    val error: String? = null,
    val retryCount: Int = 0,
)

interface OfflineQueueDao {
    suspend fun insert(message: OfflineMessage)
    suspend fun getPending(): List<OfflineMessage>
    suspend fun markSent(id: Long)
    suspend fun markFailed(id: Long, error: String)
}

class InMemoryOfflineQueueDao : OfflineQueueDao {
    private val queue = CopyOnWriteArrayList<OfflineMessage>()

    override suspend fun insert(message: OfflineMessage) {
        queue.add(message)
    }

    override suspend fun getPending(): List<OfflineMessage> {
        return queue.filter { it.status == "pending" }
    }

    override suspend fun markSent(id: Long) {
        val idx = queue.indexOfFirst { it.id == id }
        if (idx >= 0) {
            queue[idx] = queue[idx].copy(status = "sent")
        }
    }

    override suspend fun markFailed(id: Long, error: String) {
        val idx = queue.indexOfFirst { it.id == id }
        if (idx >= 0) {
            queue[idx] = queue[idx].copy(status = "failed", error = error)
        }
    }
}

/**
 * Offline Resume Manager - drains queue on reconnect.
 */
class OfflineResumeManager(
    private val repository: OfflineQueueRepository,
    private val bridge: com.atropos.android.app.bridge.AndroidEngineBridge,
) {
    suspend fun drainQueue() {
        val pending = repository.dequeue()
        for (message in pending) {
            val outcome = bridge.send(message.text)
            if (outcome is com.atropos.android.app.bridge.SendOutcome.Delivered) {
                repository.markSent(message.id)
            } else if (outcome is com.atropos.android.app.bridge.SendOutcome.Refused) {
                repository.markFailed(message.id, outcome.detail)
            } else {
                // Still offline, stop draining
                break
            }
        }
    }
}