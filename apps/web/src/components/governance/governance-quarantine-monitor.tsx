/* SPDX-License-Identifier: AGPL-3.0-only */
"use client";

import React, { FC } from "react";
import { AlertOctagon, CheckCircle2, ShieldAlert } from "lucide-react";

export interface QuarantineRecord {
  entityId: string;
  entityType: "PROVIDER" | "TOOL" | "PROPOSAL";
  quarantinedAt: string;
  reason: string;
  autoReleaseEpochMs: number;
}

export interface GovernanceQuarantineMonitorProps {
  quarantinedEntities: QuarantineRecord[];
  onRelease?: (entityId: string) => void;
}

export const GovernanceQuarantineMonitor: FC<GovernanceQuarantineMonitorProps> = ({
  quarantinedEntities,
  onRelease,
}) => {
  return (
    <div className="sg-governance-quarantine-monitor flex flex-col bg-slate-950/70 border border-slate-800 rounded-lg p-4">
      <div className="flex items-center gap-2 pb-3 border-b border-slate-800">
        <ShieldAlert className="w-4 h-4 text-rose-400" />
        <span className="font-semibold text-sm text-slate-200">Governance Quarantine & Safety Barrier</span>
      </div>

      {quarantinedEntities.length === 0 ? (
        <div className="flex items-center gap-2 p-4 text-xs text-emerald-400 bg-emerald-950/20 border border-emerald-800/40 rounded mt-3">
          <CheckCircle2 className="w-4 h-4" />
          <span>Zero quarantined entities. All providers, tools, and proposals operate within safety bounds.</span>
        </div>
      ) : (
        <div className="divide-y divide-slate-800 mt-3">
          {quarantinedEntities.map((record) => (
            <div key={record.entityId} className="flex items-center justify-between py-2.5">
              <div className="flex items-center gap-2.5">
                <AlertOctagon className="w-4 h-4 text-rose-400 shrink-0" />
                <div>
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-xs font-semibold text-rose-300">{record.entityId}</span>
                    <span className="text-[10px] px-1.5 py-0.5 rounded bg-slate-800 text-slate-400">
                      {record.entityType}
                    </span>
                  </div>
                  <p className="text-xs text-slate-400 mt-0.5">{record.reason}</p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => onRelease?.(record.entityId)}
                className="px-2.5 py-1 bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs rounded border border-slate-700"
              >
                Release
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
