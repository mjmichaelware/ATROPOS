/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

/** Client model for many explicit sessions; engine storage remains canonical. */
data class SessionTabModel(
    val tabs: List<ChatListEntry> = emptyList(),
    val activeId: String? = null
) {
    fun replace(remote: List<ChatListEntry>): SessionTabModel {
        val nextActive = activeId?.takeIf { id -> remote.any { it.id == id } }
            ?: remote.firstOrNull()?.id
        return copy(tabs = remote, activeId = nextActive)
    }

    fun select(id: String): SessionTabModel =
        if (tabs.any { it.id == id }) copy(activeId = id) else this

    fun explicitResume(): String? = activeId
}
