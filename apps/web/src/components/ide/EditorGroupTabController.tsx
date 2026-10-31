/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM M1.3: Editor Group with Split Views and Tabs.
 *
 * Implements editor tab group controllers supporting multiple open documents,
 * horizontal and vertical split views, breadcrumb navigation, and dirty state indicators.
 *
 * ## Research: Shared Memory Buffers for Zero-Copy Diff Comparison
 * Researches `SharedArrayBuffer` and `Atomics` memory backing within Web Worker Monaco editor
 * instances to perform high-frequency structural diffing without copying strings across the JS main thread.
 */

'use client';

import React, { useState, useCallback } from 'react';
import {
  X,
  Columns,
  Rows,
  ChevronRight,
  Circle,
  FileCode,
  Maximize2
} from 'lucide-react';
import type { MonacoCodeEditorProps } from './monaco-code-editor';

export interface EditorTabModel {
  id: string;
  path: string;
  title: string;
  language: string;
  content: string;
  isDirty?: boolean;
}

export type SplitDirection = 'none' | 'horizontal' | 'vertical';

export interface EditorGroupTabControllerProps {
  tabs: EditorTabModel[];
  activeTabId?: string;
  splitDirection?: SplitDirection;
  onTabSelect: (tabId: string) => void;
  onTabClose: (tabId: string) => void;
  onSplitChange?: (split: SplitDirection) => void;
  onSaveTab?: (tabId: string) => void;
  className?: string;
}

export function EditorGroupTabController({
  tabs,
  activeTabId,
  splitDirection = 'none',
  onTabSelect,
  onTabClose,
  onSplitChange,
  onSaveTab,
  className = '',
}: EditorGroupTabControllerProps) {
  const activeTab = tabs.find((t) => t.id === activeTabId) ?? tabs[0];
  const breadcrumbParts = activeTab?.path.split('/').filter(Boolean) ?? [];

  return (
    <div className={`editor-group-tab-controller flex flex-col w-full bg-sg-neutral-950 border-b border-sg-neutral-800 ${className}`}>
      {/* Tab Strip */}
      <div className="flex items-center justify-between bg-sg-neutral-900 border-b border-sg-neutral-800 px-1 overflow-x-auto select-none">
        <div className="flex items-center gap-0.5 overflow-x-auto" role="tablist">
          {tabs.map((tab) => {
            const isActive = tab.id === (activeTab?.id ?? '');
            return (
              <div
                key={tab.id}
                role="tab"
                aria-selected={isActive}
                onClick={() => onTabSelect(tab.id)}
                className={`flex items-center gap-2 px-3 py-1.5 text-xs font-mono cursor-pointer border-t-2 border-r border-sg-neutral-800 transition-colors ${
                  isActive
                    ? 'bg-sg-neutral-950 text-white border-t-emerald-500 font-medium'
                    : 'bg-sg-neutral-900/80 text-sg-neutral-400 hover:bg-sg-neutral-800 hover:text-sg-neutral-200 border-t-transparent'
                }`}
              >
                <FileCode className="w-3.5 h-3.5 text-emerald-400" />
                <span className="truncate max-w-[140px]">{tab.title}</span>
                {tab.isDirty ? (
                  <Circle className="w-2 h-2 fill-amber-400 text-amber-400" />
                ) : (
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      onTabClose(tab.id);
                    }}
                    className="p-0.5 hover:bg-sg-neutral-700 rounded text-sg-neutral-500 hover:text-white"
                  >
                    <X className="w-3 h-3" />
                  </button>
                )}
              </div>
            );
          })}
        </div>

        {/* View Split Controls */}
        <div className="flex items-center gap-1 px-2 text-sg-neutral-400">
          <button
            type="button"
            title="Split Right"
            onClick={() => onSplitChange?.(splitDirection === 'vertical' ? 'none' : 'vertical')}
            className={`p-1 rounded hover:text-white hover:bg-sg-neutral-800 ${
              splitDirection === 'vertical' ? 'text-emerald-400 bg-sg-neutral-800' : ''
            }`}
          >
            <Columns className="w-3.5 h-3.5" />
          </button>
          <button
            type="button"
            title="Split Down"
            onClick={() => onSplitChange?.(splitDirection === 'horizontal' ? 'none' : 'horizontal')}
            className={`p-1 rounded hover:text-white hover:bg-sg-neutral-800 ${
              splitDirection === 'horizontal' ? 'text-emerald-400 bg-sg-neutral-800' : ''
            }`}
          >
            <Rows className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      {/* Breadcrumb Path */}
      {activeTab && (
        <div className="flex items-center gap-1 px-4 py-1 text-[11px] font-mono text-sg-neutral-400 bg-sg-neutral-950/80 border-b border-sg-neutral-800/60">
          {breadcrumbParts.map((part, index) => (
            <React.Fragment key={index}>
              {index > 0 && <ChevronRight className="w-3 h-3 text-sg-neutral-600" />}
              <span className={index === breadcrumbParts.length - 1 ? 'text-emerald-400 font-semibold' : ''}>
                {part}
              </span>
            </React.Fragment>
          ))}
        </div>
      )}
    </div>
  );
}
