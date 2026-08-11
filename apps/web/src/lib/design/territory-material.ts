/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * Territory and attestation expressed as visual weight.
 *
 * `HOE-E01`: "Out-of-territory recedes; in-territory holds accent; no parallel
 * visual system." `HOE-E02`: "Valid envelope sharpens; drift softens; certainty
 * readable without numbers."
 *
 * Both atoms describe the same discipline from two directions — emphasis must
 * be *earned by state*, never applied for decoration. The failure they prevent
 * is a surface where everything is equally bright, so the operator cannot see
 * at a glance that the thing they are about to act on lies outside the
 * territory the run was granted.
 *
 * This module returns tokens, never colours or CSS. A file that emitted styles
 * would become a second design system beside `DesignTokens`, which `HOE-E01`
 * forbids in the same sentence; a file that returns named state lets one
 * stylesheet own how each state looks.
 */

export type TerritoryStanding = 'in-territory' | 'out-of-territory' | 'unknown';

export type AttestationStanding = 'attested' | 'drifted' | 'unattested';

export interface MaterialTokens {
  /** How present the element is. Recedes when it is not the operator's ground. */
  readonly emphasis: 'accent' | 'normal' | 'receded';
  /** How sharp the type reads. Certainty without a number. */
  readonly focus: 'sharp' | 'normal' | 'soft';
  /**
   * The non-colour sentence. §E forbids colour as the only channel, and a
   * desaturated card with no words is exactly colour-only.
   */
  readonly note: string | null;
}

/**
 * Where a path stands relative to the granted territory.
 *
 * An empty grant returns `unknown`, never `in-territory`. Absence of a grant is
 * not permission, and rendering an ungranted path at full accent would tell the
 * operator it was cleared when nothing cleared it.
 */
export function territoryStanding(
  path: string,
  granted: readonly string[],
): TerritoryStanding {
  if (granted.length === 0) return 'unknown';
  const permitted = granted.some(
    (allowed) => path === allowed || path.startsWith(`${allowed}/`),
  );
  return permitted ? 'in-territory' : 'out-of-territory';
}

/**
 * Combines both standings into the tokens a surface renders.
 *
 * Territory outranks attestation: a perfectly attested action that lies outside
 * the granted paths is the more dangerous of the two, so it recedes regardless
 * of how well attested it is. Sharpening it would reward the wrong property.
 */
export function materialFor(
  territory: TerritoryStanding,
  attestation: AttestationStanding,
): MaterialTokens {
  if (territory === 'out-of-territory') {
    return {
      emphasis: 'receded',
      focus: 'soft',
      note: 'Outside the granted territory for this run.',
    };
  }
  if (territory === 'unknown') {
    return {
      emphasis: 'receded',
      focus: 'soft',
      note: 'No territory was granted, so this is not cleared — only unclassified.',
    };
  }
  switch (attestation) {
    case 'attested':
      return { emphasis: 'accent', focus: 'sharp', note: null };
    case 'drifted':
      return {
        emphasis: 'normal',
        focus: 'soft',
        note: 'Context attestation drifted since this was recorded.',
      };
    case 'unattested':
      return {
        emphasis: 'normal',
        focus: 'normal',
        note: 'Not attested.',
      };
  }
}

/**
 * `HOE-E07`: "Planning/Working/Review/Blocked drive tokens; never decorative
 * mode switch."
 *
 * The theme accent is a function of the status vocabulary and nothing else.
 * A status this map does not know returns `neutral` rather than a guess — a
 * surface that rethemed on an unrecognised status would be changing its
 * appearance for a reason it could not explain.
 */
export function accentForStatus(term: string): string {
  switch (term) {
    case 'planning':
      return 'planning';
    case 'working':
      return 'working';
    case 'review-required':
      return 'review';
    case 'blocked':
    case 'failed':
      return 'blocked';
    case 'completed':
      return 'complete';
    case 'idle':
    case 'waiting':
    case 'cancelled':
      return 'quiet';
    default:
      return 'neutral';
  }
}
