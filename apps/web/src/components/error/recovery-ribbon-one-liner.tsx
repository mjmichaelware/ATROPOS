/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import React from 'react';
import { composeRecoveryRibbon, HealthVectorState } from '../../lib/recovery/recovery-ribbon-composer';

export function RecoveryRibbonOneLiner({
  continuityNotice,
  isRepaired = false,
  hasCrashFailure = false,
  storageFractionUsed,
  isAuthorityResolved = true,
}: {
  continuityNotice?: string;
  isRepaired?: boolean;
  hasCrashFailure?: boolean;
  storageFractionUsed?: number;
  isAuthorityResolved?: boolean;
}) {
  const ribbon = composeRecoveryRibbon(
    continuityNotice,
    isRepaired,
    hasCrashFailure,
    storageFractionUsed,
    isAuthorityResolved
  );

  if (!ribbon.shouldRender) return null;

  return (
    <div
      role="alert"
      className={`flex items-center justify-between px-4 py-2 text-xs font-mono border-b ${
        ribbon.overallState === HealthVectorState.ATTENTION
          ? 'bg-amber-950/80 border-amber-600 text-amber-200'
          : ribbon.overallState === HealthVectorState.UNKNOWN
          ? 'bg-slate-900 border-slate-700 text-slate-300'
          : 'bg-emerald-950/80 border-emerald-600 text-emerald-200'
      }`}
    >
      <span className="truncate">{ribbon.displayText}</span>
      {ribbon.attentionCount > 0 && (
        <span className="shrink-0 px-2 py-0.5 rounded bg-black/40 text-[10px] font-bold">
          {ribbon.attentionCount} {ribbon.attentionCount === 1 ? 'item needs' : 'items need'} attention
        </span>
      )}
    </div>
  );
}

export default RecoveryRibbonOneLiner;
