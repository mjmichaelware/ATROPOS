/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.thinking

/**
 * The reasoning a surface may show, at the depth it was asked for.
 *
 * `HOE-B03`/`HOE-C06` require multi-level thinking — L1 outline, then L2, then
 * L3 on explicit expand — and `HOE-E04` requires each surface to own its own
 * verbosity, so a terminal expanded to L3 never forces the Web to follow. Both
 * atoms were unimplementable while the engine exposed no thinking payload at
 * all: a drawer with nothing behind it is a control that lies about what exists.
 *
 * Two rules make this honest rather than decorative:
 *
 * The full depth is stored once and filtered on read. `HOE-B03`'s IMPL note is
 * explicit — "Thinking depth is UI filter only; never change provider task
 * payload" — because a system that asks the provider for less when the drawer
 * is collapsed produces different *reasoning* per surface, not just a different
 * view of it, and the two surfaces would then disagree about what happened.
 *
 * Depth is monotonic. L2 is L1 plus more; L3 is L2 plus more. Expanding must
 * never remove a line the operator was reading, which is the same rule
 * `HOE-A08` applies to disclosure and the same way it is broken — by rendering
 * each level as its own branch instead of as an extension of the last.
 */
enum class ThinkingDepth(val level: Int, val label: String) {
    /** One-line outline. What the operator sees before expanding anything. */
    L1(1, "Outline"),
    L2(2, "Reasoning"),
    L3(3, "Full trace");

    fun includes(other: ThinkingDepth): Boolean = other.level <= level

    companion object {
        val DEFAULT: ThinkingDepth = L1

        fun fromLevel(level: Int): ThinkingDepth? = entries.firstOrNull { it.level == level }
    }
}

/**
 * One line of reasoning, and the depth at which it first appears.
 *
 * `minDepth` rather than `depth`: a line belongs to every level at or above the
 * one that introduced it, which is what makes the filter additive by
 * construction rather than by the caller remembering to include the earlier
 * levels.
 */
data class ThinkingLine(
    val id: String,
    val minDepth: ThinkingDepth,
    val text: String
)

/**
 * The stored reasoning for one node.
 *
 * Redaction is not performed here. It happens at the render and persist
 * boundary, where every other surface's redaction happens — a second redaction
 * point that could drift from the first would be worse than none, because the
 * operator would not know which one had been applied.
 */
data class ThinkingRecord(
    val nodeId: String,
    val lines: List<ThinkingLine>
) {
    /**
     * The lines visible at a depth.
     *
     * Filters by "introduced at or below", never by equality. Equality is
     * exactly the mistake that turns a depth control into a filter, and it
     * looks correct at every individual level.
     */
    fun at(depth: ThinkingDepth): List<ThinkingLine> =
        lines.filter { depth.includes(it.minDepth) }

    /**
     * True when there is anything to reveal beyond this depth.
     *
     * The surface uses this to decide whether an expand control should exist at
     * all. A drawer that opens onto nothing teaches the operator the gesture is
     * meaningless, and the next time there is real reasoning they will not look
     * — the same failure `HOE-C08` names for evidence morphs.
     */
    fun hasMoreThan(depth: ThinkingDepth): Boolean =
        lines.any { !depth.includes(it.minDepth) }

    /** The deepest level that actually carries a line. */
    fun deepestAvailable(): ThinkingDepth? =
        lines.maxByOrNull { it.minDepth.level }?.minDepth

    fun isEmpty(): Boolean = lines.isEmpty()
}

/**
 * Per-surface depth, held apart so one surface cannot move another.
 *
 * `HOE-E04`: "Independent surface verbosity channels (terminal deep / web quiet
 * or reverse)." The engine stores the full depth once; each channel remembers
 * only how much of it that surface asked to see.
 */
class ThinkingChannels(
    private val depths: MutableMap<String, ThinkingDepth> = mutableMapOf()
) {
    fun depthFor(surface: String): ThinkingDepth = depths[surface] ?: ThinkingDepth.DEFAULT

    fun expand(surface: String, depth: ThinkingDepth): ThinkingDepth {
        depths[surface] = depth
        return depth
    }

    /**
     * Collapses one surface back to the default.
     *
     * Returns the surfaces that were left untouched, so a caller can assert the
     * isolation rather than trust it.
     */
    fun collapse(surface: String): Set<String> {
        depths[surface] = ThinkingDepth.DEFAULT
        return depths.keys.filterNot { it == surface }.toSet()
    }

    fun surfaces(): Set<String> = depths.keys.toSet()
}
