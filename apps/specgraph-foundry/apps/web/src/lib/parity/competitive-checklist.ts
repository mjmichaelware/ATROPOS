/* SPDX-License-Identifier: AGPL-3.0-only */

import { DELTA_REGISTER, outstandingGaps, type Surface } from './delta-register';

/**
 * The capability checklist, and the rule that a claim needs evidence.
 *
 * Competitive checklists are usually marketing artifacts: a grid of ticks where
 * the tick means someone believed it on the day they made the slide. This one
 * is built so a tick is falsifiable — every `shipped` entry names the file that
 * implements it, and a claim with no owner is downgraded to `claimed` with the
 * reason stated.
 *
 * The downgrade target is deliberately not `absent`. A capability that exists
 * but cannot show where it lives is unproven, not missing, and reporting it as
 * missing is a fabrication in the other direction — the same asymmetry
 * `P20-G06` applies to VERIFIED.
 */

export type CapabilityState =
  /** Implemented, with an owner a reader can open. */
  | 'shipped'
  /** Asserted, no owner named. Shown as unproven. */
  | 'claimed'
  /** Known not to exist here. */
  | 'absent';

export interface Capability {
  readonly id: string;
  readonly title: string;
  /** Why this belongs on the list at all. */
  readonly matters: string;
  readonly state: CapabilityState;
  /** The file that implements it. Null downgrades the claim. */
  readonly owner: string | null;
  readonly surfaces: readonly Surface[];
}

export interface ChecklistRow extends Capability {
  /** What the surface may actually display. */
  readonly renderedState: CapabilityState;
  readonly downgradeReason: string | null;
}

/**
 * What ATROPOS does that a general coding assistant does not.
 *
 * Every entry is here because it is enforced somewhere in this repository, and
 * the owner column is what makes that checkable.
 */
export const CAPABILITIES: readonly Capability[] = [
  {
    id: 'refuses-unproven-verified',
    title: 'A completion claim without evidence is refused, not decorated',
    matters:
      'The failure mode of an autonomous system is not doing nothing — it is reporting success it cannot support.',
    state: 'shipped',
    owner: 'apps/web/src/lib/evidence/affordance.ts',
    surfaces: ['cli', 'tui', 'web'],
  },
  {
    id: 'unmeasured-is-not-zero',
    title: 'An unmeasured metric reads "not measured", never 0%',
    matters:
      'A false-VERIFIED rate defaulting to zero is the most flattering possible lie a governance dashboard can tell.',
    state: 'shipped',
    owner: 'apps/web/src/lib/governance/client.ts',
    surfaces: ['web'],
  },
  {
    id: 'territory-bounded-export',
    title: 'Artifacts land where the operator chose, or the export refuses',
    matters:
      'Hard-coded landing zones write into whichever directory the packager picked, which on a phone is nowhere the operator can find.',
    state: 'shipped',
    owner: 'src/main/kotlin/atropos/core/artifact/export/ArtifactLanding.kt',
    surfaces: ['cli', 'web', 'android'],
  },
  {
    id: 'resume-is-primary',
    title: 'The checkpoint resumes; there is no equally-weighted "start over"',
    matters:
      'A surface whose most prominent control discards long-horizon work teaches the operator that continuing is the awkward path.',
    state: 'shipped',
    owner: 'src/main/kotlin/atropos/core/checkpoint/CheckpointSummary.kt',
    surfaces: ['cli', 'tui', 'web'],
  },
  {
    id: 'read-only-bridge',
    title: 'The Web surface cannot originate an action',
    matters:
      'The one write records a human decision on something policy already stopped, and it refuses an unattributed decision.',
    state: 'shipped',
    owner: 'src/main/kotlin/atropos/bridge/BridgeRoutes.kt',
    surfaces: ['web'],
  },
  {
    id: 'single-activity-stream',
    title: 'One activity stream over existing evidence, not a second event system',
    matters:
      'Two event systems disagree eventually, and the disagreement is invisible because each is internally consistent.',
    state: 'shipped',
    owner: 'src/main/kotlin/atropos/core/monitor/ActivityEvent.kt',
    surfaces: ['cli', 'tui', 'web'],
  },
  {
    id: 'disclosure-only-adds',
    title: 'Expanding detail never hides what the previous level showed',
    matters:
      'A disclosure control that filters instead of adding will hide the row the operator was reading.',
    state: 'shipped',
    owner: 'apps/web/src/lib/disclosure/levels.ts',
    surfaces: ['tui', 'web'],
  },
  {
    id: 'self-amending-governance',
    title: 'The system proposes changes to its own authority, under observation',
    matters:
      'Phase 20 requires a predeclared metric, a rollback and an observation period before a change stands.',
    // The gate and the metric exist; nothing writes to the durable ledger yet,
    // so this is not claimed as shipped.
    state: 'claimed',
    owner: null,
    surfaces: ['web'],
  },
];

/**
 * The checklist as it may be rendered.
 *
 * A `shipped` claim with no owner is downgraded here rather than at the call
 * site, so no surface can render the un-downgraded form.
 */
export function checklist(
  capabilities: readonly Capability[] = CAPABILITIES,
): readonly ChecklistRow[] {
  return capabilities.map((capability) => {
    if (capability.state === 'shipped' && !capability.owner) {
      return {
        ...capability,
        renderedState: 'claimed',
        downgradeReason:
          'Claimed as shipped but names no implementation, so it is shown as unproven rather than shipped.',
      };
    }
    return { ...capability, renderedState: capability.state, downgradeReason: null };
  });
}

export interface ChecklistSummary {
  readonly total: number;
  readonly shipped: number;
  readonly claimed: number;
  readonly absent: number;
  /** Open deltas carried from the register, so one number does not hide them. */
  readonly outstandingGaps: number;
}

/**
 * Counts by rendered state, never by asserted state.
 *
 * The gap count comes from the delta register rather than being recomputed, so
 * the checklist and the register cannot report different numbers of open items.
 */
export function summarise(
  capabilities: readonly Capability[] = CAPABILITIES,
): ChecklistSummary {
  const rows = checklist(capabilities);
  return {
    total: rows.length,
    shipped: rows.filter((row) => row.renderedState === 'shipped').length,
    claimed: rows.filter((row) => row.renderedState === 'claimed').length,
    absent: rows.filter((row) => row.renderedState === 'absent').length,
    outstandingGaps: outstandingGaps(DELTA_REGISTER).length,
  };
}
