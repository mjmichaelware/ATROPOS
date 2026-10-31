/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM C15: Contextual CodeLens Action Display Provider.
 *
 * Displays actionable metadata directly above class, method, and contract definitions,
 * exposing direct click actions to run tests, inspect callers, view Git blame, or view formal verification proofs.
 *
 * ## Research: Asynchronous CodeLens Computation with Cancellation Tokens
 * Researches debounced AST symbol query caching with AbortController cancellation tokens
 * to prevent stale reference count recalculation during continuous typing.
 */

'use client';

import React from 'react';
import { Play, Users, GitCommit, ShieldCheck } from 'lucide-react';

export interface CodeLensItem {
  id: string;
  line: number;
  label: string;
  kind: 'runTest' | 'callers' | 'blame' | 'proof';
  command: () => void;
}

export interface ContextualCodeLensProviderProps {
  items: CodeLensItem[];
  className?: string;
}

export function ContextualCodeLensProvider({
  items,
  className = '',
}: ContextualCodeLensProviderProps) {
  if (items.length === 0) return null;

  return (
    <div className={`contextual-codelens-provider flex flex-wrap gap-2 text-[10px] font-mono text-sg-neutral-400 select-none py-0.5 ${className}`}>
      {items.map((item) => (
        <button
          key={item.id}
          type="button"
          onClick={item.command}
          className="inline-flex items-center gap-1 hover:text-emerald-400 transition-colors cursor-pointer bg-sg-neutral-900/60 px-1.5 py-0.5 rounded border border-sg-neutral-800 hover:border-emerald-500/40"
        >
          {item.kind === 'runTest' && <Play className="w-2.5 h-2.5 text-emerald-400 fill-emerald-400" />}
          {item.kind === 'callers' && <Users className="w-2.5 h-2.5 text-sky-400" />}
          {item.kind === 'blame' && <GitCommit className="w-2.5 h-2.5 text-amber-400" />}
          {item.kind === 'proof' && <ShieldCheck className="w-2.5 h-2.5 text-purple-400" />}
          <span>{item.label}</span>
        </button>
      ))}
    </div>
  );
}
