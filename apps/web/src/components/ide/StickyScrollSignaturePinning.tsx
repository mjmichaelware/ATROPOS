/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM C5: Sticky Scroll Signature Pinning Subsystem.
 *
 * Pins enclosing class, interface, and function signature declarations to the top of the
 * editor viewport during scrolling through long implementations.
 *
 * ## Research: Binary Interval Search Trees for Sticky Scope Lookup
 * Researches 1D interval trees (`IntervalTree<ScopeRange>`) to resolve sticky header
 * scopes in O(log N) time relative to the current topmost visible line number.
 */

'use client';

import React from 'react';
import { ChevronRight, Code2 } from 'lucide-react';

export interface ScopeSignature {
  line: number;
  kind: 'class' | 'interface' | 'function' | 'object';
  name: string;
  signatureText: string;
}

export interface StickyScrollSignaturePinningProps {
  currentTopLine: number;
  scopes: ScopeSignature[];
  maxStickyLines?: number;
  onNavigateToLine?: (line: number) => void;
  className?: string;
}

export function StickyScrollSignaturePinning({
  currentTopLine,
  scopes,
  maxStickyLines = 3,
  onNavigateToLine,
  className = '',
}: StickyScrollSignaturePinningProps) {
  // Find active enclosing scopes whose definition starts before currentTopLine
  const activeScopes = scopes
    .filter((s) => s.line <= currentTopLine)
    .slice(-maxStickyLines);

  if (activeScopes.length === 0) return null;

  return (
    <div
      className={`sticky-scroll-signature-pinning sticky top-0 left-0 right-0 z-20 bg-sg-neutral-900/95 backdrop-blur-sm border-b border-sg-neutral-800 text-xs font-mono shadow-md ${className}`}
    >
      {activeScopes.map((scope, index) => (
        <div
          key={scope.line}
          onClick={() => onNavigateToLine?.(scope.line)}
          className="flex items-center gap-1.5 px-3 py-1 cursor-pointer hover:bg-sg-neutral-800 transition-colors text-sg-neutral-300"
          title={`Line ${scope.line}: Click to jump to definition`}
        >
          <Code2 className="w-3.5 h-3.5 text-emerald-400" />
          <span className="text-[10px] uppercase font-bold text-emerald-500">
            {scope.kind}
          </span>
          <span className="text-sg-neutral-500">•</span>
          <span className="font-semibold text-white">{scope.name}</span>
          <span className="text-sg-neutral-400 truncate max-w-md text-[11px]">
            {scope.signatureText}
          </span>
        </div>
      ))}
    </div>
  );
}
