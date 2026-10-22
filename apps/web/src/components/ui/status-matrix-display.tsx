/* SPDX-License-Identifier: AGPL-3.0-only */
"use client";

import React, { FC } from "react";
import { CanonicalStatus } from "@/lib/status-system";
import { StatusBadge } from "@/components/ui/status-badge";
import { ShieldCheck, ShieldAlert, CheckCircle, Code2, PlayCircle, AlertTriangle } from "lucide-react";

export enum FormalCompletionState {
  IMPLEMENTED = "IMPLEMENTED",
  COMPILED = "COMPILED",
  TESTED = "TESTED",
  VERIFIED = "VERIFIED",
  BLOCKED = "BLOCKED",
}

export interface StatusMatrixProps {
  runStatus: CanonicalStatus;
  completionState: FormalCompletionState;
  evidenceHashes: string[];
  compact?: boolean;
}

export const StatusMatrixDisplay: FC<StatusMatrixProps> = ({
  runStatus,
  completionState,
  evidenceHashes,
  compact = false,
}) => {
  const isVerifiedWithoutHashes =
    completionState === FormalCompletionState.VERIFIED &&
    (!evidenceHashes || evidenceHashes.length === 0);

  const getCompletionIcon = (state: FormalCompletionState) => {
    switch (state) {
      case FormalCompletionState.VERIFIED:
        return <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" aria-hidden="true" />;
      case FormalCompletionState.TESTED:
        return <CheckCircle className="w-3.5 h-3.5 text-blue-400" aria-hidden="true" />;
      case FormalCompletionState.COMPILED:
        return <PlayCircle className="w-3.5 h-3.5 text-cyan-400" aria-hidden="true" />;
      case FormalCompletionState.IMPLEMENTED:
        return <Code2 className="w-3.5 h-3.5 text-slate-300" aria-hidden="true" />;
      case FormalCompletionState.BLOCKED:
        return <ShieldAlert className="w-3.5 h-3.5 text-rose-400" aria-hidden="true" />;
    }
  };

  return (
    <div className={`sg-status-matrix-display inline-flex items-center gap-2 ${compact ? "text-xs" : "text-sm"}`}>
      {/* 1. Operational Run State (9 states) */}
      <StatusBadge status={runStatus} size={compact ? "sm" : "md"} />

      {/* 2. Formal Completion Claim (5 states) */}
      {isVerifiedWithoutHashes ? (
        <span
          role="alert"
          aria-label="Unverified Claim: Completion claimed without cryptographic proof hashes"
          className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded bg-amber-950/60 border border-amber-600/70 text-amber-300 font-mono text-xs font-semibold"
        >
          <AlertTriangle className="w-3.5 h-3.5 text-amber-400" aria-hidden="true" />
          <span>[CLAIM-UNVERIFIED]</span>
        </span>
      ) : (
        <span
          className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded bg-slate-900/70 border border-slate-700/60 text-slate-200 font-mono text-xs"
          aria-label={`Formal State: ${completionState}`}
        >
          {getCompletionIcon(completionState)}
          <span>{completionState}</span>
          {evidenceHashes && evidenceHashes.length > 0 && (
            <span className="text-[10px] text-emerald-400 font-normal">
              ({evidenceHashes.length} proof{evidenceHashes.length > 1 ? "s" : ""})
            </span>
          )}
        </span>
      )}
    </div>
  );
};
