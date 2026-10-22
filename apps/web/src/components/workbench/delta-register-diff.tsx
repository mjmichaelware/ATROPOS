/* SPDX-License-Identifier: AGPL-3.0-only */
"use client";

import React, { FC } from "react";

export interface DeltaRegisterDiffProps {
  rawDiff: string;
  filePath?: string;
  className?: string;
}

export const DeltaRegisterDiff: FC<DeltaRegisterDiffProps> = ({
  rawDiff,
  filePath,
  className = "",
}) => {
  const lines = rawDiff.split("\n");

  return (
    <div className={`sg-delta-register-diff font-mono text-xs rounded border border-slate-800 bg-black/80 overflow-hidden ${className}`}>
      {filePath && (
        <div className="bg-slate-900/80 px-3 py-1.5 border-b border-slate-800 text-[11px] text-slate-400 font-semibold flex items-center justify-between">
          <span>{filePath}</span>
          <span className="text-emerald-400 font-normal">Unified Diff</span>
        </div>
      )}
      <div className="overflow-x-auto p-2 space-y-0.5">
        {lines.map((line, idx) => {
          let lineClass = "text-slate-300";
          let bgClass = "bg-transparent";

          if (line.startsWith("+++") || line.startsWith("---")) {
            lineClass = "text-slate-400 font-bold";
          } else if (line.startsWith("+")) {
            lineClass = "text-emerald-400";
            bgClass = "bg-emerald-950/30";
          } else if (line.startsWith("-")) {
            lineClass = "text-rose-400";
            bgClass = "bg-rose-950/30";
          } else if (line.startsWith("@@")) {
            lineClass = "text-cyan-400 font-semibold";
            bgClass = "bg-cyan-950/20";
          }

          return (
            <div key={idx} className={`px-2 py-0.5 rounded-sm ${bgClass} ${lineClass} whitespace-pre`}>
              {line || " "}
            </div>
          );
        })}
      </div>
    </div>
  );
};
