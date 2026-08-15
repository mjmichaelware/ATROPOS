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

object SecretSinkMatrix {
    private val allowedSinks = mutableSetOf<SecretSinkKind>()

    init {
        // Safe defaults: most sinks are prohibited unless explicitly permitted
        allowedSinks.add(SecretSinkKind.USER_UI)
    }

    fun setPermitted(kind: SecretSinkKind, allowed: Boolean) {
        if (allowed) allowedSinks.add(kind) else allowedSinks.remove(kind)
    }

    fun isEgressPermitted(kind: SecretSinkKind): Boolean {
        return allowedSinks.contains(kind)
    }

    fun resetDefaults() {
        allowedSinks.clear()
        allowedSinks.add(SecretSinkKind.USER_UI)
    }
}
