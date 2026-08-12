/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

/**
 * One turn in a conversation, as the UI needs it.
 *
 * Deliberately not the engine's message type. The client is a thin surface
 * over the bridge (HOE-D01: "never embed full engine"), so it carries only
 * what a bubble has to draw. Anything richer belongs behind the bridge.
 */
data class MobileMessage(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long
)
