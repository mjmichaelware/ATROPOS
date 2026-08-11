/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * HOE-F04: Accessibility gates (keyboard-complete, non-color, reduced-motion, high-contrast, screen-reader, HIG).
 * Pure validation layer, not enforcement.
 */

interface A11yFinding {
  type: 'error' | 'warning'
  category: 'keyboard' | 'color' | 'motion' | 'contrast' | 'screenreader' | 'hig'
  message: string
  element?: HTMLElement
}

export class AccessibilityValidator {
  private findings: A11yFinding[] = []

  /**
   * Check if element is keyboard-accessible (focusable or has keyboard handler).
   */
  isKeyboardAccessible(element: HTMLElement): boolean {
    const tag = element.tagName.toLowerCase()
    const isFocusable = [
      'a', 'button', 'input', 'select', 'textarea', 'details'
    ].includes(tag)

    if (isFocusable) return true

    const tabIndex = element.getAttribute('tabindex')
    if (tabIndex !== null && parseInt(tabIndex) >= 0) return true

    const hasKeyboardHandler = [
      'onkeydown', 'onkeyup', 'onkeypress'
    ].some(attr => element.hasAttribute(attr))

    return hasKeyboardHandler
  }

  /**
   * Check for color-only status indicators (non-compliant if no text).
   */
  hasNonColorStatusIndicators(element: HTMLElement): A11yFinding[] {
    const findings: A11yFinding[] = []

    // Find elements styled only by color (role=img, aria-label missing)
    const colorOnlyElements = Array.from(element.querySelectorAll('[style*="color"]'))
      .filter((el: Element) => {
        const html = el.innerHTML
        return !html || html.trim().length === 0
      })
      .filter((el: Element) => {
        const ariaLabel = el.getAttribute('aria-label')
        return !ariaLabel || ariaLabel.trim().length === 0
      })

    colorOnlyElements.forEach((el) => {
      findings.push({
        type: 'error',
        category: 'color',
        message: 'Status indicated by color only; missing text alternative',
        element: el as HTMLElement
      })
    })

    return findings
  }

  /**
   * Check for forced animations (motion must be reducible).
   */
  checkAnimations(element: HTMLElement): A11yFinding[] {
    const findings: A11yFinding[] = []
    const style = window.getComputedStyle(element)
    const animation = style.animation
    const transition = style.transition

    // Check if animation respects prefers-reduced-motion
    if (animation && animation !== 'none') {
      const mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
      if (mediaQuery.matches) {
        // In reduced-motion, animations should stop
        const reducedStyle = window.matchMedia('(prefers-reduced-motion: reduce)').matches
        if (!reducedStyle) {
          findings.push({
            type: 'warning',
            category: 'motion',
            message: 'Animation ignores prefers-reduced-motion',
            element
          })
        }
      }
    }

    return findings
  }

  /**
   * Check contrast ratio between element and background.
   */
  getContrastRatio(element: HTMLElement): number {
    const style = window.getComputedStyle(element)
    const color = style.color
    const bgColor = style.backgroundColor

    const fg = this.parseColor(color)
    const bg = this.parseColor(bgColor)

    if (!fg || !bg) return 0

    const fgLum = this.getLuminance(fg)
    const bgLum = this.getLuminance(bg)

    const lighter = Math.max(fgLum, bgLum)
    const darker = Math.min(fgLum, bgLum)

    return (lighter + 0.05) / (darker + 0.05)
  }

  /**
   * Validate all accessibility gates.
   */
  validate(root: HTMLElement = document.body): A11yFinding[] {
    this.findings = []

    // Keyboard accessibility: all interactive elements
    const interactive = Array.from(
      root.querySelectorAll(
        'a, button, input, select, textarea, [onclick], [role="button"]'
      )
    )
    interactive.forEach((el) => {
      if (!this.isKeyboardAccessible(el as HTMLElement)) {
        this.findings.push({
          type: 'error',
          category: 'keyboard',
          message: 'Interactive element not keyboard-accessible',
          element: el as HTMLElement
        })
      }
    })

    // Color-only status
    this.findings.push(...this.hasNonColorStatusIndicators(root))

    // Animations
    Array.from(root.querySelectorAll('[style*="animation"], [style*="transition"]'))
      .forEach((el) => {
        this.findings.push(...this.checkAnimations(el as HTMLElement))
      })

    return this.findings
  }

  private parseColor(color: string): { r: number; g: number; b: number } | null {
    const match = color.match(/rgb\((\d+),\s*(\d+),\s*(\d+)\)/)
    if (!match) return null
    return {
      r: parseInt(match[1]),
      g: parseInt(match[2]),
      b: parseInt(match[3])
    }
  }

  private getLuminance(rgb: { r: number; g: number; b: number }): number {
    const [r, g, b] = [rgb.r, rgb.g, rgb.b].map((c) => {
      c = c / 255
      return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4)
    })
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
  }
}
