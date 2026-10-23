'use client';

import React, { useMemo } from 'react';
import {
  GitCommit,
  ArrowRight,
  Check,
  X,
  FileCode,
  Copy,
  ChevronDown,
  ChevronUp
} from 'lucide-react';

export interface UnifiedDiffEditorProps {
  filePath?: string;
  filename?: string;
  originalContent: string;
  modifiedContent: string;
  onApplyDiff?: () => void;
  onDiscardDiff?: () => void;
}

interface DiffLine {
  type: 'addition' | 'deletion' | 'context';
  text: string;
  origLineNumber?: number;
  modLineNumber?: number;
}

export function UnifiedDiffEditor({
  filePath,
  filename,
  originalContent,
  modifiedContent,
  onApplyDiff,
  onDiscardDiff
}: UnifiedDiffEditorProps) {
  const diffLines = useMemo(() => {
    const orig = originalContent.split('\n');
    const mod = modifiedContent.split('\n');

    const result: DiffLine[] = [];
    let i = 0;
    let j = 0;

    // Simple line diff algorithm
    while (i < orig.length || j < mod.length) {
      if (i < orig.length && j < mod.length && orig[i] === mod[j]) {
        result.push({
          type: 'context',
          text: orig[i],
          origLineNumber: i + 1,
          modLineNumber: j + 1
        });
        i++;
        j++;
      } else if (j < mod.length && (i >= orig.length || !orig.includes(mod[j]))) {
        result.push({
          type: 'addition',
          text: mod[j],
          modLineNumber: j + 1
        });
        j++;
      } else if (i < orig.length) {
        result.push({
          type: 'deletion',
          text: orig[i],
          origLineNumber: i + 1
        });
        i++;
      }
    }

    return result;
  }, [originalContent, modifiedContent]);

  const stats = useMemo(() => {
    const additions = diffLines.filter((l) => l.type === 'addition').length;
    const deletions = diffLines.filter((l) => l.type === 'deletion').length;
    return { additions, deletions };
  }, [diffLines]);

  return (
    <div className="flex flex-col h-full w-full bg-slate-950 border border-slate-800 rounded-xl overflow-hidden font-mono text-xs shadow-2xl">
      {/* Diff Header Bar */}
      <div className="flex items-center justify-between px-5 py-3 bg-slate-900 border-b border-slate-800">
        <div className="flex items-center gap-2.5">
          <FileCode className="h-4 w-4 text-cyan-400" />
          <span className="font-bold text-white text-xs">{filePath || filename || 'Unknown'}</span>
          <div className="flex items-center gap-1.5 ml-2 text-[10px]">
            <span className="px-1.5 py-0.5 rounded bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 font-bold">
              +{stats.additions}
            </span>
            <span className="px-1.5 py-0.5 rounded bg-rose-500/10 border border-rose-500/30 text-rose-400 font-bold">
              -{stats.deletions}
            </span>
          </div>
        </div>

        {/* Diff Actions */}
        <div className="flex items-center gap-2">
          {onDiscardDiff && (
            <button
              onClick={onDiscardDiff}
              className="flex items-center gap-1 px-3 py-1.5 rounded-lg border border-rose-500/30 bg-rose-500/10 text-rose-400 hover:bg-rose-500/20 text-xs font-bold transition-colors"
            >
              <X className="h-3.5 w-3.5" />
              <span>Discard Diff</span>
            </button>
          )}

          {onApplyDiff && (
            <button
              onClick={onApplyDiff}
              className="flex items-center gap-1 px-3 py-1.5 rounded-lg bg-emerald-500 hover:bg-emerald-400 text-black text-xs font-bold transition-colors shadow-md shadow-emerald-500/20"
            >
              <Check className="h-3.5 w-3.5" />
              <span>Apply Changes</span>
            </button>
          )}
        </div>
      </div>

      {/* Diff Lines Body */}
      <div className="flex-1 overflow-auto divide-y divide-slate-800/30 font-mono text-xs leading-6">
        {diffLines.map((line, idx) => (
          <div
            key={idx}
            className={`flex items-start select-text ${
              line.type === 'addition'
                ? 'bg-emerald-500/10 text-emerald-200'
                : line.type === 'deletion'
                ? 'bg-rose-500/10 text-rose-200'
                : 'text-slate-300 hover:bg-slate-900/40'
            }`}
          >
            {/* Old Line Number */}
            <div className="w-12 py-0.5 pr-2 text-right select-none text-[10px] text-slate-600 bg-slate-900/30 border-r border-slate-800/60 shrink-0">
              {line.origLineNumber || ''}
            </div>

            {/* New Line Number */}
            <div className="w-12 py-0.5 pr-2 text-right select-none text-[10px] text-slate-600 bg-slate-900/30 border-r border-slate-800/60 shrink-0">
              {line.modLineNumber || ''}
            </div>

            {/* Change Prefix Indicator (+, -, ' ') */}
            <div
              className={`w-6 py-0.5 text-center select-none font-bold shrink-0 ${
                line.type === 'addition'
                  ? 'text-emerald-400'
                  : line.type === 'deletion'
                  ? 'text-rose-400'
                  : 'text-slate-600'
              }`}
            >
              {line.type === 'addition' ? '+' : line.type === 'deletion' ? '-' : ' '}
            </div>

            {/* Line Text Content */}
            <div className="flex-1 py-0.5 pr-4 whitespace-pre overflow-x-auto tab-4">
              {line.text}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
