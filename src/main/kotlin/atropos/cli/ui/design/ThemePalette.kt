/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.design

/**
 * What the attached terminal can actually render. Detected once, never guessed
 * per-call, and never assumed to be the best case.
 */
enum class ColorTier {
    /** NO_COLOR, TERM=dumb, or a non-interactive pipe. Emits no SGR at all. */
    NONE,

    /** Guaranteed-portable 16-color SGR. */
    BASIC,

    /** 256-color indexed. The ATROPOS default. */
    INDEXED,

    /** 24-bit truecolor, used when COLORTERM advertises it. */
    TRUECOLOR;

    companion object {
        fun detect(
            colorEnabled: Boolean,
            term: String?,
            colorterm: String?
        ): ColorTier {
            if (!colorEnabled) return NONE
            val t = term.orEmpty().lowercase()
            if (t.isEmpty() || t == "dumb") return NONE

            val ct = colorterm.orEmpty().lowercase()
            if (ct.contains("truecolor") || ct.contains("24bit")) return TRUECOLOR
            if (t.contains("256") || t.contains("kitty") || t.contains("alacritty")) return INDEXED
            if (t.contains("xterm") || t.contains("screen") || t.contains("tmux")) return INDEXED
            return BASIC
        }
    }
}

/** SGR parameters for one role, per tier. Empty means "emit nothing". */
data class RoleStyle(
    val basic: String,
    val indexed: String = basic,
    val truecolor: String = indexed
) {
    fun forTier(tier: ColorTier): String = when (tier) {
        ColorTier.NONE -> ""
        ColorTier.BASIC -> basic
        ColorTier.INDEXED -> indexed
        ColorTier.TRUECOLOR -> truecolor
    }
}

/**
 * A complete named theme: every [Role] mapped at every tier.
 *
 * Palettes are the only place raw SGR parameters may appear. The role set is
 * exhaustive by construction — [ThemePalette] requires a style for every role,
 * so adding a role fails the build until every theme defines it, rather than
 * silently rendering unstyled in some themes.
 */
class ThemePalette(
    val id: String,
    val displayName: String,
    val isDark: Boolean,
    private val styles: Map<Role, RoleStyle>
) {
    init {
        val missing = Role.entries.filterNot(styles::containsKey)
        require(missing.isEmpty()) {
            "theme '$id' is missing styles for: ${missing.joinToString(", ")}"
        }
    }

    fun style(role: Role, tier: ColorTier): String =
        styles.getValue(role).forTier(tier)
}

/**
 * Built-in themes. ATROPOS keeps its cyan-on-black identity as the default; the
 * alternates exist so the token layer is proven to be genuinely theme-independent
 * rather than one palette with indirection bolted on.
 */
object ThemeCatalog {
    const val DEFAULT_ID = "atropos-dark"

    /** ATROPOS default: restrained cyan identity, high contrast, dark. */
    private val atroposDark = ThemePalette(
        id = "atropos-dark",
        displayName = "ATROPOS Dark",
        isDark = true,
        styles = mapOf(
            Role.BRAND to RoleStyle("1;36", "1;38;5;51", "1;38;2;34;211;238"),
            Role.BRAND_MUTED to RoleStyle("36", "38;5;37", "38;2;14;165;183"),

            Role.TEXT_PRIMARY to RoleStyle("1;37", "38;5;253", "38;2;228;228;231"),
            Role.TEXT_SECONDARY to RoleStyle("37", "38;5;245", "38;2;161;161;170"),
            Role.TEXT_MUTED to RoleStyle("90", "38;5;239", "38;2;82;82;91"),
            Role.TEXT_INVERSE to RoleStyle("30", "38;5;16", "38;2;9;9;11"),

            Role.STATUS_IDLE to RoleStyle("90", "38;5;243", "38;2;113;113;122"),
            Role.STATUS_CANCELLED to RoleStyle("9;90", "9;38;5;243", "9;38;2;113;113;122"),
            Role.STATUS_RUNNING to RoleStyle("1;36", "1;38;5;51", "1;38;2;34;211;238"),
            Role.STATUS_WAITING to RoleStyle("33", "38;5;179", "38;2;217;164;65"),
            Role.STATUS_FAILED to RoleStyle("1;31", "1;38;5;203", "1;38;2;239;68;68"),
            Role.STATUS_COMPLETE to RoleStyle("1;32", "1;38;5;42", "1;38;2;34;197;94"),
            Role.STATUS_UNKNOWN to RoleStyle("90", "38;5;243", "38;2;113;113;122"),
            Role.INFO to RoleStyle("36", "38;5;74", "38;2;56;164;220"),

            Role.STATUS_VERIFIED to RoleStyle("1;32", "1;38;5;42", "1;38;2;34;197;94"),
            Role.STATUS_PENDING to RoleStyle("33", "38;5;179", "38;2;217;164;65"),
            Role.STATUS_ERROR to RoleStyle("1;31", "1;38;5;203", "1;38;2;239;68;68"),

            Role.SURFACE_HEADER to RoleStyle("46;30", "48;5;235;38;5;250", "48;2;24;24;27;38;2;212;212;216"),
            Role.SURFACE_FOOTER to RoleStyle("46;30", "48;5;235;38;5;245", "48;2;24;24;27;38;2;161;161;170"),

            Role.BORDER_SUBTLE to RoleStyle("90", "38;5;239", "38;2;63;63;70"),
            Role.BORDER_STRONG to RoleStyle("37", "38;5;245", "38;2;113;113;122"),

            Role.ACCENT_SELECTION to RoleStyle("30;46", "38;5;16;48;5;51", "38;2;9;9;11;48;2;34;211;238"),
            Role.ACCENT_FOCUS to RoleStyle("1;36", "1;38;5;51", "1;38;2;34;211;238"),

            Role.CODE to RoleStyle("37", "38;5;252", "38;2;212;212;216"),
            Role.PATH to RoleStyle("36", "38;5;44", "38;2;34;197;211"),

            Role.DIFF_ADD to RoleStyle("32", "38;5;42", "38;2;34;197;94"),
            Role.DIFF_REMOVE to RoleStyle("31", "38;5;203", "38;2;239;68;68"),
            Role.DIFF_CONTEXT to RoleStyle("90", "38;5;243", "38;2;113;113;122"),
            Role.DIFF_HUNK to RoleStyle("36", "38;5;37", "38;2;14;165;183")
        )
    )

