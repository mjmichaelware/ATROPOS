/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Global reactive ActiveProvider state.
 *
 * Holds the current execution engine and dynamically notifies UI listeners
 * in real-time when a fallback swap or provider rotation occurs.
 */
object ActiveProvider {
    @Volatile
    private var currentProvider: String = "openrouter"

    private val listeners = CopyOnWriteArrayList<(String) -> Unit>()

    /** Get the currently active execution engine. */
    fun get(): String = currentProvider

    /**
     * Update the active execution engine in real-time.
     * Fires all registered listeners so UI elements re-render immediately.
     */
    fun set(provider: String) {
        val clean = provider.trim().lowercase()
        if (clean.isBlank()) return
        if (currentProvider != clean) {
            currentProvider = clean
            for (listener in listeners) {
                try {
                    listener(clean)
                } catch (_: Exception) {
                    // Soft-fail: UI listener failure must not interrupt execution thread
                }
            }
        }
    }

    /** Register a real-time update listener. */
    fun addListener(listener: (String) -> Unit) {
        listeners.add(listener)
    }

    /** Unregister an update listener. */
    fun removeListener(listener: (String) -> Unit) {
        listeners.remove(listener)
    }

    /** Clear all listeners and reset to default. */
    fun reset(defaultProvider: String = "openrouter") {
        listeners.clear()
        currentProvider = defaultProvider.trim().lowercase()
    }
}
