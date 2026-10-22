/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ADD-W-008: Recovery Ribbon Composite One-Liner Engine
 * Section 1.4 mandate: Composes continuity, storage, and authority health
 * into a single non-collapsing scannable line.
 */

export enum HealthVectorState {
  OK = 'OK',
  ATTENTION = 'ATTENTION',
  UNKNOWN = 'UNKNOWN',
}

export interface CompositeRibbonLine {
  overallState: HealthVectorState;
  displayText: string;
  shouldRender: boolean;
  attentionCount: number;
}

export function composeRecoveryRibbon(
  continuityNotice: string | null | undefined,
  isRepaired: boolean,
  hasCrashFailure: boolean,
  storageFractionUsed: number | null | undefined,
  isAuthorityResolved: boolean
): CompositeRibbonLine {
  let attentionCount = 0;
  let hasUnknown = false;

  // 1. Continuity Vector
  let continuityText = '';
  if (hasCrashFailure) {
    attentionCount++;
    continuityText = 'Crash recovery failed';
  } else if (isRepaired || continuityNotice) {
    continuityText = continuityNotice || 'Work restored on startup';
  } else {
    continuityText = 'Continuity clean';
  }

  // 2. Storage Vector
  let storageText = '';
  if (storageFractionUsed == null) {
    hasUnknown = true;
    storageText = 'Storage state unknown';
  } else {
    const pct = Math.round(storageFractionUsed * 100);
    if (storageFractionUsed >= 0.9) {
      attentionCount++;
      storageText = `Storage critical (${pct}% of ceiling)`;
    } else if (storageFractionUsed >= 0.75) {
      attentionCount++;
      storageText = `Storage ${pct}% of ceiling`;
    } else {
      storageText = `Storage nominal (${pct}%)`;
    }
  }

  // 3. Authority Vector
  let authorityText = '';
  if (isAuthorityResolved) {
    authorityText = 'Authority resolved';
  } else {
    attentionCount++;
    authorityText = 'Authority unverified';
  }

  const overallState = attentionCount > 0
    ? HealthVectorState.ATTENTION
    : hasUnknown
    ? HealthVectorState.UNKNOWN
    : HealthVectorState.OK;

  const displayText = `${continuityText} · ${storageText} · ${authorityText}`;
  const shouldRender = overallState !== HealthVectorState.OK || isRepaired;

  return {
    overallState,
    displayText,
    shouldRender,
    attentionCount,
  };
}
