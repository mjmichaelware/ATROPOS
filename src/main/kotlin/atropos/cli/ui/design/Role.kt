/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.design

/**
 * Semantic role vocabulary for CLI rendering.
 * Every [Role] must be defined in every [ThemePalette].
 */
enum class Role {
    // Brand identity
    BRAND,
    BRAND_MUTED,

    // Text roles (foreground)
    TEXT_PRIMARY,
    TEXT_SECONDARY,
    TEXT_MUTED,
    TEXT_INVERSE,

    // Status indicators (semantic colors)
    STATUS_IDLE,
    STATUS_CANCELLED,
    STATUS_RUNNING,
    STATUS_WAITING,
    STATUS_FAILED,
    STATUS_COMPLETE,
    STATUS_UNKNOWN,
    INFO,

    // Verification status
    STATUS_VERIFIED,
    STATUS_PENDING,
    STATUS_ERROR,

    // Surface backgrounds
    SURFACE_HEADER,
    SURFACE_FOOTER,

    // Borders
    BORDER_SUBTLE,
    BORDER_STRONG,

    // Accents
    ACCENT_SELECTION,
    ACCENT_FOCUS,

    // Code and paths
    CODE,
    PATH,

    // Diff visualization
    DIFF_ADD,
    DIFF_REMOVE,
    DIFF_CONTEXT,
    DIFF_HUNK;

    companion object {
        /**
         * Convenience for mapping RunState or similar enums to visual roles.
         */
        fun fromStatus(status: String): Role = when (status.lowercase()) {
            "idle" -> STATUS_IDLE
            "cancelled" -> STATUS_CANCELLED
            "running", "working" -> STATUS_RUNNING
            "waiting", "review-required" -> STATUS_WAITING
            "failed" -> STATUS_FAILED
            "completed" -> STATUS_COMPLETE
            "verified" -> STATUS_VERIFIED
            "pending" -> STATUS_PENDING
            "error" -> STATUS_ERROR
            else -> STATUS_UNKNOWN
        }
    }
}