    /** Light-terminal variant. Same roles, darker inks for contrast on white. */
    private val atroposLight = ThemePalette(
        id = "atropos-light",
        displayName = "ATROPOS Light",
        isDark = false,
        styles = mapOf(
            Role.BRAND to RoleStyle("1;36", "1;38;5;30", "1;38;2;14;116;144"),
            Role.BRAND_MUTED to RoleStyle("36", "38;5;31", "38;2;8;145;178"),

            Role.TEXT_PRIMARY to RoleStyle("30", "38;5;235", "38;2;24;24;27"),
            Role.TEXT_SECONDARY to RoleStyle("90", "38;5;241", "38;2;82;82;91"),
            Role.TEXT_MUTED to RoleStyle("37", "38;5;247", "38;2;140;140;150"),
            Role.TEXT_INVERSE to RoleStyle("97", "38;5;231", "38;2;250;250;250"),

            Role.STATUS_IDLE to RoleStyle("90", "38;5;245", "38;2;113;113;122"),
            Role.STATUS_CANCELLED to RoleStyle("9;90", "9;38;5;245", "9;38;2;113;113;122"),
            Role.STATUS_RUNNING to RoleStyle("36", "38;5;30", "38;2;14;116;144"),
            Role.STATUS_WAITING to RoleStyle("33", "38;5;136", "38;2;161;98;7"),
            Role.STATUS_FAILED to RoleStyle("31", "38;5;160", "38;2;185;28;28"),
            Role.STATUS_COMPLETE to RoleStyle("32", "38;5;28", "38;2;21;128;61"),
            Role.STATUS_UNKNOWN to RoleStyle("90", "38;5;245", "38;2;113;113;122"),
            Role.INFO to RoleStyle("36", "38;5;25", "38;2;29;110;175"),

            Role.STATUS_VERIFIED to RoleStyle("32", "38;5;28", "38;2;21;128;61"),
            Role.STATUS_PENDING to RoleStyle("33", "38;5;136", "38;2;161;98;7"),
            Role.STATUS_ERROR to RoleStyle("31", "38;5;160", "38;2;185;28;28"),

            Role.SURFACE_HEADER to RoleStyle("46;30", "48;5;254;38;5;235", "48;2;228;228;231;38;2;24;24;27"),
            Role.SURFACE_FOOTER to RoleStyle("46;30", "48;5;254;38;5;241", "48;2;228;228;231;38;2;82;82;91"),

            Role.BORDER_SUBTLE to RoleStyle("37", "38;5;250", "38;2;212;212;216"),
            Role.BORDER_STRONG to RoleStyle("90", "38;5;244", "38;2;161;161;170"),

            Role.ACCENT_SELECTION to RoleStyle("30;46", "38;5;231;48;5;30", "38;2;250;250;250;48;2;14;116;144"),
            Role.ACCENT_FOCUS to RoleStyle("1;36", "1;38;5;30", "1;38;2;14;116;144"),

            Role.CODE to RoleStyle("30", "38;5;238", "38;2;39;39;42"),
            Role.PATH to RoleStyle("36", "38;5;31", "38;2;8;145;178"),

            Role.DIFF_ADD to RoleStyle("32", "38;5;28", "38;2;21;128;61"),
            Role.DIFF_REMOVE to RoleStyle("31", "38;5;160", "38;2;185;28;28"),
            Role.DIFF_CONTEXT to RoleStyle("90", "38;5;245", "38;2;113;113;122"),
            Role.DIFF_HUNK to RoleStyle("36", "38;5;31", "38;2;8;145;178")
        )
    )

    val all: List<ThemePalette> = listOf(atroposDark, atroposLight)

    fun byId(id: String?): ThemePalette =
        all.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) }
            ?: all.first { it.id == DEFAULT_ID }
}
