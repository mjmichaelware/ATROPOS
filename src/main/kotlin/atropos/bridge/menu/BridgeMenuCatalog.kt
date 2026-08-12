/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.menu

import atropos.bridge.projection.MenuAction

/** Compatibility facade; [HelpRegistry] owns the bridge-safe action set. */
object BridgeMenuCatalog {
    const val CONVERSATION = HelpRegistry.CONVERSATION
    const val WORK = HelpRegistry.WORK
    const val STATUS = HelpRegistry.STATUS
    const val GOVERNANCE = HelpRegistry.GOVERNANCE

    fun actions(): List<MenuAction> = HelpRegistry.actions()

    fun routeFor(actionId: String): MenuRoute? = HelpRegistry.routeFor(actionId)
}
