/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.welcome

import java.security.MessageDigest

/**
 * The first-boot welcome, content-addressed so it is identical every time.
 *
 * `SUP.UX.FREE-PROVIDER-WELCOME`: "Onboarding is deterministic and zero-cost
 * after first view; free-provider path is first-class. Competitors show generic
 * or cloud-centric welcome."
 *
 * Deterministic is the operative property. A welcome assembled at runtime from
 * whatever providers happen to be reachable differs between boots, which means
 * it cannot be cached by hash, cannot be diffed, and quietly becomes a place
 * where a paid provider can appear first because it answered fastest. Building
 * it from a fixed input and hashing the result makes "same content, same id" a
 * structural fact.
 *
 * The free-provider list is passed in rather than discovered here: discovery is
 * a provider concern, and a welcome that probed the network would be neither
 * deterministic nor zero-cost.
 */
class WelcomeArtifact(
    private val freeProviders: List<String>,
    private val storageCeilingBytes: Long?
) {
    fun render(): String = buildString {
        appendLine("ATROPOS")
        appendLine()
        appendLine("Local-first. Nothing leaves this machine unless you route it somewhere.")
        appendLine()
        appendLine("Providers available without payment:")
        if (freeProviders.isEmpty()) {
            // Honest rather than encouraging: claiming a free path exists when
            // none is configured would strand the operator at the first prompt.
            appendLine("  none configured — /providers to see what is registered")
        } else {
            freeProviders.sorted().forEach { appendLine("  $it") }
        }
        appendLine()
        appendLine("Storage ceiling:")
        appendLine(
            storageCeilingBytes?.let { "  $it bytes; /storage status to see usage" }
                // An undeclared ceiling is not an unlimited one, and the first
                // thing an operator learns should not be a false reassurance.
                ?: "  not declared — storage is unbounded until you set one"
        )
        appendLine()
        appendLine("First command: /help")
    }.trimEnd()

    /** The content address. Identical content always yields an identical id. */
    fun contentId(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(render().toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
