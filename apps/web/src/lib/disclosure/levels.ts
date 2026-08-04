/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * The four disclosure levels, and the rule that they only ever add.
 *
 * `HOE-A08`: "Default collapsed; each expand reveals only additional detail;
 * never hide prior level." That rule is easy to state and easy to break — a
 * component that renders level 3 as its own branch rather than as level 2 plus
 * one row has silently turned disclosure into a filter, and nothing catches it
 * because each level looks correct in isolation.
 *
 * So the rule lives here as a function rather than as a convention. `visibleAt`
 * is monotonic by construction: the set of fields shown at level N is a strict
 * superset of the set shown at N-1, and `isAdditive` exists so a test can prove
 * it for any field set rather than trusting a reviewer to notice.
 *
 * Pure and framework-free, because the guarantee is about which fields are
 * shown, not about React. A rule embedded in a component can only be tested by
 * rendering one.
 */

export type DisclosureLevel = 1 | 2 | 3 | 4;

export const DISCLOSURE_LEVELS: readonly DisclosureLevel[] = [1, 2, 3, 4];

/** Source Doc 4's names for the levels, for surfaces that label the control. */
export const DISCLOSURE_LABELS: Readonly<Record<DisclosureLevel, string>> = {
  1: 'Simple',
  2: 'Standard',
  3: 'Detailed',
  4: 'Internal',
};

/** §5.0 default: collapsed, but not so collapsed that the surface is useless. */
export const DEFAULT_DISCLOSURE_LEVEL: DisclosureLevel = 2;

/** One piece of information and the level at which it first appears. */
export interface DisclosureField<T = string> {
  readonly id: T;
  readonly minLevel: DisclosureLevel;
}

/**
 * The fields visible at a level.
 *
 * Filters by "first appears at or below", never by equality — equality is
 * exactly the mistake that turns disclosure into a filter.
 */
export function visibleAt<T>(
  fields: readonly DisclosureField<T>[],
  level: DisclosureLevel,
): readonly DisclosureField<T>[] {
  return fields.filter((field) => field.minLevel <= level);
}

export function visibleIds<T>(
  fields: readonly DisclosureField<T>[],
  level: DisclosureLevel,
): readonly T[] {
  return visibleAt(fields, level).map((field) => field.id);
}

/**
 * True when every level is a superset of the one below it.
 *
 * The falsifiable form of "no information is removed between levels". A field
 * set that fails this has a level which hides something a lower level showed,
 * which is the one thing `HOE-A08` forbids outright.
 */
export function isAdditive<T>(fields: readonly DisclosureField<T>[]): boolean {
  for (let level = 2; level <= 4; level += 1) {
    const lower = new Set(visibleIds(fields, (level - 1) as DisclosureLevel));
    const higher = new Set(visibleIds(fields, level as DisclosureLevel));
    for (const id of lower) {
      if (!higher.has(id)) return false;
    }
  }
  return true;
}

/** Clamps an arbitrary stored value to a real level, without guessing upward. */
export function coerceLevel(value: unknown): DisclosureLevel {
  if (value === 1 || value === 2 || value === 3 || value === 4) return value;
  // An unreadable preference falls back to the default rather than to 4:
  // revealing internal detail because a setting was corrupt is a disclosure
  // decision nobody made.
  return DEFAULT_DISCLOSURE_LEVEL;
}
