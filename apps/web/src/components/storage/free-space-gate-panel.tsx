/* SPDX-License-Identifier: AGPL-3.0-only */
"use client";

import React, { FC } from "react";
import { HardDrive, AlertTriangle, CheckCircle, Trash2 } from "lucide-react";

export interface StorageThresholdStatus {
  availableBytes: number;
  totalBytes: number;
  isGateTripped: boolean;
  thresholdBytes: number;
}

export interface FreeSpaceGatePanelProps {
  status: StorageThresholdStatus;
  onTriggerCleanup?: () => void;
}

export const FreeSpaceGatePanel: FC<FreeSpaceGatePanelProps> = ({
  status,
  onTriggerCleanup,
}) => {
  const formatMB = (bytes: number) => (bytes / (1024 * 1024)).toFixed(1);
  const percentUsed = Math.min(100, Math.round(((status.totalBytes - status.availableBytes) / (status.totalBytes || 1)) * 100));

  return (
    <div className="sg-free-space-gate-panel bg-slate-950/70 border border-slate-800 rounded-lg p-4 flex flex-col gap-3">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <HardDrive className="w-4 h-4 text-emerald-400" />
          <span className="font-semibold text-sm text-slate-200">Disk Free-Space Gate</span>
        </div>
        {status.isGateTripped ? (
          <span className="px-2 py-0.5 rounded bg-rose-950 border border-rose-800 text-rose-300 text-xs font-semibold flex items-center gap-1">
            <AlertTriangle className="w-3.5 h-3.5" /> GATE TRIPPED (&lt;500MB)
          </span>
        ) : (
          <span className="px-2 py-0.5 rounded bg-emerald-950/40 border border-emerald-800/40 text-emerald-300 text-xs font-semibold flex items-center gap-1">
            <CheckCircle className="w-3.5 h-3.5" /> HEALTHY
          </span>
        )}
      </div>

      <div className="w-full bg-slate-900 rounded-full h-2 overflow-hidden border border-slate-800">
        <div
          className={`h-full transition-all ${status.isGateTripped ? "bg-rose-500" : "bg-emerald-500"}`}
          style={{ width: `${percentUsed}%` }}
        />
      </div>

      <div className="flex items-center justify-between text-xs text-slate-400 font-mono">
        <span>Available: {formatMB(status.availableBytes)} MB</span>
        <span>Threshold: {formatMB(status.thresholdBytes)} MB</span>
        <span>Total: {formatMB(status.totalBytes)} MB</span>
      </div>

      {status.isGateTripped && (
        <div className="flex items-center justify-between p-2.5 bg-rose-950/30 border border-rose-900/40 rounded mt-1">
          <span className="text-xs text-rose-300">Run mutations blocked to prevent disk exhaustion.</span>
          <button
            type="button"
            onClick={onTriggerCleanup}
            className="flex items-center gap-1 px-2.5 py-1 bg-rose-900 hover:bg-rose-800 text-white rounded text-xs"
          >
            <Trash2 className="w-3 h-3" />
            <span>Prune CAS</span>
          </button>
        </div>
      )}
    </div>
  );
};
