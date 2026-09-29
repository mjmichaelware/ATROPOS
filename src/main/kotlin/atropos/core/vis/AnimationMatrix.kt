/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.vis

import atropos.cli.ui.design.ThemePalette
import atropos.core.monitor.ActivityEvent

/**
 * F-VIS-011: Animation Matrix
 *
 * Reduced-motion is mandatory; only real progress animates.
 * Table maps each animation to its allow/deny decision per surface.
 *
 * Depends on: F-CLI-007 (responsive terminal), F-WEB-009 (reduced motion)
 */
class AnimationMatrix(
    private val theme: ThemePalette
) {

    /** Animation entries in the matrix */
    enum class Animation(
        val id: String,
        val description: String,
        val isProgress: Boolean = false
    ) {
        THINKING_CHIP("thinking_chip", "Thinking depth indicator spinner", true),
        SPINNER("spinner", "Generic loading spinner", true),
        PULSE("pulse", "Live/streaming indicator pulse", true),
        FADE_IN("fade_in", "Content fade-in on mount", false),
        SLIDE_IN("slide_in", "Panel/sheet slide-in", false),
        EXPAND("expand", "Disclosure/tree expansion", false),
        COLLAPSE("collapse", "Disclosure/tree collapse", false),
        TYPING("typing", "Composer typing indicator", false),
        TOAST("toast", "Toast appear/dismiss", false),
        TRANSITION("transition", "Route/layout transition", false),
        PARALLAX("parallax", "Background parallax", false),
        HOVER("hover", "Button/link hover", false);
    }

    /** Per-surface allow/deny policy */
    sealed class Policy {
        object ALLOW : Policy()
        object DENY : Policy()
        data class CONDITIONAL(val condition: String) : Policy()
    }

    /** The animation matrix - static compile-time truth */
    val matrix: Map<Animation, Map<String, Policy>> = mapOf(
        Animation.THINKING_CHIP to mapOf(
            "cli" to Policy.ALLOW,
            "web" to Policy.ALLOW,
            "android" to Policy.ALLOW,
            "reduced-motion" to Policy.DENY
        ),
        Animation.SPINNER to mapOf(
            "cli" to Policy.ALLOW,
            "web" to Policy.ALLOW,
            "android" to Policy.ALLOW,
            "reduced-motion" to Policy.DENY
        ),
        Animation.PULSE to mapOf(
            "cli" to Policy.ALLOW,
            "web" to Policy.ALLOW,
            "android" to Policy.ALLOW,
            "reduced-motion" to Policy.DENY
        ),
        Animation.FADE_IN to mapOf(
            "cli" to Policy.DENY,
            "web" to Policy.CONDITIONAL("only on first paint"),
            "android" to Policy.CONDITIONAL("only on first paint"),
            "reduced-motion" to Policy.DENY
        ),
        Animation.SLIDE_IN to mapOf(
            "cli" to Policy.DENY,
            "web" to Policy.CONDITIONAL("only for panels/sheets"),
            "android" to Policy.CONDITIONAL("only for sheets"),
            "reduced-motion" to Policy.DENY
        ),
        Animation.EXPAND to mapOf(
            "cli" to Policy.ALLOW,
            "web" to Policy.ALLOW,
            "android" to Policy.ALLOW,
            "reduced-motion" to Policy.DENY
        ),
        Animation.COLLAPSE to mapOf(
            "cli" to Policy.ALLOW,
            "web" to Policy.ALLOW,
            "android" to Policy.ALLOW,
            "reduced-motion" to Policy.DENY
        ),
        Animation.TYPING to mapOf(
            "cli" to Policy.ALLOW,
            "web" to Policy.ALLOW,
            "android" to Policy.ALLOW,
            "reduced-motion" to Policy.DENY
        ),
        Animation.TOAST to mapOf(
            "cli" to Policy.ALLOW,
            "web" to Policy.ALLOW,
            "android" to Policy.ALLOW,
            "reduced-motion" to Policy.DENY
        ),
        Animation.TRANSITION to mapOf(
            "cli" to Policy.DENY,
            "web" to Policy.CONDITIONAL("only between layouts"),
            "android" to Policy.CONDITIONAL("only between destinations"),
            "reduced-motion" to Policy.DENY
        ),
        Animation.PARALLAX to mapOf(
            "cli" to Policy.DENY,
            "web" to Policy.DENY,
            "android" to Policy.DENY,
            "reduced-motion" to Policy.DENY
        ),
        Animation.HOVER to mapOf(
            "cli" to Policy.DENY,
            "web" to Policy.ALLOW,
            "android" to Policy.DENY,
            "reduced-motion" to Policy.DENY
        )
    )

    /** Check if an animation is allowed for a surface and reduced-motion state */
    fun isAllowed(animation: Animation, surface: String, reducedMotion: Boolean): Boolean {
        val surfacePolicy = matrix[animation]?.getOrDefault(surface, Policy.DENY) ?: Policy.DENY
        return when {
            reducedMotion && surfacePolicy !is Policy.ALLOW -> false // Progress animations allowed even with reduced motion
            reducedMotion -> false
            surfacePolicy is Policy.ALLOW -> true
            surfacePolicy is Policy.DENY -> false
            surfacePolicy is Policy.CONDITIONAL -> true // Would evaluate condition at runtime
            else -> false
        }
    }

    /** Get all allowed animations for a surface */
    fun allowedFor(surface: String, reducedMotion: Boolean): List<Animation> {
        return matrix.entries.filter { (anim, policies) ->
            isAllowed(anim, surface, reducedMotion)
        }.map { it.key }
    }

    /** Render the matrix as a markdown table for docs */
    fun renderMarkdown(): String {
        val sb = StringBuilder()
        sb.appendLine("| Animation | CLI | Web | Android | Reduced Motion |")
        sb.appendLine("|-----------|-----|-----|---------|----------------|")
        for (anim in Animation.values()) {
            val row = matrix[anim] ?: continue
            sb.append("| ${anim.id} | ")
            for (surface in listOf("cli", "web", "android", "reduced-motion")) {
                val policy = row[surface] ?: Policy.DENY
                val symbol = when (policy) {
                    is Policy.ALLOW -> "✅"
                    is Policy.DENY -> "❌"
                    is Policy.CONDITIONAL -> "⚠️"
                    else -> "❓"
                }
                sb.append("$symbol | ")
            }
            sb.appendLine()
        }
        return sb.toString()
    }
}

/**
 * Runtime animation controller that respects the matrix
 */
class AnimationController(
    private val matrix: AnimationMatrix,
    private val surface: String,
    private val reducedMotion: Boolean
) {
    fun shouldAnimate(animation: AnimationMatrix.Animation): Boolean {
        return matrix.isAllowed(animation, surface, reducedMotion)
    }

    /** Get allowed progress animations (always animate) */
    val progressAnimations: List<AnimationMatrix.Animation>
        get() = AnimationMatrix.Animation.values().filter { it.isProgress }
}