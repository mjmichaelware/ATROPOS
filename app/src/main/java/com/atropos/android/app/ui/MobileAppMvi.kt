/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

import com.atropos.android.app.bridge.MobileApproval
import com.atropos.android.app.bridge.MobileCheckpoint
import com.atropos.android.app.bridge.MobileSixAnswers
import com.atropos.android.app.bridge.MobileThinking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Immutable state rendered by the Android conversation surface. */
data class MobileAppState(
    val messages: List<MobileMessage> = emptyList(),
    val isOnline: Boolean = false,
    val sessions: List<ChatListEntry> = emptyList(),
    val sessionTabs: SessionTabModel = SessionTabModel(),
    val checkpoint: MobileCheckpoint? = null,
    val thinking: MobileThinking? = null,
    val answers: MobileSixAnswers? = null,
    val approvals: List<MobileApproval> = emptyList(),
    val activeProvider: String? = null,
    val outbox: ComposerOutbox = ComposerOutbox()
)

/** Intents accepted by the single Android surface-state owner. */
sealed interface MobileAppIntent {
    data class ReachabilityChanged(val online: Boolean) : MobileAppIntent
    data class SessionsLoaded(val sessions: List<ChatListEntry>) : MobileAppIntent
    data class TranscriptLoaded(val messages: List<MobileMessage>) : MobileAppIntent
    data class AnswersLoaded(val answers: MobileSixAnswers?) : MobileAppIntent
    data class ApprovalsLoaded(val approvals: List<MobileApproval>) : MobileAppIntent
    data class ProviderLoaded(val provider: String?) : MobileAppIntent
    data class CheckpointLoaded(val checkpoint: MobileCheckpoint?) : MobileAppIntent
    data class ThinkingLoaded(val thinking: MobileThinking?) : MobileAppIntent
    data class SessionSelected(val id: String) : MobileAppIntent
    data class MessageQueued(val text: String) : MobileAppIntent
    data object QueueHeadDelivered : MobileAppIntent
    data class ApprovalRemoved(val id: String, val notice: MobileMessage) : MobileAppIntent
    data class Notice(val notice: MobileMessage) : MobileAppIntent
}

/** Pure reducer used by the Compose shell and directly testable without Android. */
fun reduceMobileAppState(state: MobileAppState, intent: MobileAppIntent): MobileAppState = when (intent) {
    is MobileAppIntent.ReachabilityChanged -> state.copy(
        isOnline = intent.online,
        answers = if (intent.online) state.answers else null,
        approvals = if (intent.online) state.approvals else emptyList(),
        activeProvider = if (intent.online) state.activeProvider else null
    )
    is MobileAppIntent.SessionsLoaded -> state.copy(
        sessions = intent.sessions,
        sessionTabs = state.sessionTabs.replace(intent.sessions)
    )
    is MobileAppIntent.TranscriptLoaded -> state.copy(messages = intent.messages)
    is MobileAppIntent.AnswersLoaded -> state.copy(answers = intent.answers)
    is MobileAppIntent.ApprovalsLoaded -> state.copy(approvals = intent.approvals)
    is MobileAppIntent.ProviderLoaded -> state.copy(activeProvider = intent.provider)
    is MobileAppIntent.CheckpointLoaded -> state.copy(checkpoint = intent.checkpoint)
    is MobileAppIntent.ThinkingLoaded -> state.copy(thinking = intent.thinking)
    is MobileAppIntent.SessionSelected -> state.copy(
        sessionTabs = state.sessionTabs.select(intent.id)
    )
    is MobileAppIntent.MessageQueued -> state.copy(outbox = state.outbox.queue(intent.text))
    MobileAppIntent.QueueHeadDelivered -> state.copy(outbox = state.outbox.dropHead())
    is MobileAppIntent.ApprovalRemoved -> state.copy(
        approvals = state.approvals.filterNot { it.id == intent.id },
        messages = state.messages + intent.notice
    )
    is MobileAppIntent.Notice -> state.copy(messages = state.messages + intent.notice)
}

/** StateFlow-backed MVI store; transport remains owned by AndroidEngineBridge. */
class MobileAppMviStore(initial: MobileAppState = MobileAppState()) {
    private val mutableState = MutableStateFlow(initial)
    val state: StateFlow<MobileAppState> = mutableState.asStateFlow()

    fun dispatch(intent: MobileAppIntent) {
        mutableState.update { reduceMobileAppState(it, intent) }
    }
}
