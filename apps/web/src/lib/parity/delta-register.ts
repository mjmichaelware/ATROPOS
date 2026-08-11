/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * The register of deliberate differences between surfaces.
 *
 * Cross-surface parity does not mean identity. The CLI, the TUI, the Web app
 * and the Android build differ in real ways — a terminal has no pointer target
 * size, a phone has no persistent side rail — and pretending otherwise produces
 * either a Web app shaped like a terminal or a parity report that is quietly
 * ignored because everyone knows it is wrong.
 *
 * So a difference is legal here, and undocumented difference is not. Every
 * entry names what differs, on which surfaces, and why. An entry without a
 * reason is itself a finding: "we did it differently" with no justification is
 * how a surface drifts one commit at a time.
 *
 * This is a register, not a policy engine. It cannot approve a delta. It makes
 * the set of deltas countable and reviewable, which is the thing prose cannot
 * do.
 */

export type Surface = 'cli' | 'tui' | 'web' | 'android';

export const ALL_SURFACES: readonly Surface[] = ['cli', 'tui', 'web', 'android'];

export type DeltaKind =
  /** The surface cannot host the capability at all (no pointer, no filesystem). */
  | 'platform-impossible'
  /** Present, expressed differently to suit the surface. */
  | 'expressed-differently'
  /** Not built here yet. A gap, recorded as a gap. */
  | 'not-yet-built'
  /** Deliberately withheld on this surface. */
  | 'withheld-by-policy';

export interface Delta {
  readonly id: string;
  /** The capability this delta is about. */
  readonly capability: string;
  readonly kind: DeltaKind;
  /** Surfaces where the capability behaves as the reference does. */
  readonly parityOn: readonly Surface[];
  /** Surfaces where it does not. */
  readonly divergesOn: readonly Surface[];
  /** Why the divergence is acceptable. Blank makes the entry a finding. */
  readonly reason: string;
  /** Where the divergence is decided in code, so a reader can check it. */
  readonly owner: string | null;
}

export interface RegisterFinding {
  readonly id: string;
  readonly detail: string;
}

/**
 * The deltas this repository has accepted.
 *
 * Kept as data rather than documentation because a table in a markdown file
 * cannot be asserted against. Every entry here was a real decision.
 */
export const DELTA_REGISTER: readonly Delta[] = [
  {
    id: 'pointer-target-size',
    capability: 'Minimum 24px pointer targets (WCAG 2.5.8)',
    kind: 'platform-impossible',
    parityOn: ['web', 'android'],
    divergesOn: ['cli', 'tui'],
    reason:
      'A character-cell surface has no pointer and no CSS pixel; the equivalent guarantee there is keyboard reachability, which the command registry provides.',
    owner: 'apps/web/src/lib/a11y/conformance.ts',
  },
  {
    id: 'six-answers-push',
    capability: 'The six continuous answers update without being asked',
    kind: 'expressed-differently',
    parityOn: ['cli', 'tui', 'web'],
    divergesOn: ['android'],
    reason:
      'The CLI and TUI redraw in place, the Web app holds an EventSource on /v1/answers/stream; Android reads the same projection but on resume rather than continuously, because a background socket on a phone is a battery decision the operator did not make.',
    owner: 'apps/web/src/lib/engine/use-answers-stream.ts',
  },
  {
    id: 'bridge-write-surface',
    capability: 'Originating an action from the surface',
    kind: 'withheld-by-policy',
    parityOn: ['cli', 'tui'],
    divergesOn: ['web', 'android'],
    reason:
      'The bridge exposes exactly one write, and it only releases something policy already stopped. An open write surface on a loopback port is remote code execution against the operator’s own machine, since the CLI can reach /shell and !command.',
    owner: 'src/main/kotlin/atropos/bridge/BridgeRoutes.kt',
  },
  {
    id: 'specgraph-tenancy',
    capability: 'SpecGraph workspaces in the primary navigation',
    kind: 'withheld-by-policy',
    parityOn: [],
    divergesOn: ['web'],
    reason:
      '§1.3/§12.2: SpecGraph is an engine inside ATROPOS, not the product identity. It is retained in full under Developer Tools, which §2.10 hides by default.',
    owner: 'apps/web/src/lib/specgraph/tenancy.ts',
  },
  {
    id: 'governance-ledger-writer',
    capability: 'Phase 20 proposals visible on the governance surface',
    kind: 'not-yet-built',
    parityOn: [],
    divergesOn: ['cli', 'tui', 'web', 'android'],
    reason:
      'The durable proposal and amendment ledgers have no writer yet, so every surface truthfully renders "no proposals". This is recorded as a gap rather than filled with placeholder state.',
    owner: 'src/main/kotlin/atropos/core/phase20/ImprovementProposal.kt',
  },
];

/**
 * Deltas that fail the register's own rule.
 *
 * Three ways to be a finding: no reason given, no surface actually diverging
 * (an entry that records nothing), or a surface listed on both sides — which
 * means the entry cannot be true.
 */
export function registerFindings(
  register: readonly Delta[] = DELTA_REGISTER,
): readonly RegisterFinding[] {
  const findings: RegisterFinding[] = [];
  for (const delta of register) {
    if (delta.reason.trim() === '') {
      findings.push({ id: delta.id, detail: 'No reason recorded for this divergence.' });
    }
    if (delta.divergesOn.length === 0) {
      findings.push({ id: delta.id, detail: 'Records a delta but names no diverging surface.' });
    }
    const both = delta.parityOn.filter((surface) => delta.divergesOn.includes(surface));
    if (both.length > 0) {
      findings.push({
        id: delta.id,
        detail: `${both.join(', ')} listed as both at parity and diverging.`,
      });
    }
  }
  return findings;
}

/** Every delta touching a surface, whichever side of it the surface is on. */
export function deltasForSurface(
  surface: Surface,
  register: readonly Delta[] = DELTA_REGISTER,
): readonly Delta[] {
  return register.filter((delta) => delta.divergesOn.includes(surface));
}

/**
 * The gaps: capabilities not built on a surface, as distinct from ones the
 * surface cannot host or deliberately withholds.
 *
 * Separated because they are the only kind that represents outstanding work.
 * Collapsing them into a single "not at parity" count makes a platform
 * impossibility look like a backlog item forever.
 */
export function outstandingGaps(
  register: readonly Delta[] = DELTA_REGISTER,
): readonly Delta[] {
  return register.filter((delta) => delta.kind === 'not-yet-built');
}
