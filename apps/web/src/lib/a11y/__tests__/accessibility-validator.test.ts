import { describe, it, expect, beforeEach } from 'vitest'
import { AccessibilityValidator } from '../accessibility-validator'

/**
 * HOE-F04: Accessibility gates validation tests.
 */
describe('AccessibilityValidator', () => {
  let validator: AccessibilityValidator
  let testElement: HTMLElement

  beforeEach(() => {
    validator = new AccessibilityValidator()
    testElement = document.createElement('div')
    document.body.appendChild(testElement)
  })

  afterEach(() => {
    document.body.removeChild(testElement)
  })

  it('identifies keyboard-accessible button', () => {
    const button = document.createElement('button')
    testElement.appendChild(button)

    expect(validator.isKeyboardAccessible(button)).toBe(true)
  })

  it('identifies non-keyboard interactive element', () => {
    const div = document.createElement('div')
    div.style.cursor = 'pointer'
    testElement.appendChild(div)

    expect(validator.isKeyboardAccessible(div)).toBe(false)
  })

  it('respects tabindex >= 0', () => {
    const div = document.createElement('div')
    div.setAttribute('tabindex', '0')
    testElement.appendChild(div)

    expect(validator.isKeyboardAccessible(div)).toBe(true)
  })

  it('detects color-only status indicators', () => {
    const colorIndicator = document.createElement('div')
    colorIndicator.style.color = 'red'
    colorIndicator.innerHTML = ''
    testElement.appendChild(colorIndicator)

    const findings = validator.hasNonColorStatusIndicators(testElement)
    expect(findings.length).toBeGreaterThan(0)
    expect(findings[0].category).toBe('color')
  })

  it('exposes one entry point that runs every gate', () => {
    // An instance method, not a static: the validator accumulates findings
    // across calls, so a static entry point would share them between callers.
    const findings = new AccessibilityValidator().validate(testElement)
    expect(Array.isArray(findings)).toBe(true)
  })
})
