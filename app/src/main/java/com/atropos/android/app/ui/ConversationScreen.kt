/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atropos.android.app.bridge.MobileApproval
import com.atropos.android.app.bridge.MobileCheckpoint
import com.atropos.android.app.bridge.MobileSixAnswers
import com.atropos.android.app.bridge.MobileThinking

fun conversationStatus(isOnline: Boolean): String =
    if (isOnline) "Ask ATROPOS anything." else "Engine not reachable"

/**
 * HOE-D01: the app shell — conversation stream plus composer.
 *
 * The primary column is always visible and is the whole screen on a phone
 * (HOE-D03). Tools and timeline belong in a secondary sheet, not here, which
 * is why this file knows nothing about them.
 *
 * Stateless with respect to transport: it renders what it is given and reports
 * what was typed. Everything about reaching the engine lives behind
 * [onSendMessage], so this composable stays previewable and testable without a
 * running engine.
 */
@Composable
fun ConversationScreen(
    messages: List<MobileMessage>,
    isOnline: Boolean,
    onSendMessage: (String) -> Unit,
    sessions: List<ChatListEntry> = emptyList(),
    onSessionSelected: (String) -> Unit = {},
    checkpoint: MobileCheckpoint? = null,
    onCheckpointAction: (String) -> Unit = {},
    thinking: MobileThinking? = null,
    onThinkingDepthRequested: (Int) -> Unit = {},
    answers: MobileSixAnswers? = null,
    approvals: List<MobileApproval> = emptyList(),
    onApprovalDecided: (String, Boolean) -> Unit = { _, _ -> },
    activeProvider: String? = null,
    /** What is waiting to send, or null when nothing is. */
    queuedNotice: String? = null
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Follow the tail as turns arrive; nothing to do while empty.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MobileHeader(isOnline = isOnline, activeProvider = activeProvider)

        ChatListScreen(sessions = sessions, onSelected = onSessionSelected)
        CheckpointChip(checkpoint = checkpoint, onAction = onCheckpointAction)
        ThinkingSheet(thinking = thinking, onDepthRequested = onThinkingDepthRequested)

        // Approvals sit above the transcript and outside the scrolling area.
        // An action the engine has stopped on is the one thing that must not
        // scroll out of view while the operator reads back through the
        // conversation that produced it.
        approvals.forEach { approval ->
            MobileApprovalCard(
                approval = approval,
                onApprove = { id -> onApprovalDecided(id, true) },
                onReject = { id -> onApprovalDecided(id, false) }
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) {
                // An empty transcript is not an empty screen. The engine
                // can describe what it is doing whether or not anyone has
                // spoken to it yet, so the six answers fill the column that
                // used to hold one line of apology.
                if (answers != null) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        SixAnswersPanel(answers = answers)
                    }
                } else {
                    EmptyConversationHint(
                        isOnline = isOnline,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    // The answers ride at the head of the transcript once one
                    // exists, so scrolling back to the start of a conversation
                    // also reaches the state that conversation is about.
                    answers?.let { item(key = "six-answers") { SixAnswersPanel(answers = it) } }
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(message)
                    }
                }
            }
        }

        // Above the composer, not in the transcript: what is waiting to send
        // is a property of the input field, and a notice that scrolled away
        // with the conversation would stop answering "did my message go".
        queuedNotice?.let { notice ->
            Text(
                text = notice,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        ComposerScreen(
            value = input,
            onValueChange = { input = it },
            onSend = {
                val text = input.trim()
                if (text.isNotEmpty()) {
                    onSendMessage(text)
                    input = ""
                }
            },
            isOnline = isOnline
        )
    }
}

/**
 * The first thing a new operator sees. It says how to make the engine
 * reachable rather than only that it is not, because "offline" on its own
 * gives no next action.
 */
@Composable
private fun EmptyConversationHint(isOnline: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = conversationStatus(isOnline),
            style = MaterialTheme.typography.titleMedium
        )
        if (!isOnline) {
            Text(
                text = "Start it in Termux with the bridge port set:\n" +
                    "ATROPOS_BRIDGE_PORT=8787 java -jar ATROPOS.jar",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}
