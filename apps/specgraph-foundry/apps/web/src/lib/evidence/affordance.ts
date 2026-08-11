/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * Whether a completion claim may be shown, and what its evidence affordance is.
 *
 * `HOE-A05` requires an evidence affordance on every completion claim.
 * `HOE-C08` adds the constraint that makes it honest: "Morph only when evidence
 * exists; bind motion to real state." A card that animates open to reveal
 * nothing has taught the operator that the gesture means nothing, and the next
 * time real evidence is there they will not look.
 *
 * `P20-G06` is the reason this is a gate rather than a decoration: "VERIFIED
 * without required evidence hashes → claim rejected." So a claim that says
 * verified and carries no evidence is not rendered as verified at all — it is
 * downgraded and the reason is stated. The surface refuses the claim rather
 * than dressing it.
 */

import { POSITIVE_COMPLETION_TERM } from '@atropos/web-contracts';

export interface EvidenceRef {
  readonly id: string;
  readonly kind: string;
  /** Where the operator can go to read it. Absent means it cannot be opened. */
  readonly location: string | null;
}

export interface CompletionClaim {
  /** One of the five P20-G09 completion terms. */
  readonly state: string;
  readonly evidence: readonly EvidenceRef[];
}

export interface EvidenceAffordance {
  /** The state the surface may actually render. */
  readonly renderedState: string;
  /** True when the expand gesture has something to reveal. */
  readonly canExpand: boolean;
  /** True when the surface should morph rather than jump. */
  readonly morph: boolean;
  /** Stated whenever the rendered state differs from the claimed one. */
  readonly downgradeReason: string | null;
  readonly evidenceCount: number;
}

/**
 * Decides what a claim is allowed to look like.
 *
 * The downgrade target is `tested`, not `blocked`: a claim with no evidence has
 * not been shown to have failed, only to be unproven, and reporting an unproven
 * claim as a failure is its own fabrication in the opposite direction.
 */
export function evidenceAffordance(claim: CompletionClaim): EvidenceAffordance {
  const count = claim.evidence.length;
  const claimsVerified = claim.state === POSITIVE_COMPLETION_TERM;

  if (claimsVerified && count === 0) {
    return {
      renderedState: 'tested',
      canExpand: false,
      morph: false,
      downgradeReason:
        'This claims verified but cites no evidence, so it is shown as unproven rather than verified.',
      evidenceCount: 0,
    };
  }

  return {
    renderedState: claim.state,
    canExpand: count > 0,
    // Motion is bound to real state: nothing to reveal, nothing to animate.
    morph: count > 0,
    downgradeReason: null,
    evidenceCount: count,
  };
}

/**
 * The Why / How / Evidence actions `HOE-A06` requires on a recommendation.
 *
 * An action whose source is missing is omitted rather than rendered disabled.
 * A greyed-out "Evidence" button still asserts that evidence exists somewhere;
 * absence says the true thing, which is that this recommendation cannot show
 * its reasoning.
 */
export function explainActions(input: {
  why: string | null;
  how: string | null;
  evidence: readonly EvidenceRef[];
}): ReadonlyArray<{ id: 'why' | 'how' | 'evidence'; label: string }> {
  const actions: Array<{ id: 'why' | 'how' | 'evidence'; label: string }> = [];
  if (input.why && input.why.trim() !== '') actions.push({ id: 'why', label: 'Why?' });
  if (input.how && input.how.trim() !== '') actions.push({ id: 'how', label: 'How?' });
  if (input.evidence.length > 0) actions.push({ id: 'evidence', label: 'Evidence' });
  return actions;
}

/** Evidence that can actually be opened, as opposed to merely named. */
export function openableEvidence(evidence: readonly EvidenceRef[]): readonly EvidenceRef[] {
  return evidence.filter((ref) => ref.location !== null && ref.location.trim() !== '');
}
