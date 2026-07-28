/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.design

/** ATROPOS Design Tokens - Red Theme */
/** Auto-generated from design-tokens/src/tokens.json - DO NOT EDIT MANUALLY */
/** Primary color is RED, shared with web UI */

object DesignTokens {
  // Colors - RED Theme
  object Colors {

    object Red {
      const val shade50: String = "#fef2f2" // red-50
      const val shade100: String = "#fee2e2" // red-100
      const val shade200: String = "#fecaca" // red-200
      const val shade300: String = "#fca5a5" // red-300
      const val shade400: String = "#f87171" // red-400
      const val shade500: String = "#ef4444" // red-500
      const val shade600: String = "#dc2626" // red-600
      const val shade700: String = "#b91c1c" // red-700
      const val shade800: String = "#991b1b" // red-800
      const val shade900: String = "#7f1d1d" // red-900
      const val shade950: String = "#4c0519" // red-950
    }

    object Gray {
      const val shade50: String = "#f9fafb" // gray-50
      const val shade100: String = "#f3f4f6" // gray-100
      const val shade200: String = "#e5e7eb" // gray-200
      const val shade300: String = "#d1d5db" // gray-300
      const val shade400: String = "#9ca3af" // gray-400
      const val shade500: String = "#6b7280" // gray-500
      const val shade600: String = "#4b5563" // gray-600
      const val shade700: String = "#374151" // gray-700
      const val shade800: String = "#1f2937" // gray-800
      const val shade900: String = "#111827" // gray-900
      const val shade950: String = "#030712" // gray-950
    }

    object Green {
      const val shade50: String = "#f0fdf4" // green-50
      const val shade100: String = "#dcfce7" // green-100
      const val shade200: String = "#bbf7d0" // green-200
      const val shade300: String = "#86efac" // green-300
      const val shade400: String = "#4ade80" // green-400
      const val shade500: String = "#22c55e" // green-500
      const val shade600: String = "#16a34a" // green-600
      const val shade700: String = "#15803d" // green-700
      const val shade800: String = "#166534" // green-800
      const val shade900: String = "#145231" // green-900
    }

    object Amber {
      const val shade50: String = "#fffbeb" // amber-50
      const val shade100: String = "#fef3c7" // amber-100
      const val shade200: String = "#fde68a" // amber-200
      const val shade300: String = "#fcd34d" // amber-300
      const val shade400: String = "#fbbf24" // amber-400
      const val shade500: String = "#f59e0b" // amber-500
      const val shade600: String = "#d97706" // amber-600
      const val shade700: String = "#b45309" // amber-700
      const val shade800: String = "#92400e" // amber-800
      const val shade900: String = "#78350f" // amber-900
    }

    object Blue {
      const val shade50: String = "#eff6ff" // blue-50
      const val shade100: String = "#dbeafe" // blue-100
      const val shade200: String = "#bfdbfe" // blue-200
      const val shade300: String = "#93c5fd" // blue-300
      const val shade400: String = "#60a5fa" // blue-400
      const val shade500: String = "#3b82f6" // blue-500
      const val shade600: String = "#2563eb" // blue-600
      const val shade700: String = "#1d4ed8" // blue-700
      const val shade800: String = "#1e40af" // blue-800
      const val shade900: String = "#1e3a8a" // blue-900
    }

    object Cyan {
      const val shade50: String = "#ecf9ff" // cyan-50
      const val shade100: String = "#d3f0ff" // cyan-100
      const val shade200: String = "#a8e0ff" // cyan-200
      const val shade300: String = "#77cbff" // cyan-300
      const val shade400: String = "#4fb0ff" // cyan-400
      const val shade500: String = "#0096ff" // cyan-500
      const val shade600: String = "#0078d4" // cyan-600
      const val shade700: String = "#0063b1" // cyan-700
      const val shade800: String = "#004b87" // cyan-800
      const val shade900: String = "#003d6b" // cyan-900
    }

  }

  // Semantic Colors
  object Semantic {
    object Brand {
      const val primary: String = "#dc2626" // Primary brand red
      const val primaryLight: String = "#f87171"
      const val primaryDark: String = "#991b1b"
      const val hover: String = "#b91c1c"
      const val active: String = "#7f1d1d"
      const val muted: String = "#fee2e2"
    }

    object Status {
      const val success: String = "#16a34a"
      const val warning: String = "#d97706"
      const val danger: String = "#dc2626"
      const val info: String = "#2563eb"
      const val pending: String = "#6b7280"
    }

    object Domain {
      const val source: String = "#0096ff"
      const val research: String = "#f59e0b"
      const val planning: String = "#3b82f6"
      const val verified: String = "#16a34a"
      const val blocked: String = "#dc2626"
      const val unknown: String = "#6b7280"
    }
  }

  // Spacing Scale
  object Spacing {
    const val space0: Int = 0
    const val space1: Int = 4
    const val space2: Int = 8
    const val space3: Int = 12
    const val space4: Int = 16
    const val space6: Int = 24
    const val space8: Int = 32
    const val space12: Int = 48
    const val space16: Int = 64
    const val space20: Int = 80
    const val space24: Int = 96
  }

  // Motion Durations (milliseconds)
  object Motion {
    const val instant: Long = 0L
    const val fast: Long = 100L
    const val normal: Long = 200L
    const val slow: Long = 300L
    const val reveal: Long = 500L
    const val morph: Long = 700L
    const val press: Long = 50L
  }

  // Typography
  object Typography {
    const val fontFamilySans: String = "-apple-system, BlinkMacSystemFont, Segoe UI, Roboto, sans-serif"
    const val fontFamilyMono: String = "SF Mono, Monaco, Cascadia Code, Roboto Mono, monospace"
    const val fontSizeXs: Int = 12
    const val fontSizeSm: Int = 14
    const val fontSizeBase: Int = 16
    const val fontSizeLg: Int = 18
    const val fontSizeXl: Int = 20
    const val fontWeight400: Int = 400
    const val fontWeight500: Int = 500
    const val fontWeight600: Int = 600
    const val fontWeight700: Int = 700
  }

  // Z-Index Scale
  object ZIndex {
    const val hide: Int = -1
    const val base: Int = 0
    const val docked: Int = 10
    const val sticky: Int = 20
    const val fixed: Int = 30
    const val backdrop: Int = 40
    const val modal: Int = 60
    const val popover: Int = 70
    const val toast: Int = 90
    const val tooltip: Int = 100
  }

  // Layout Constants (matching CLI design)
  object Layout {
    const val labelWidth: Int = 16
    const val labelWidthDense: Int = 12
    const val gutter: Int = 2
    const val continuationIndent: Int = 2
  }

  // Theme Preferences
  enum class Theme {
    LIGHT, DARK, HIGH_CONTRAST, SYSTEM
  }

  fun getPrimaryRed(theme: Theme): String = when (theme) {
    Theme.LIGHT -> Colors.Red.shade600
    Theme.DARK -> Colors.Red.shade500
    Theme.HIGH_CONTRAST -> Colors.Red.shade400
    Theme.SYSTEM -> Colors.Red.shade600
  }
}

private fun capitalize(s: String): String = s.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
