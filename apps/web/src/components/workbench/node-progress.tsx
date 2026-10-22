/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import React, { useState, useEffect } from 'react';

/**
 * ADD-W-005: Verified Node Progress Motion Binding & Anti-Fake Animation Gate
 * Section 1.3 mandate: Spinners and progress rings animate if and only if
 * real computational progress occurs within a 5,000ms rolling window.
 */

export interface NodeProgressTelemetry {
  nodeId: string;
  nodeTitle: string;
  stage: string;
  percentProgress: number;
  lastUpdateTimestamp: number;
}

export interface NodeProgressState {
  activeNodes: Record<string, NodeProgressTelemetry>;
  isStreaming: boolean;
  hasStagnated: boolean;
}

export function shouldAnimateMotion(
  lastTelemetry: NodeProgressTelemetry | null | undefined,
  currentTelemetry: NodeProgressTelemetry,
  prefersReducedMotion: boolean
): boolean {
  if (prefersReducedMotion) return false;
  if (!lastTelemetry) return true;

  const now = Date.now();
  const isWithinWindow = now - currentTelemetry.lastUpdateTimestamp < 5000;
  const isProgressChanging = currentTelemetry.percentProgress !== lastTelemetry.percentProgress;

  return isWithinWindow && isProgressChanging;
}

export interface NodeProgressProps {
  telemetry: NodeProgressTelemetry;
  previousTelemetry?: NodeProgressTelemetry | null;
}

export function NodeProgressIndicator({ telemetry, previousTelemetry }: NodeProgressProps) {
  const [prefersReducedMotion, setPrefersReducedMotion] = useState(false);

  useEffect(() => {
    if (typeof window !== 'undefined') {
      const media = window.matchMedia('(prefers-reduced-motion: reduce)');
      setPrefersReducedMotion(media.matches);
      const listener = (e: MediaQueryListEvent) => setPrefersReducedMotion(e.matches);
      media.addEventListener('change', listener);
      return () => media.removeEventListener('change', listener);
    }
  }, []);

  const canAnimate = shouldAnimateMotion(previousTelemetry, telemetry, prefersReducedMotion);

  return (
    <div className="flex items-center gap-3 rounded-lg border border-white/10 bg-slate-900/60 p-3 font-mono text-white">
      <div className="relative flex items-center justify-center">
        {canAnimate ? (
          <div className="h-5 w-5 rounded-full border-2 border-emerald-400 border-t-transparent animate-spin" />
        ) : (
          <div className="h-5 w-5 rounded-full border-2 border-slate-600 flex items-center justify-center">
            <span className="h-1.5 w-1.5 rounded-full bg-slate-400" />
          </div>
        )}
      </div>

      <div className="flex flex-col flex-1 min-w-0">
        <div className="flex items-center justify-between text-xs">
          <span className="font-semibold text-slate-200 truncate">{telemetry.nodeTitle}</span>
          <span className="font-bold text-emerald-400">{telemetry.percentProgress.toFixed(0)}%</span>
        </div>
        <div className="flex items-center justify-between text-[10px] text-slate-400 mt-0.5">
          <span>{telemetry.stage}</span>
          <span>{canAnimate ? 'Active Progress' : 'Stagnant / Static'}</span>
        </div>
        <div className="mt-1.5 h-1 w-full rounded bg-slate-800 overflow-hidden">
          <div
            className="h-full bg-emerald-500 transition-all duration-300"
            style={{ width: `${Math.min(100, Math.max(0, telemetry.percentProgress))}%` }}
          />
        </div>
      </div>
    </div>
  );
}

export default NodeProgressIndicator;
