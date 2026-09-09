/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.icons.Icons
import androidx.compose.material3.icons.filled.ContentCopy
import androidx.compose.material3.icons.filled.Share
import androidx.compose.material3.icons.filled.Download
import androidx.compose.material3.icons.filed.Upload
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
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * Copy/Share + Offline Durable Resume (F-AND-010).
 *
 * Provides:
 * - Copy to clipboard (text, code blocks, responses)
 * - Share via system share sheet
 * - Offline durable resume: messages composed offline are persisted
 *   to Room and sent on reconnect
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

    // Toast state
    var showCopyToast by remember { mutableStateOf(false) }
    var showShareToast by remember { mutableStateOf(false) }

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
                        contentDescription = "Copy to clipboard",
                        modifier = Modifier.size(44.dp),
                    ) { Icon(Icons.Default.ContentCopy, contentDescription = null) }
                    IconButton(
                        onClick = { shareViaIntent(text); shared = true },
                        contentDescription = "Share via system sheet",
                        modifier = Modifier.size(44.dp),
                    ) { Icon(Icons.Default.Share, contentDescription = null) }
                    IconButton(
                        onClick = downloadAsFile(text),
                        contentDescription = "Download as file",
                        modifier = Modifier.size(44.dp),
                    ) { Icon(Icons.Default.Download, contentDescription = null) }
                }
            }
        }
    }

    // Toasts
    if (copied) {
        Snackbar(
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            action = { copied = false },
            visual = { Text("Copied to clipboard", style = MaterialTheme.typography.bodySmall) },
            dismissAction = { copied = false },
            duration = SnackbarDuration.Short,
        )
    }
    if (shared) {
        Snackbar(
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            action = { shared = false },
            visual = { Text("Shared", style = MaterialTheme.typography.bodySmall) },
            dismissAction = { shared = false },
            duration = SnackbarDuration.Short,
        )
    }
}

/**
 * Offline Durable Resume - Room-backed message queue.
 *
 * Messages composed while offline are persisted to Room and
 * automatically sent when connectivity is restored.
 */
class OfflineQueueRepository(
    private val dao: OfflineQueueDao,
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

@Entity(tableName = "offline_messages")
data class OfflineMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "pending", // pending, sent, failed
    val error: String? = null,
    val retryCount: Int = 0,
)

@Dao
interface OfflineQueueDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: OfflineMessage)

    @Query("SELECT * FROM offline_messages WHERE status = 'pending' ORDER BY createdAt ASC")
    suspend fun getPending(): List<OfflineMessage>

    @Query("UPDATE offline_messages SET status = 'sent' WHERE id = :id")
    suspend fun markSent(id: Long)

    @Query("UPDATE offline_messages SET status = 'failed', error = :error WHERE id = :id")
    suspend fun markFailed(id: Long, error: String)
}

@Database(entities = [OfflineMessage::class], version = 1)
abstract class OfflineQueueDatabase : RoomDatabase() {
    abstract fun offlineQueueDao(): OfflineQueueDao
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