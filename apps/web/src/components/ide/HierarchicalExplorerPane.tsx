/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM M1.2: Explorer Hierarchical File Pane.
 *
 * Implements a dense, collapsible Explorer pane displaying nested project directories,
 * source files, and asset folders with Git status badges and inline creation triggers.
 *
 * ## Research: Virtualized DOM Tree Rendering at 60 FPS
 * Researches virtualized window rendering with dynamic node height indexing
 * (`react-virtual` / window offset tree flattening) to allow smooth 60 FPS exploration
 * across repositories exceeding 50,000 files without DOM bloat.
 */

'use client';

import React, { useState, useMemo, useCallback } from 'react';
import {
  Folder,
  FolderOpen,
  FileCode,
  FileText,
  File,
  Plus,
  ChevronRight,
  ChevronDown,
  RefreshCw
} from 'lucide-react';
import type { FileTreeItem } from './workspace-file-tree';

export type GitStatusBadge = 'M' | 'A' | 'U' | 'D' | null;

export interface ExplorerFileNode extends FileTreeItem {
  gitStatus?: GitStatusBadge;
  depth?: number;
}

export interface HierarchicalExplorerPaneProps {
  rootNodes: ExplorerFileNode[];
  selectedPath?: string;
  onSelectNode: (node: ExplorerFileNode) => void;
  onCreateFile?: (parentPath: string, fileName: string) => void;
  onRefresh?: () => void;
  className?: string;
}

export function HierarchicalExplorerPane({
  rootNodes,
  selectedPath,
  onSelectNode,
  onCreateFile,
  onRefresh,
  className = '',
}: HierarchicalExplorerPaneProps) {
  const [collapsedDirs, setCollapsedDirs] = useState<Set<string>>(new Set());
  const [newFileInputParent, setNewFileInputParent] = useState<string | null>(null);
  const [newFileName, setNewFileName] = useState('');

  const toggleDirectory = useCallback((dirPath: string) => {
    setCollapsedDirs((prev) => {
      const next = new Set(prev);
      if (next.has(dirPath)) {
        next.delete(dirPath);
      } else {
        next.add(dirPath);
      }
      return next;
    });
  }, []);

  const handleCreateSubmit = (parentPath: string) => {
    if (newFileName.trim()) {
      onCreateFile?.(parentPath, newFileName.trim());
      setNewFileName('');
      setNewFileInputParent(null);
    }
  };

  const renderNode = (node: ExplorerFileNode, depth: number = 0): React.ReactNode => {
    const isDir = node.isDirectory || (node.children && node.children.length > 0);
    const isCollapsed = collapsedDirs.has(node.path);
    const isSelected = selectedPath === node.path;

    return (
      <div key={node.path} className="explorer-node-branch">
        <div
          role="treeitem"
          aria-selected={isSelected}
          aria-expanded={isDir ? !isCollapsed : undefined}
          onClick={() => {
            if (isDir) {
              toggleDirectory(node.path);
            }
            onSelectNode(node);
          }}
          style={{ paddingLeft: `${depth * 14 + 8}px` }}
          className={`flex items-center justify-between py-1 pr-2 text-xs cursor-pointer select-none rounded transition-colors group ${
            isSelected
              ? 'bg-emerald-950/60 text-emerald-400 font-medium'
              : 'text-sg-neutral-300 hover:bg-sg-neutral-800/50'
          }`}
        >
          <div className="flex items-center gap-1.5 min-w-0">
            {isDir ? (
              <span className="text-sg-neutral-500">
                {isCollapsed ? <ChevronRight className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
              </span>
            ) : (
              <span className="w-3.5" />
            )}
            {isDir ? (
              isCollapsed ? <Folder className="w-4 h-4 text-amber-400" /> : <FolderOpen className="w-4 h-4 text-amber-400" />
            ) : node.name.endsWith('.ts') || node.name.endsWith('.tsx') || node.name.endsWith('.kt') ? (
              <FileCode className="w-4 h-4 text-blue-400" />
            ) : node.name.endsWith('.md') ? (
              <FileText className="w-4 h-4 text-sg-neutral-400" />
            ) : (
              <File className="w-4 h-4 text-sg-neutral-400" />
            )}
            <span className="truncate">{node.name}</span>
          </div>

          <div className="flex items-center gap-1">
            {node.gitStatus && (
              <span
                className={`text-[10px] font-mono font-bold px-1 rounded ${
                  node.gitStatus === 'M'
                    ? 'text-amber-400 bg-amber-950/40'
                    : node.gitStatus === 'A'
                    ? 'text-emerald-400 bg-emerald-950/40'
                    : 'text-purple-400 bg-purple-950/40'
                }`}
              >
                {node.gitStatus}
              </span>
            )}
            {isDir && onCreateFile && (
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  setNewFileInputParent(node.path);
                }}
                className="opacity-0 group-hover:opacity-100 p-0.5 text-sg-neutral-400 hover:text-white"
                title="New File Inside Directory"
              >
                <Plus className="w-3 h-3" />
              </button>
            )}
          </div>
        </div>

        {isDir && !isCollapsed && newFileInputParent === node.path && (
          <div style={{ paddingLeft: `${(depth + 1) * 14 + 8}px` }} className="py-1 pr-2">
            <input
              type="text"
              autoFocus
              value={newFileName}
              onChange={(e) => setNewFileName(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') handleCreateSubmit(node.path);
                if (e.key === 'Escape') setNewFileInputParent(null);
              }}
              onBlur={() => handleCreateSubmit(node.path)}
              placeholder="Filename..."
              className="w-full text-xs bg-sg-neutral-900 border border-emerald-500 rounded px-1.5 py-0.5 text-white outline-none"
            />
          </div>
        )}

        {isDir && !isCollapsed && node.children?.map((child) => renderNode(child, depth + 1))}
      </div>
    );
  };

  return (
    <div
      className={`hierarchical-explorer-pane flex flex-col h-full bg-sg-neutral-900 border-r border-sg-neutral-800 text-sg-neutral-200 ${className}`}
      role="tree"
      aria-label="Workspace File Explorer"
    >
      <div className="flex items-center justify-between px-3 py-2 border-b border-sg-neutral-800">
        <span className="text-xs font-semibold uppercase tracking-wider text-sg-neutral-400">
          Explorer
        </span>
        <div className="flex items-center gap-1">
          {onRefresh && (
            <button
              type="button"
              onClick={onRefresh}
              className="p-1 text-sg-neutral-400 hover:text-white rounded"
              title="Refresh Workspace Explorer"
            >
              <RefreshCw className="w-3.5 h-3.5" />
            </button>
          )}
        </div>
      </div>

      <div className="flex-1 overflow-y-auto p-1 font-mono">
        {rootNodes.map((rootNode) => renderNode(rootNode, 0))}
      </div>
    </div>
  );
}
