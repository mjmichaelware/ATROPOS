/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.platform

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import atropos.core.agent.AgentService
import atropos.core.AtroposConfig
import com.atropos.android.app.ui.MobileAppState
import com.atropos.android.app.ui.MobileAppIntent

/**
 * ATOM-APK-014: In-Process Android AAR & ART Engine Binding.
 *
 * Core engine runs inside ART Dalvik memory space.
 * Jetpack Compose UI binds directly to StateFlow with 0 loopback HTTP network calls on port 4317.
 * Mathematical Invariant: NetworkLatency(IntentDispatch) = 0 ms.
 */
class DirectInProcessEngineBridge(
    val config: AtroposConfig,
    val agentService: AgentService
) {
    private val _state = MutableStateFlow(MobileAppState(isOnline = true))
    val state: StateFlow<MobileAppState> = _state

    fun dispatchIntent(intent: MobileAppIntent) {
        if (intent is MobileAppIntent.MessageQueued) {
            agentService.submitGoal(intent.text)
        }
    }
}
