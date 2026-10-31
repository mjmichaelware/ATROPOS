/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import React, { useState } from 'react';
import { GitMerge, Check, ArrowRight, ArrowLeft, Layers } from 'lucide-react';

/**
 * ATOM VC5: DEDICATED THREE-WAY MERGE CONFLICT EDITOR
 * Path: apps/web/src/components/ide/ThreeWayMergeConflictEditor.tsx
 *
 * Dedicated three-way merge editor interface displaying base, current (local),
 * and incoming changes side by side with one-click resolution buttons.
 */
export interface ConflictHunk {
  id: string;
  startLine: number;
  currentContent: string;
  incomingContent: string;
  baseContent: string;
  resolution?: 'current' | 'incoming' | 'both' | 'custom';
}

export interface ThreeWayMergeConflictEditorProps {
  filePath: string;
  hunks: ConflictHunk[];
  onResolveAll: (resolvedHunks: ConflictHunk[]) => void;
  className?: string;
}

export const ThreeWayMergeConflictEditor: React.FC<ThreeWayMergeConflictEditorProps> = ({
  filePath,
  hunks: initialHunks,
  onResolveAll,
  className = '',
}) => {
  const [hunks, setHunks] = useState<ConflictHunk[]>(initialHunks);

  const handleResolveHunk = (hunkId: string, choice: 'current' | 'incoming' | 'both') => {
    setHunks((prev) =>
      prev.map((h) => (h.id === hunkId ? { ...h, resolution: choice } : h))
    );
  };

  const allResolved = hunks.every((h) => !!h.resolution);

  return (
    <div className={`flex flex-col h-full bg-slate-950 text-slate-100 border border-slate-800 rounded-lg overflow-hidden ${className}`}>
      {/* Header Bar */}
      <div className="flex items-center justify-between px-4 py-2 bg-slate-900 border-b border-slate-800">
        <div className="flex items-center gap-2">
          <GitMerge className="w-4 h-4 text-amber-400" />
          <span className="font-semibold text-xs tracking-wider text-slate-200">
            MERGE CONFLICT: <span className="font-mono text-cyan-400">{filePath}</span>
          </span>
          <span className="text-[11px] px-2 py-0.5 rounded-full bg-slate-800 text-slate-400 font-mono">
            {hunks.filter((h) => !h.resolution).length} unresolved
          </span>
        </div>

        <button
          disabled={!allResolved}
          onClick={() => onResolveAll(hunks)}
          className={`flex items-center gap-1.5 px-3 py-1 rounded text-xs font-semibold transition-colors ${
            allResolved
              ? 'bg-emerald-500 hover:bg-emerald-400 text-slate-950 cursor-pointer'
              : 'bg-slate-800 text-slate-500 cursor-not-allowed'
          }`}
        >
          <Check className="w-3.5 h-3.5" />
          Complete Merge
        </button>
      </div>

      {/* Three-Pane Column Labels */}
      <div className="grid grid-cols-3 border-b border-slate-800 bg-slate-900/60 text-center py-1.5 text-[11px] font-mono font-semibold">
        <div className="text-cyan-400 border-r border-slate-800">Current (Local / HEAD)</div>
        <div className="text-slate-400 border-r border-slate-800">Base Common Ancestor</div>
        <div className="text-emerald-400">Incoming (Remote / Branch)</div>
      </div>

      {/* Conflict Hunks Viewer */}
      <div className="flex-1 overflow-auto divide-y divide-slate-800/80 p-2 space-y-4">
        {hunks.map((hunk, index) => (
          <div key={hunk.id} className="bg-slate-900/40 border border-slate-800 rounded p-3">
            <div className="flex items-center justify-between mb-2 pb-1 border-b border-slate-800/60 text-xs">
              <span className="font-mono text-slate-400">Conflict #{index + 1} at line {hunk.startLine}</span>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => handleResolveHunk(hunk.id, 'current')}
                  className={`px-2 py-0.5 rounded text-[11px] border ${
                    hunk.resolution === 'current'
                      ? 'bg-cyan-500 text-slate-950 border-cyan-400 font-bold'
                      : 'border-cyan-800/60 text-cyan-300 hover:bg-cyan-950/60'
                  }`}
                >
                  Accept Current
                </button>
                <button
                  onClick={() => handleResolveHunk(hunk.id, 'both')}
                  className={`px-2 py-0.5 rounded text-[11px] border ${
                    hunk.resolution === 'both'
                      ? 'bg-amber-500 text-slate-950 border-amber-400 font-bold'
                      : 'border-amber-800/60 text-amber-300 hover:bg-amber-950/60'
                  }`}
                >
                  Accept Both
                </button>
                <button
                  onClick={() => handleResolveHunk(hunk.id, 'incoming')}
                  className={`px-2 py-0.5 rounded text-[11px] border ${
                    hunk.resolution === 'incoming'
                      ? 'bg-emerald-500 text-slate-950 border-emerald-400 font-bold'
                      : 'border-emerald-800/60 text-emerald-300 hover:bg-emerald-950/60'
                  }`}
                >
                  Accept Incoming
                </button>
              </div>
            </div>

            <div className="grid grid-cols-3 gap-2 font-mono text-[11px]">
              <pre className="p-2 bg-cyan-950/20 border border-cyan-800/40 rounded text-cyan-200 overflow-x-auto whitespace-pre-wrap">
                {hunk.currentContent}
              </pre>
              <pre className="p-2 bg-slate-950/40 border border-slate-800 rounded text-slate-400 overflow-x-auto whitespace-pre-wrap">
                {hunk.baseContent}
              </pre>
              <pre className="p-2 bg-emerald-950/20 border border-emerald-800/40 rounded text-emerald-200 overflow-x-auto whitespace-pre-wrap">
                {hunk.incomingContent}
              </pre>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};
