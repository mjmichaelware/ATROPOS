/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.design

import atropos.core.contract.UiDesignTokens

/**
 * Spacing and layout constants for terminal UI composition.
 * These are tokens derived from DesignTokens.Layout and exposed as named constants
 * for use in Surface and other renderers.
 */
object Spacing {
  const val LABEL_WIDTH: Int = DesignTokens.Layout.labelWidth
  const val LABEL_WIDTH_DENSE: Int = DesignTokens.Layout.labelWidthDense
  const val GUTTER: Int = DesignTokens.Layout.gutter
  const val CONTINUATION_INDENT: Int = DesignTokens.Layout.continuationIndent
  const val MIN_WIDTH: Int = DesignTokens.Layout.minWidth

  /** Shared concentricity token used by framed terminal surfaces. */
  val PANEL_RADIUS: Double = UiDesignTokens.parentRadiusMinusInset(16.0, 4.0)
}
