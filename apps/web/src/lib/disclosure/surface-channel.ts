/* SPDX-License-Identifier: AGPL-3.0-only */

import { coerceLevel, type DisclosureLevel } from './levels';

/**
 * Per-surface disclosure state.
 *
 * `HOE-E04`: "Each surface owns its disclosure state; engine stores full depth
 * once." The failure this prevents is subtle — if disclosure is stored as one
 * global preference, expanding Thinking on the web silently expands it in the
 * operator's terminal too. They are looking at two different things for two
 * different reasons, and a shared level makes one of those choices overwrite
 * the other.
 *
 * The engine is unaffected either way: it holds the full depth regardless, and
 * a level is only ever a filter applied on the way to a screen. That is why
 * this module stores levels and nothing else — a channel that also cached
 * content would become a second copy of the engine's record.
 */

export type SurfaceId = 'web' | 'cli' | 'android';

export type SurfaceChannels = Readonly<Record<SurfaceId, DisclosureLevel>>;

export const SURFACES: readonly SurfaceId[] = ['web', 'cli', 'android'];

export function defaultChannels(): SurfaceChannels {
  return Object.freeze({ web: 2, cli: 2, android: 2 }) as SurfaceChannels;
}

/**
 * Sets one surface's level, leaving every other surface untouched.
 *
 * The whole point of the atom, expressed as the only mutator: there is no
 * function here that sets them all, because a caller reaching for one would be
 * doing the thing `HOE-E04` forbids.
 */
export function setSurfaceLevel(
  channels: SurfaceChannels,
  surface: SurfaceId,
  level: DisclosureLevel,
): SurfaceChannels {
  return Object.freeze({ ...channels, [surface]: coerceLevel(level) }) as SurfaceChannels;
}

export function levelFor(channels: SurfaceChannels, surface: SurfaceId): DisclosureLevel {
  return coerceLevel(channels[surface]);
}

/** Restores channels from storage, coercing each independently. */
export function restoreChannels(raw: unknown): SurfaceChannels {
  const base = defaultChannels();
  if (raw === null || typeof raw !== 'object') return base;
  const candidate = raw as Record<string, unknown>;
  return Object.freeze(
    SURFACES.reduce<Record<SurfaceId, DisclosureLevel>>(
      (acc, surface) => {
        acc[surface] = coerceLevel(candidate[surface]);
        return acc;
      },
      { ...base },
    ),
  ) as SurfaceChannels;
}
