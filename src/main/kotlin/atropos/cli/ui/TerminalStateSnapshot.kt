/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.session.QuotaSessionTracker

data class TerminalStateSnapshot(
    var reactive: Boolean = false,
    var provider: String = "unknown",
    var verboseExecution: Boolean = false,
    var mode: String = "ASK",
    var workspace: String = "",
    var tracker: QuotaSessionTracker = QuotaSessionTracker(),
    var activity: String? = null,
    var verificationState: String? = null,
    var activeScreen: String = "Dashboard",
    var activeTab: String = "tab 1",
    var openTabCount: Int = 1,
    var activePatchId: String? = null,
    /**
     * The open tabs, for the tab bar.
     *
     * [ViewportLayout] has always accepted a tab list and always defaulted it
     * to empty, and nothing ever passed one — so `SessionTabBar.render`
     * returned on its first line every frame and the bar occupied zero rows.
     * The tabs were real, `/tabs` switched between them, and the operator had
     * no way to see that any of it was happening.
     */
    var tabs: List<ViewportLayout.TabState> = emptyList()
)
