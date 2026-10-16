/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.security

enum class SecretSinkKind {
    PROVIDER_PROMPT,
    MODEL_OUTPUT,
    SYSTEM_LOG,
    USER_UI,
    PERSISTENT_MEMORY,
    RESEARCH_QUERY,
    EGRESS_URL,
    GIT_DIFF,
    EVIDENCE_BUNDLE
}

enum class IntegrationTier {
    TIER_0_LOCAL_TOOLCHAINS,
    TIER_1_HEADLESS_CI,
    TIER_2_TRACKERS,
    TIER_3_SLACK_MCP
}

object SecretSinkMatrix {
    private val allowedSinks = mutableSetOf<SecretSinkKind>()
    private val allowedTiers = mutableSetOf<IntegrationTier>()

    init {
        // Safe defaults: most sinks are prohibited unless explicitly permitted
        allowedSinks.add(SecretSinkKind.USER_UI)
        allowedTiers.add(IntegrationTier.TIER_0_LOCAL_TOOLCHAINS)
        allowedTiers.add(IntegrationTier.TIER_1_HEADLESS_CI)
    }

    fun setPermitted(kind: SecretSinkKind, allowed: Boolean) {
        if (allowed) allowedSinks.add(kind) else allowedSinks.remove(kind)
    }

    fun isEgressPermitted(kind: SecretSinkKind): Boolean {
        return allowedSinks.contains(kind)
    }

    fun isIntegrationPermitted(tier: IntegrationTier): Boolean {
        return allowedTiers.contains(tier)
    }

    fun setIntegrationTierPermitted(tier: IntegrationTier, allowed: Boolean) {
        if (allowed) allowedTiers.add(tier) else allowedTiers.remove(tier)
    }

    /**
     * SD7-T3-006: Scrub tokens via SecretSinkMatrix before clipboard emission.
     */
    fun scrubTokens(text: String): String {
        return text.replace(Regex("""(?i)(bearer\s+|token\s+|key\s*=\s*)[a-zA-Z0-9_\-\.]{16,}"""), "$1[REDACTED_SECRET]")
    }

    /**
     * SD7-T3-006: OSC-52 ANSI clipboard emission with token scrubbing.
     */
    fun emitOsc52(payload: String): String {
        val scrubbed = scrubTokens(payload)
        val encoded = java.util.Base64.getEncoder().encodeToString(scrubbed.toByteArray(Charsets.UTF_8))
        return "\u001b]52;c;$encoded\u0007"
    }

    /**
     * Clipboard bridge cascade: OSC-52 emission -> Termux clipboard -> wl-copy -> pbcopy.
     */
    fun bridgeClipboardCascade(payload: String): String {
        val scrubbed = scrubTokens(payload)
        tryTermuxClipboardSet(scrubbed)
        return emitOsc52(scrubbed)
    }

    /**
     * SD7-R-084: Include termux-clipboard-set in OSC-52 fallback cascade.
     */
    fun copyToClipboardWithFallback(payload: String): Boolean {
        val scrubbed = scrubTokens(payload)
        if (tryTermuxClipboardSet(scrubbed)) {
            return true
        }
        if (tryCommandClipboard(scrubbed, "wl-copy") ||
            tryCommandClipboard(scrubbed, "pbcopy") ||
            tryCommandClipboard(scrubbed, "xclip", "-selection", "clipboard")
        ) {
            return true
        }
        val osc52 = emitOsc52(scrubbed)
        System.err.print(osc52)
        System.err.flush()
        return true
    }

    private fun tryTermuxClipboardSet(text: String): Boolean {
        return tryCommandClipboard(text, "termux-clipboard-set")
    }

    private fun tryCommandClipboard(text: String, vararg command: String): Boolean {
        return try {
            val process = ProcessBuilder(*command).start()
            process.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(text)
            }
            process.waitFor(1, java.util.concurrent.TimeUnit.SECONDS) && process.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }

    fun resetDefaults() {
        allowedSinks.clear()
        allowedSinks.add(SecretSinkKind.USER_UI)
        allowedTiers.clear()
        allowedTiers.add(IntegrationTier.TIER_0_LOCAL_TOOLCHAINS)
        allowedTiers.add(IntegrationTier.TIER_1_HEADLESS_CI)
    }
}
