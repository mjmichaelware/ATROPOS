/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import React, { useEffect, useState } from 'react';

/**
 * ADD-W-018: Factory Live Preview and Symbol Diagnostics Strip
 * Surfaces real-time diagnostics before committing diffs to disk.
 */

export interface PreviewDiagnosticsState {
  projectId: string;
  status: 'running' | 'paused' | 'completed' | 'error';
  patchFingerprint?: string;
  impactedSymbols: string[];
  verificationStatus?: 'passed' | 'failed' | 'pending';
  diagnosticsList: string[];
  lastUpdateTimestamp: string;
  isInsideTerritory?: boolean;
}

export interface FactoryLivePreviewProps {
  projectId: string;
  onRollbackRequest?: (fingerprint: string) => Promise<void>;
}

export function FactoryLivePreview({ projectId, onRollbackRequest }: FactoryLivePreviewProps) {
  const [diagnostics, setDiagnostics] = useState<PreviewDiagnosticsState>({
    projectId,
    status: 'running',
    patchFingerprint: 'sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
    impactedSymbols: [],
    verificationStatus: 'pending',
    diagnosticsList: [],
    lastUpdateTimestamp: new Date().toISOString(),
    isInsideTerritory: true,
  });
  const [isRollingBack, setIsRollingBack] = useState(false);
  const [pollError, setPollError] = useState<string | null>(null);

  useEffect(() => {
    let isCancelled = false;

    async function pollPreview() {
      try {
        const res = await fetch(`/v1/preview?projectId=${encodeURIComponent(projectId)}`);
        if (!res.ok) {
          throw new Error(`HTTP ${res.status}`);
        }
        const data = await res.json();
        if (!isCancelled) {
          setDiagnostics({
            projectId: data.projectId || projectId,
            status: data.status || 'running',
            patchFingerprint: data.patchFingerprint,
            impactedSymbols: data.impactedSymbols || [],
            verificationStatus: data.verificationStatus || 'pending',
            diagnosticsList: data.diagnosticsList || [],
            lastUpdateTimestamp: data.lastUpdateTimestamp || new Date().toISOString(),
            isInsideTerritory: data.isInsideTerritory ?? true,
          });
          setPollError(null);
        }
      } catch (err: unknown) {
        if (!isCancelled) {
          setPollError((err as Error).message || 'Failed to fetch preview diagnostics');
        }
      }
    }

    void pollPreview();
    const interval = setInterval(pollPreview, 3000);

    return () => {
      isCancelled = true;
      clearInterval(interval);
    };
  }, [projectId]);

  const handleRollback = async () => {
    if (!diagnostics.patchFingerprint || !onRollbackRequest) return;
    setIsRollingBack(true);
    try {
      await onRollbackRequest(diagnostics.patchFingerprint);
    } finally {
      setIsRollingBack(false);
    }
  };

  const isTerritoryOk = diagnostics.isInsideTerritory ?? true;

  return (
    <div className="flex flex-col gap-4 rounded-xl border border-white/10 bg-[#07100D] p-5 text-white font-mono shadow-2xl">
      {/* Header and Telemetry Vectors */}
      <div className="flex flex-wrap items-center justify-between border-b border-white/10 pb-4 gap-2">
        <div className="flex items-center gap-3">
          <h3 className="text-base font-bold text-emerald-400">FACTORY LIVE PREVIEW</h3>
          <span className="text-xs text-slate-400">Project: {projectId}</span>
        </div>
        <div className="flex items-center gap-2">
          {/* Territory Gating Indicator */}
          <span
            className={`px-2.5 py-1 rounded text-xs font-bold border ${
              isTerritoryOk
                ? 'bg-emerald-950/80 border-emerald-600 text-emerald-300'
                : 'bg-amber-950/80 border-amber-600 text-amber-300 animate-pulse'
            }`}
          >
            {isTerritoryOk ? 'Inside territory' : 'Outside territory'}
          </span>

          {/* Verification Status Token */}
          <span
            className={`px-2.5 py-1 rounded text-xs font-bold border ${
              diagnostics.verificationStatus === 'passed'
                ? 'bg-emerald-950 border-emerald-600 text-emerald-300'
                : diagnostics.verificationStatus === 'failed'
                ? 'bg-red-950 border-red-600 text-red-300'
                : 'bg-slate-900 border-slate-700 text-slate-300'
            }`}
          >
            {diagnostics.verificationStatus?.toUpperCase() || 'PENDING'}
          </span>
        </div>
      </div>

      {pollError && (
        <div className="p-2.5 rounded bg-red-950/30 border border-red-800 text-xs text-red-300">
          Diagnostics feed offline: {pollError}
        </div>
      )}

      {/* Diagnostics Strip: Patch Fingerprint */}
      <div className="flex flex-col gap-1 rounded bg-slate-900/60 p-3 border border-white/5">
        <span className="text-[10px] font-bold tracking-wider text-slate-400">PATCH FINGERPRINT (SHA-256)</span>
        <span className="text-xs text-slate-200 select-all break-all">
          {diagnostics.patchFingerprint || 'None generated'}
        </span>
      </div>

      {/* Impacted Code Symbols */}
      <div className="flex flex-col gap-2">
        <span className="text-[10px] font-bold tracking-wider text-slate-400">
          IMPACTED SYMBOLS ({diagnostics.impactedSymbols.length})
        </span>
        {diagnostics.impactedSymbols.length === 0 ? (
          <span className="text-xs text-slate-500 italic">No impacted symbols detected</span>
        ) : (
          <div className="flex flex-wrap gap-1.5">
            {diagnostics.impactedSymbols.map((sym) => (
              <span
                key={sym}
                className="px-2 py-0.5 rounded bg-slate-800 border border-white/10 text-xs text-emerald-300"
              >
                {sym}
              </span>
            ))}
          </div>
        )}
      </div>

      {/* Compiler Warnings / Diagnostics */}
      <div className="flex flex-col gap-2">
        <span className="text-[10px] font-bold tracking-wider text-slate-400">
          ACTIVE COMPILER DIAGNOSTICS ({diagnostics.diagnosticsList.length})
        </span>
        {diagnostics.diagnosticsList.length === 0 ? (
          <span className="text-xs text-slate-500 italic">Zero compiler warnings or errors</span>
        ) : (
          <div className="flex flex-col gap-1 max-h-36 overflow-y-auto">
            {diagnostics.diagnosticsList.map((diag, idx) => (
              <div key={idx} className="p-2 rounded bg-amber-950/20 border border-amber-800/40 text-xs text-amber-200">
                {diag}
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Governed Rollback Action */}
      {diagnostics.verificationStatus === 'failed' && onRollbackRequest && (
        <div className="mt-2 flex items-center justify-between border-t border-white/10 pt-4">
          <span className="text-xs text-red-300">Verification failed. Operator governed rollback available.</span>
          <button
            onClick={handleRollback}
            disabled={isRollingBack}
            className="px-3 py-1.5 rounded bg-red-800 hover:bg-red-700 disabled:opacity-50 text-xs font-bold text-white transition-all"
          >
            {isRollingBack ? 'Executing Rollback...' : 'Rollback Patch'}
          </button>
        </div>
      )}
    </div>
  );
}
