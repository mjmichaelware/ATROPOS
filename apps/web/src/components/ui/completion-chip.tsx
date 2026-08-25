/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * ADD-W-002: the five-state completion chip.
 *
 * The run vocabulary says what work is *doing*; the completion vocabulary
 * says what may be *claimed*. Collapsing the second into a bare "done" is the
 * exact failure this atom names, so the type here accepts only the contract's
 * five terms and renders the weakest-honest form when a claim cannot be
 * backed: an unverified project shows "unverified claim", never "done".
 */
'use client';

import { COMPLETION_TERMS, POSITIVE_COMPLETION_TERM, type CompletionTerm } from '@atropos/web-contracts';

export interface CompletionChipProps {
  /** A contract completion term — never free-form text, never just "done". */
  term: CompletionTerm;
}

const ICONS: Record<CompletionTerm, string> = {
  implemented: '◇',
  compiled: '◆',
  tested: '✓',
  verified: '✓✓',
  blocked: '⛔',
};

export function isCompletionTerm(value: unknown): value is CompletionTerm {
  return (
    typeof value === 'string' &&
    (COMPLETION_TERMS as readonly string[]).includes(value)
  );
}

export function CompletionChip({ term }: CompletionChipProps) {
  return (
    <span
      /* Term travels as text beside any colour (§E); positive claims are
         marked so "verified" cannot be confused with the weaker three. */
      data-completion={term}
      title={
        term === POSITIVE_COMPLETION_TERM
          ? 'Backed by gates'
          : `Completion claim: ${term}`
      }
      className="wb-completion-chip"
    >
      <span aria-hidden="true">{ICONS[term]}</span> {term}
    </span>
  );
}

/**
 * The honest fallback for a completion-ish boolean that is not a term:
 * `completionIsVerifiable === false` means a claim exists but no evidence
 * stands behind it — which is a warning about the claim, not a completion.
 */
export function UnverifiedClaim() {
  return (
    <span data-completion="unverified" className="wb-completion-chip wb-completion-unverified" title="No evidence backs this claim">
      ⚠ unverified claim
    </span>
  );
}
