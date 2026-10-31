/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

/** Android reach and touch grammar shared by the mobile surface. */
class OneHandDensity(
    val touchTargetDp: Int = 44,
    val touchDensityGate: DecoupledErgonomicTouchDensityGate = DecoupledErgonomicTouchDensityGate
) {
    enum class ReachZone { TOP, MIDDLE, BOTTOM }
    data class OfflineResume(val projectId: String, val sessionId: String?, val available: Boolean)

    init {
        touchDensityGate.assertCompliantTouchTarget(touchTargetDp)
    }

    fun reachZone(index: Int, itemCount: Int): ReachZone {
        if (itemCount <= 1) return ReachZone.MIDDLE
        val fraction = index.coerceIn(0, itemCount - 1).toDouble() / (itemCount - 1)
        return when {
            fraction < 0.34 -> ReachZone.TOP
            fraction > 0.66 -> ReachZone.BOTTOM
            else -> ReachZone.MIDDLE
        }
    }

    fun offlineResume(projectId: String, sessionId: String?, online: Boolean): OfflineResume =
        OfflineResume(projectId, sessionId, available = !online && !sessionId.isNullOrBlank())
}
