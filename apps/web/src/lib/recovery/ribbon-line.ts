/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * The one-line status the recovery ribbon carries.
 *
 * `SUP.UX.RECOVERY-RIBBON` asks for a single line covering continuity,
 * free space and authority — the three things that decide whether the operator
 * can trust what they are about to do. Competitors surface a crash dialog and
 * stop there, which answers "did it restart" and leaves "can it write" and
 * "under whose authority" for the operator to discover by failing.
 *
 * One line rather than three chips because the ribbon appears at the moment of
 * least attention. Three independent indicators are three things to read; one
 * sentence is one.
 *
 * Every part is tri-state. `unknown` is never folded into `ok`: a free-space
 * reading the surface could not obtain is not free space, and a ribbon that
 * said "storage fine" because the storage route was unreachable would be lying
 * about the one thing it exists to report.
 */

export type PartState = 'ok' | 'attention' | 'unknown';

export interface RibbonPart {
  readonly state: PartState;
  readonly text: string;
}

export interface RibbonInputs {
  /** The engine's continuity report. Null when it could not be asked. */
  readonly continuity: { repaired: boolean; failed: boolean; notice: string | null } | null;
  /** Fraction of the storage ceiling in use, or null when unmeasured. */
  readonly storageFractionUsed: number | null;
  /** Whether an authority document resolved, or null when unknown. */
  readonly authority: { resolved: boolean; source: string | null } | null;
}

/**
 * `SUP.STOR.FREE-SPACE-GATE`'s warn threshold, mirrored for display only.
 *
 * The gate itself lives in the engine and decides refusals. This is the point
 * at which the ribbon starts saying so — a surface that stayed quiet until the
 * engine refused would make the refusal look sudden.
 */
export const STORAGE_WARN_FRACTION = 0.85;

export function continuityPart(input: RibbonInputs['continuity']): RibbonPart {
  if (input === null) {
    return { state: 'unknown', text: 'continuity unknown' };
  }
  if (input.failed) {
    return { state: 'attention', text: 'recovery did not run' };
  }
  if (input.repaired) {
    return { state: 'attention', text: 'work restored on startup' };
  }
  return { state: 'ok', text: 'continuity clean' };
}

export function storagePart(fractionUsed: number | null): RibbonPart {
  if (fractionUsed === null) {
    // Unmeasured is not unlimited, and an undeclared ceiling is not a large one.
    return { state: 'unknown', text: 'free space unmeasured' };
  }
  const percent = Math.round(fractionUsed * 100);
  if (fractionUsed >= STORAGE_WARN_FRACTION) {
    return { state: 'attention', text: `storage ${percent}% of ceiling` };
  }
  return { state: 'ok', text: `storage ${percent}% of ceiling` };
}

export function authorityPart(input: RibbonInputs['authority']): RibbonPart {
  if (input === null) {
    return { state: 'unknown', text: 'authority unknown' };
  }
  if (!input.resolved) {
    // Absence of a grant is never permission; the ribbon says so rather than
    // rendering an unauthenticated session as an ordinary one.
    return { state: 'attention', text: 'no authority document resolved' };
  }
  return { state: 'ok', text: `authority ${input.source ?? 'resolved'}` };
}

export interface RibbonLine {
  readonly parts: readonly RibbonPart[];
  readonly text: string;
  /** The worst state across the parts — what the ribbon's styling follows. */
  readonly state: PartState;
  /** True when nothing needs saying and the ribbon should stay silent. */
  readonly silent: boolean;
}

/**
 * Composes the line.
 *
 * The overall state is the worst part, with `attention` outranking `unknown`:
 * a known problem is more actionable than an unread indicator, and ordering it
 * the other way would let one unmeasured reading mute a real warning.
 *
 * `silent` requires every part to be `ok`. An all-clear ribbon is not worth the
 * operator's attention, but a single `unknown` is — it means the surface cannot
 * vouch for something it normally would.
 */
export function ribbonLine(inputs: RibbonInputs): RibbonLine {
  const parts = [
    continuityPart(inputs.continuity),
    storagePart(inputs.storageFractionUsed),
    authorityPart(inputs.authority),
  ];

  const state: PartState = parts.some((part) => part.state === 'attention')
    ? 'attention'
    : parts.some((part) => part.state === 'unknown')
      ? 'unknown'
      : 'ok';

  return {
    parts,
    text: parts.map((part) => part.text).join(' · '),
    state,
    silent: state === 'ok',
  };
}
