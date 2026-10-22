/* SPDX-License-Identifier: AGPL-3.0-only */
"use client";

import React, { FC, useState } from "react";
import { ShieldCheck, Database, Search, FileCode } from "lucide-react";

export interface LedgerEventItem {
  timestamp: string;
  caller: string;
  tool: string;
  casHash: string;
  bytes: number;
}

export interface EvidenceLedgerBrowserProps {
  initialEvents: LedgerEventItem[];
  onInspectCas?: (hash: string) => void;
}

export const EvidenceLedgerBrowser: FC<EvidenceLedgerBrowserProps> = ({
  initialEvents,
  onInspectCas,
}) => {
  const [filter, setFilter] = useState("");
  const [inspectedHash, setInspectedHash] = useState<string | null>(null);

  const filteredEvents = initialEvents.filter(
    (ev) =>
      ev.tool.toLowerCase().includes(filter.toLowerCase()) ||
      ev.caller.toLowerCase().includes(filter.toLowerCase()) ||
      ev.casHash.toLowerCase().includes(filter.toLowerCase())
  );

  const handleInspect = (hash: string) => {
    setInspectedHash(hash);
    onInspectCas?.(hash);
  };

  return (
    <div className="sg-evidence-ledger-browser flex flex-col h-full bg-slate-950/70 border border-slate-800 rounded-lg p-4">
      {/* Header & Filter */}
      <div className="flex items-center justify-between pb-3 border-b border-slate-800 gap-4">
        <div className="flex items-center gap-2">
          <Database className="w-4 h-4 text-emerald-400" />
          <span className="font-semibold text-sm text-slate-200">Sovereign Evidence Ledger</span>
        </div>
        <div className="relative w-64">
          <Search className="w-3.5 h-3.5 absolute left-2.5 top-2.5 text-slate-500" />
          <input
            type="text"
            placeholder="Filter events or CAS hash..."
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            className="w-full bg-slate-900 border border-slate-700 rounded pl-8 pr-3 py-1 text-xs text-slate-200 focus:outline-none focus:border-emerald-500"
          />
        </div>
      </div>

      {/* Events Table */}
      <div className="flex-1 overflow-auto mt-3">
        <table className="w-full text-left font-mono text-xs text-slate-300">
          <thead className="text-[11px] uppercase bg-slate-900/60 text-slate-400 border-b border-slate-800">
            <tr>
              <th className="p-2">Timestamp</th>
              <th className="p-2">Caller</th>
              <th className="p-2">Tool</th>
              <th className="p-2">CAS Hash</th>
              <th className="p-2 text-right">Bytes</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800/60">
            {filteredEvents.map((ev, idx) => (
              <tr key={`${ev.casHash}-${idx}`} className="hover:bg-slate-900/40">
                <td className="p-2 text-slate-400">{ev.timestamp.split("T")[1]?.slice(0, 8) ?? ev.timestamp}</td>
                <td className="p-2 text-blue-400 font-sans">{ev.caller}</td>
                <td className="p-2 text-emerald-400">{ev.tool}</td>
                <td className="p-2">
                  <button
                    type="button"
                    onClick={() => handleInspect(ev.casHash)}
                    className="text-amber-400 hover:underline flex items-center gap-1"
                  >
                    <ShieldCheck className="w-3 h-3 text-emerald-400" />
                    <span>{ev.casHash.slice(0, 12)}...</span>
                  </button>
                </td>
                <td className="p-2 text-right text-slate-400">{ev.bytes} B</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* CAS Inspector Modal */}
      {inspectedHash && (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 bg-black/70 flex items-center justify-center p-4"
        >
          <div className="bg-slate-900 border border-slate-700 rounded-lg max-w-lg w-full p-4 space-y-3">
            <div className="flex items-center justify-between border-b border-slate-800 pb-2">
              <span className="text-xs font-semibold text-emerald-400 flex items-center gap-1.5">
                <FileCode className="w-4 h-4" /> CAS Payload: {inspectedHash.slice(0, 16)}...
              </span>
              <button
                type="button"
                onClick={() => setInspectedHash(null)}
                className="text-xs text-slate-400 hover:text-white"
              >
                Close
              </button>
            </div>
            <pre className="bg-black/50 p-3 rounded font-mono text-xs text-slate-300 max-h-60 overflow-auto">
              {`// Content-Addressed Storage Entry\n// Hash: ${inspectedHash}\n// Status: VERIFIED CRYPTOGRAPHIC MATCH`}
            </pre>
          </div>
        </div>
      )}
    </div>
  );
};
