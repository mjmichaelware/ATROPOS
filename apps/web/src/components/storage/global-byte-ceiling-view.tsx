/* SPDX-License-Identifier: AGPL-3.0-only */
"use client";

import React, { FC } from "react";
import { PieChart, Server, Layers } from "lucide-react";

export interface StorageCategoryAllocation {
  category: string;
  bytesUsed: number;
  byteCeiling: number;
  objectCount: number;
}

export interface GlobalByteCeilingViewProps {
  allocations: StorageCategoryAllocation[];
}

export const GlobalByteCeilingView: FC<GlobalByteCeilingViewProps> = ({ allocations }) => {
  const formatMB = (bytes: number) => (bytes / (1024 * 1024)).toFixed(2);

  return (
    <div className="sg-global-byte-ceiling-view bg-slate-950/70 border border-slate-800 rounded-lg p-4 flex flex-col gap-3">
      <div className="flex items-center gap-2 pb-2 border-b border-slate-800">
        <Server className="w-4 h-4 text-cyan-400" />
        <span className="font-semibold text-sm text-slate-200">Global Byte Allocation & Ceiling</span>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full text-left font-mono text-xs text-slate-300">
          <thead className="text-[11px] uppercase bg-slate-900/60 text-slate-400 border-b border-slate-800">
            <tr>
              <th className="p-2">Category</th>
              <th className="p-2">Objects</th>
              <th className="p-2">Used</th>
              <th className="p-2">Ceiling</th>
              <th className="p-2 text-right">Allocation</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800/60">
            {allocations.map((alloc) => {
              const pct = Math.min(100, Math.round((alloc.bytesUsed / (alloc.byteCeiling || 1)) * 100));
              return (
                <tr key={alloc.category} className="hover:bg-slate-900/40">
                  <td className="p-2 text-cyan-300 font-sans">{alloc.category}</td>
                  <td className="p-2 text-slate-400">{alloc.objectCount}</td>
                  <td className="p-2 text-slate-200">{formatMB(alloc.bytesUsed)} MB</td>
                  <td className="p-2 text-slate-400">{formatMB(alloc.byteCeiling)} MB</td>
                  <td className="p-2 text-right">
                    <span className={`px-1.5 py-0.5 rounded text-[10px] ${pct > 80 ? "bg-amber-950 text-amber-300" : "bg-slate-800 text-slate-300"}`}>
                      {pct}%
                    </span>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
};
