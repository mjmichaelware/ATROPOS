/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * The accessibility rules this surface is checked against, as code.
 *
 * Source Doc 3 §E requires colour never to be the only channel carrying state,
 * and WCAG 2.2 AA sets the contrast floors. Both are usually written as prose
 * in a design document, where they are true right up until a component ships a
 * colour-only badge and nobody notices.
 *
 * Held as functions so a test can assert them against the real token tables.
 * The value is not the arithmetic — it is that "we meet AA" becomes a claim
 * something can fail.
 */

/** WCAG 2.2 AA floors. Large text is ≥18.66px bold or ≥24px. */
export const AA_NORMAL_TEXT = 4.5;
export const AA_LARGE_TEXT = 3;
/** §2.5.8: pointer targets are at least 24 CSS pixels on their smaller side. */
export const AA_TARGET_MIN_PX = 24;

function channel(component: number): number {
  const c = component / 255;
  return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
}

/** Parses `#rgb` or `#rrggbb`. Returns null rather than guessing a colour. */
export function parseHex(hex: string): { r: number; g: number; b: number } | null {
  const value = hex.trim().replace(/^#/, '');
  const full =
    value.length === 3
      ? value
          .split('')
          .map((c) => c + c)
          .join('')
      : value;
  if (!/^[0-9a-fA-F]{6}$/.test(full)) return null;
  return {
    r: Number.parseInt(full.slice(0, 2), 16),
    g: Number.parseInt(full.slice(2, 4), 16),
    b: Number.parseInt(full.slice(4, 6), 16),
  };
}

export function relativeLuminance(hex: string): number | null {
  const rgb = parseHex(hex);
  if (!rgb) return null;
  return 0.2126 * channel(rgb.r) + 0.7152 * channel(rgb.g) + 0.0722 * channel(rgb.b);
}

/**
 * WCAG contrast ratio between two colours.
 *
 * Null when either colour cannot be parsed. A default of 21 would report an
 * unparseable token as perfect contrast, and a default of 1 would fail the
 * build for a typo in a test fixture — neither is the truth, which is that the
 * ratio is unknown.
 */
export function contrastRatio(foreground: string, background: string): number | null {
  const fg = relativeLuminance(foreground);
  const bg = relativeLuminance(background);
  if (fg === null || bg === null) return null;
  const lighter = Math.max(fg, bg);
  const darker = Math.min(fg, bg);
  return (lighter + 0.05) / (darker + 0.05);
}

export type ContrastVerdict = 'pass' | 'fail' | 'unknown';

export function meetsContrast(
  foreground: string,
  background: string,
  floor: number = AA_NORMAL_TEXT,
): ContrastVerdict {
  const ratio = contrastRatio(foreground, background);
  if (ratio === null) return 'unknown';
  return ratio >= floor ? 'pass' : 'fail';
}

/**
 * One state as the surface presents it.
 *
 * `icon` and `label` are the non-colour channels. Both are optional in the type
 * because the point of the check is to catch a state that has neither.
 */
export interface StatePresentation {
  readonly id: string;
  readonly label?: string | null;
  readonly icon?: string | null;
  readonly colorOnly?: boolean;
}

export interface ConformanceFinding {
  readonly id: string;
  readonly rule: string;
  readonly detail: string;
}

/**
 * Finds states that carry meaning in colour alone.
 *
 * A state passes when it has a label or an icon. A greyed label counts, an
 * `aria-label` counts; the tinted background alone does not, because Source Doc
 * 3 §E's failure mode is a monochrome display or a colour-blind operator seeing
 * two identical badges.
 */
export function colourOnlyStates(
  states: readonly StatePresentation[],
): readonly ConformanceFinding[] {
  return states
    .filter((state) => {
      const hasLabel = typeof state.label === 'string' && state.label.trim() !== '';
      const hasIcon = typeof state.icon === 'string' && state.icon.trim() !== '';
      return state.colorOnly === true || (!hasLabel && !hasIcon);
    })
    .map((state) => ({
      id: state.id,
      rule: 'source-doc-3-§E',
      detail: `"${state.id}" is distinguished by colour alone; it needs a label or an icon.`,
    }));
}

/** §2.5.8: targets below the floor, reported with their measured size. */
export function undersizedTargets(
  targets: readonly { id: string; width: number; height: number }[],
  floor: number = AA_TARGET_MIN_PX,
): readonly ConformanceFinding[] {
  return targets
    .filter((target) => Math.min(target.width, target.height) < floor)
    .map((target) => ({
      id: target.id,
      rule: 'wcag-2.2-aa-2.5.8',
      detail: `"${target.id}" is ${target.width}×${target.height}px; the floor is ${floor}px.`,
    }));
}

/**
 * The conformance verdict for a set of findings.
 *
 * `conformant` requires zero findings AND at least one thing checked. An empty
 * check set returning "conformant" is how a conformance suite passes after
 * someone deletes its fixtures.
 */
export function verdict(
  checked: number,
  findings: readonly ConformanceFinding[],
): { conformant: boolean; checked: number; findings: readonly ConformanceFinding[]; reason: string } {
  if (checked === 0) {
    return {
      conformant: false,
      checked,
      findings,
      reason: 'Nothing was checked, so nothing was shown to conform.',
    };
  }
  return {
    conformant: findings.length === 0,
    checked,
    findings,
    reason:
      findings.length === 0
        ? `${checked} item(s) checked, no findings.`
        : `${findings.length} finding(s) across ${checked} item(s).`,
  };
}
