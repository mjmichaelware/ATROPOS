/* SPDX-License-Identifier: AGPL-3.0-only */
"use client";

import React, { FC } from "react";
import { Lock, FileCheck2, AlertCircle } from "lucide-react";

export interface AttestationEntry {
  sourcePath: string;
  expectedSha256: string;
  actualSha256: string;
  isVerified: boolean;
  attestedAt: string;
}

export interface HashAttestAuthorityStatusProps {
  entries: AttestationEntry[];
}

export const HashAttestAuthorityStatus: FC<HashAttestAuthorityStatusProps> = ({ entries }) => {
  return (
    <div className="sg-hash-attest-authority-status bg-slate-950/70 border border-slate-800 rounded-lg p-4 flex flex-col gap-3">
      <div className="flex items-center gap-2 pb-2 border-b border-slate-800">
        <Lock className="w-4 h-4 text-emerald-400" />
        <span className="font-semibold text-sm text-slate-200">Source Authority Hash Attestations</span>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full text-left font-mono text-xs text-slate-300">
          <thead className="text-[11px] uppercase bg-slate-900/60 text-slate-400 border-b border-slate-800">
            <tr>
              <th className="p-2">Document Path</th>
              <th className="p-2">Expected Lock</th>
              <th className="p-2">Actual State</th>
              <th className="p-2 text-right">Attestation</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800/60">
            {entries.map((entry) => (
              <tr key={entry.sourcePath} className="hover:bg-slate-900/40">
                <td className="p-2 text-slate-200">{entry.sourcePath}</td>
                <td className="p-2 text-slate-400">{entry.expectedSha256.slice(0, 12)}...</td>
                <td className="p-2 text-slate-400">{entry.actualSha256.slice(0, 12)}...</td>
                <td className="p-2 text-right">
                  {entry.isVerified ? (
                    <span className="inline-flex items-center gap-1 text-emerald-400 font-semibold">
                      <FileCheck2 className="w-3.5 h-3.5" /> LOCKED
                    </span>
                  ) : (
                    <span className="inline-flex items-center gap-1 text-rose-400 font-semibold">
                      <AlertCircle className="w-3.5 h-3.5" /> DRIFTED
                    </span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};
