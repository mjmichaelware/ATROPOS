/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.design

/**
 * The alternate accents Source Doc 5 asks for: "Red-Default, add Blue, Orange,
 * Yellow, and Purple, all deep electric colors."
 *
 * `SUP.UX.ANSI-SCHEME-TOKENS` adds the constraint that makes this more than a
 * colour list: "Ensure consistency across those features and functions for each
 * color — for example command palette is still readable."
 *
 * Each accent is therefore supplied as a triple, not a theme. Only the roles
 * that carry the product's identity take the accent; the semantic status roles
 * do not. A purple theme whose failures render purple would have traded a
 * readable failure state for a consistent hue, and on a phone terminal at
 * arm's length the colour of a failure is the fastest thing about it.
 *
 * Selection is the role this most obviously protects. It reverses white text
 * onto the accent, so the accent has to be dark enough to carry white — which
 * is why every accent here is the deep end of its hue rather than the bright
 * end. A "yellow" that was actually yellow would render the command palette's
 * selected row as white on gold, which is unreadable.
 */
object ElectricThemes {

    /**
     * @param basic the SGR-16 fallback. Constrained to what every terminal has,
     *   so an accent survives a session with no 256-colour support instead of
     *   disappearing.
     */
    data class Accent(
        val id: String,
        val displayName: String,
        val basic: String,
        val indexed: String,
        val truecolor: String,
        /** Darker form, for borders and muted brand text. */
        val basicMuted: String,
        val indexedMuted: String,
        val truecolorMuted: String,
        /** The accent as a background, carrying white text. */
        val selectionBasic: String,
        val selectionIndexed: String,
        val selectionTruecolor: String
    )

    val BLUE = Accent(
        id = "atropos-blue", displayName = "ATROPOS Electric Blue",
        basic = "1;34", indexed = "1;38;5;33", truecolor = "1;38;2;0;122;255",
        basicMuted = "34", indexedMuted = "38;5;25", truecolorMuted = "38;2;0;82;180",
        selectionBasic = "1;97;44",
        selectionIndexed = "1;38;5;255;48;5;25",
        selectionTruecolor = "1;38;2;255;255;255;48;2;0;82;180"
    )

    val ORANGE = Accent(
        id = "atropos-orange", displayName = "ATROPOS Electric Orange",
        basic = "1;33", indexed = "1;38;5;208", truecolor = "1;38;2;255;122;0",
        basicMuted = "33", indexedMuted = "38;5;166", truecolorMuted = "38;2;180;80;0",
        selectionBasic = "1;97;43",
        selectionIndexed = "1;38;5;255;48;5;166",
        selectionTruecolor = "1;38;2;255;255;255;48;2;166;62;0"
    )

    val YELLOW = Accent(
        id = "atropos-yellow", displayName = "ATROPOS Electric Yellow",
        basic = "1;33", indexed = "1;38;5;220", truecolor = "1;38;2;255;214;10",
        basicMuted = "33", indexedMuted = "38;5;136", truecolorMuted = "38;2;161;98;7",
        // Selection uses the muted amber, never the bright yellow: white on
        // gold fails contrast, and the selected row is the one line that has
        // to stay legible for the palette to be usable at all.
        selectionBasic = "1;30;43",
        selectionIndexed = "1;38;5;16;48;5;220",
        selectionTruecolor = "1;38;2;9;9;11;48;2;255;214;10"
    )

    val PURPLE = Accent(
        id = "atropos-purple", displayName = "ATROPOS Electric Purple",
        basic = "1;35", indexed = "1;38;5;135", truecolor = "1;38;2;175;82;255",
        basicMuted = "35", indexedMuted = "38;5;91", truecolorMuted = "38;2;110;40;180",
        selectionBasic = "1;97;45",
        selectionIndexed = "1;38;5;255;48;5;91",
        selectionTruecolor = "1;38;2;255;255;255;48;2;110;40;180"
    )

    val ACCENTS: List<Accent> = listOf(BLUE, ORANGE, YELLOW, PURPLE)

