/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.design

/**
 * Terminal-safe box-drawing and separator glyphs.
 * Provides Unicode box-drawing characters with ASCII fallbacks for terminals
 * that don't support Unicode (e.g., TERM=dumb, NO_COLOR, legacy terminals).
 *
 * Renderers should always use `glyph(unicode, ascii)` pattern to support
 * both Unicode and ASCII-only modes, never hardcode raw box characters.
 */
object Glyphs {
  /** Full-width horizontal rule (box-drawing) */
  const val RULE: String = "─"

  /** Section mark/heading separator */
  const val SECTION_MARK: String = "──"

  /** Bullet point for lists */
  const val BULLET: String = "•"

  /** Vertical rail for multi-line blocks (box-drawing) */
  const val RAIL: String = "│"

  /** Padding between rail and content (cells) */
  const val RAIL_PADDING: Int = 1

  /** ASCII fallback glyphs for terminals without Unicode support */
  object Ascii {
    const val RULE: String = "-"
    const val SECTION_MARK: String = "--"
    const val BULLET: String = "*"
    const val RAIL: String = "|"
  }
}