    /**
     * Builds a full dark palette around [accent].
     *
     * Every role is supplied, because [ThemePalette] refuses a partial map —
     * which is how a new role gets caught at construction instead of rendering
     * unstyled in four themes nobody looked at.
     */
    fun palette(accent: Accent): ThemePalette = ThemePalette(
        id = accent.id,
        displayName = accent.displayName,
        isDark = true,
        styles = mapOf(
            Role.BRAND to RoleStyle(accent.basic, accent.indexed, accent.truecolor),
            Role.BRAND_MUTED to RoleStyle(accent.basicMuted, accent.indexedMuted, accent.truecolorMuted),

            Role.TEXT_PRIMARY to RoleStyle("1;37", "38;5;253", "38;2;228;228;231"),
            Role.TEXT_SECONDARY to RoleStyle("37", "38;5;245", "38;2;161;161;170"),
            Role.TEXT_MUTED to RoleStyle("90", "38;5;239", "38;2;82;82;91"),
            Role.TEXT_INVERSE to RoleStyle("30", "38;5;16", "38;2;9;9;11"),

            Role.STATUS_IDLE to RoleStyle("90", "38;5;243", "38;2;113;113;122"),
            Role.STATUS_CANCELLED to RoleStyle("9;90", "9;38;5;243", "9;38;2;113;113;122"),
            // Running takes the accent: it is the one status that is about
            // identity rather than outcome, so it can carry the theme without
            // competing with success or failure.
            Role.STATUS_RUNNING to RoleStyle(accent.basic, accent.indexed, accent.truecolor),
            Role.STATUS_WAITING to RoleStyle("33", "38;5;179", "38;2;217;164;65"),
            Role.STATUS_FAILED to RoleStyle("1;31", "1;38;5;203", "1;38;2;239;68;68"),
            Role.STATUS_COMPLETE to RoleStyle("1;32", "1;38;5;42", "1;38;2;34;197;94"),
            Role.STATUS_UNKNOWN to RoleStyle("90", "38;5;243", "38;2;113;113;122"),
            Role.INFO to RoleStyle("36", "38;5;74", "38;2;56;164;220"),

            // Verified, pending and error keep green/amber/red in every theme.
            // These three answer "did it work?", and a reader must not have to
            // know which accent is active to know the answer.
            Role.STATUS_VERIFIED to RoleStyle("1;32", "1;38;5;42", "1;38;2;34;197;94"),
            Role.STATUS_PENDING to RoleStyle("33", "38;5;179", "38;2;217;164;65"),
            Role.STATUS_ERROR to RoleStyle("1;31", "1;38;5;203", "1;38;2;239;68;68"),

            Role.SURFACE_HEADER to RoleStyle("46;30", "48;5;235;38;5;250", "48;2;24;24;27;38;2;212;212;216"),
            Role.SURFACE_FOOTER to RoleStyle("46;30", "48;5;235;38;5;245", "48;2;24;24;27;38;2;161;161;170"),

            Role.BORDER_SUBTLE to RoleStyle("90", "38;5;239", "38;2;63;63;70"),
            Role.BORDER_STRONG to RoleStyle("37", "38;5;245", "38;2;113;113;122"),

            Role.ACCENT_SELECTION to RoleStyle(
                accent.selectionBasic,
                accent.selectionIndexed,
                accent.selectionTruecolor
            ),
            Role.ACCENT_FOCUS to RoleStyle(accent.basic, accent.indexed, accent.truecolor),

            Role.CODE to RoleStyle("37", "38;5;252", "38;2;212;212;216"),
            Role.PATH to RoleStyle(accent.basicMuted, accent.indexedMuted, accent.truecolorMuted),

            Role.DIFF_ADD to RoleStyle("32", "38;5;42", "38;2;34;197;94"),
            Role.DIFF_REMOVE to RoleStyle("31", "38;5;203", "38;2;239;68;68"),
            Role.DIFF_CONTEXT to RoleStyle("90", "38;5;243", "38;2;113;113;122"),
            Role.DIFF_HUNK to RoleStyle(accent.basicMuted, accent.indexedMuted, accent.truecolorMuted)
        )
    )

    /** All four accents as themes, ready for [ThemeCatalog]. */
    fun palettes(): List<ThemePalette> = ACCENTS.map(::palette)
}
