'use client';

import React, { useState, useMemo } from 'react';
import {
  Folder,
  FolderOpen,
  FileCode,
  FileText,
  File,
  Search,
  RefreshCw,
  Plus,
  ChevronRight,
  ChevronDown,
  Layers
} from 'lucide-react';

export interface FileTreeItem {
  id?: string;
  path: string;
  name: string;
  isDirectory?: boolean;
  type?: 'file' | 'directory';
  size?: number;
  sizeBytes?: number;
  children?: FileTreeItem[];
}

export type FileNode = FileTreeItem;

export interface WorkspaceFileTreeProps {
  rootItems: FileTreeItem[];
  selectedPath?: string;
  onSelectFile: (item: FileTreeItem) => void;
  onRefresh?: () => void;
}

export function WorkspaceFileTree({
  rootItems,
  selectedPath,
  onSelectFile,
  onRefresh
}: WorkspaceFileTreeProps) {
  const [filterQuery, setFilterQuery] = useState('');
  const [expandedPaths, setExpandedPaths] = useState<Set<string>>(
    new Set(['src', 'src/main', 'src/main/kotlin', 'apps', 'apps/web'])
  );

  const toggleFolder = (path: string) => {
    setExpandedPaths((prev) => {
      const next = new Set(prev);
      if (next.has(path)) {
        next.delete(path);
      } else {
        next.add(path);
      }
      return next;
    });
  };

  return (
    <div className="flex flex-col h-full w-full bg-slate-950 text-slate-200 border border-slate-800 rounded-xl overflow-hidden font-mono text-xs select-none">
      {/* File Tree Header Toolbar */}
      <div className="flex items-center justify-between px-3.5 py-2.5 bg-slate-900 border-b border-slate-800">
        <span className="font-bold text-white uppercase text-[11px] tracking-wider flex items-center gap-1.5">
          <Layers className="h-3.5 w-3.5 text-cyan-400" />
          Workspace Explorer
        </span>

        <div className="flex items-center gap-1">
          {onRefresh && (
            <button
              onClick={onRefresh}
              title="Refresh Workspace Tree"
              className="p-1 rounded hover:bg-slate-800 text-slate-400 hover:text-white transition-colors"
            >
              <RefreshCw className="h-3.5 w-3.5" />
            </button>
          )}
        </div>
      </div>

      {/* Filter Search Input */}
      <div className="p-2 border-b border-slate-800/80 bg-slate-900/40">
        <div className="relative">
          <Search className="absolute left-2.5 top-2 h-3.5 w-3.5 text-slate-500" />
          <input
            type="text"
            placeholder="Filter files..."
            value={filterQuery}
            onChange={(e) => setFilterQuery(e.target.value)}
            className="w-full pl-8 pr-2.5 py-1 text-xs rounded-lg bg-slate-900 border border-slate-800 text-white placeholder-slate-500 focus:outline-none focus:border-cyan-400"
          />
        </div>
      </div>

      {/* Tree View Item Hierarchy */}
      <div className="flex-1 overflow-y-auto p-2 space-y-0.5">
        {rootItems.map((item) => (
          <FileTreeNodeRow
            key={item.path}
            item={item}
            depth={0}
            selectedPath={selectedPath}
            filterQuery={filterQuery}
            expandedPaths={expandedPaths}
            onToggleFolder={toggleFolder}
            onSelectFile={onSelectFile}
          />
        ))}
      </div>
    </div>
  );
}

function FileTreeNodeRow({
  item,
  depth,
  selectedPath,
  filterQuery,
  expandedPaths,
  onToggleFolder,
  onSelectFile
}: {
  item: FileTreeItem;
  depth: number;
  selectedPath?: string;
  filterQuery: string;
  expandedPaths: Set<string>;
  onToggleFolder: (path: string) => void;
  onSelectFile: (item: FileTreeItem) => void;
}) {
  const isExpanded = expandedPaths.has(item.path);
  const isSelected = selectedPath === item.path;

  // Filter matching
  if (filterQuery.trim()) {
    const q = filterQuery.toLowerCase();
    const matchesSelf = item.name.toLowerCase().includes(q);
    const matchesChild = item.children?.some((c) => c.name.toLowerCase().includes(q));
    if (!matchesSelf && !matchesChild) return null;
  }

  const getFileIcon = (name: string) => {
    if (name.endsWith('.kt')) return <span className="text-[10px] text-purple-400 font-bold">KT</span>;
    if (name.endsWith('.ts') || name.endsWith('.tsx')) return <span className="text-[10px] text-cyan-400 font-bold">TS</span>;
    if (name.endsWith('.json')) return <span className="text-[10px] text-amber-400 font-bold">{}</span>;
    if (name.endsWith('.md')) return <FileText className="h-3.5 w-3.5 text-blue-400" />;
    return <FileCode className="h-3.5 w-3.5 text-slate-400" />;
  };

  const isDir = Boolean(item.isDirectory ?? (item.type === 'directory') ?? item.children);

  return (
    <div>
      <div
        onClick={() => {
          if (isDir) {
            onToggleFolder(item.path);
          } else {
            onSelectFile(item);
          }
        }}
        style={{ paddingLeft: `${depth * 14 + 6}px` }}
        className={`flex items-center justify-between py-1 px-2 rounded-lg cursor-pointer transition-colors ${
          isSelected
            ? 'bg-cyan-500/20 text-cyan-300 font-bold border border-cyan-500/40'
            : 'hover:bg-slate-900 text-slate-300 hover:text-white'
        }`}
      >
        <div className="flex items-center gap-1.5 min-w-0">
          {isDir ? (
            <>
              {isExpanded ? (
                <ChevronDown className="h-3.5 w-3.5 text-slate-500 shrink-0" />
              ) : (
                <ChevronRight className="h-3.5 w-3.5 text-slate-500 shrink-0" />
              )}
              {isExpanded ? (
                <FolderOpen className="h-3.5 w-3.5 text-amber-400 shrink-0" />
              ) : (
                <Folder className="h-3.5 w-3.5 text-amber-400 shrink-0" />
              )}
            </>
          ) : (
            <span className="w-3.5 flex justify-center shrink-0">{getFileIcon(item.name)}</span>
          )}
          <span className="truncate text-xs">{item.name}</span>
        </div>

        {(item.sizeBytes !== undefined || item.size !== undefined) && !isDir && (
          <span className="text-[10px] text-slate-600 font-mono ml-2 shrink-0">
            {((item.sizeBytes ?? item.size) ?? 0) < 1024
              ? `${(item.sizeBytes ?? item.size) ?? 0}B`
              : `${(((item.sizeBytes ?? item.size) ?? 0) / 1024).toFixed(1)}K`}
          </span>
        )}
      </div>

      {isDir && isExpanded && item.children && (
        <div className="space-y-0.5">
          {item.children.map((child) => (
            <FileTreeNodeRow
              key={child.path}
              item={child}
              depth={depth + 1}
              selectedPath={selectedPath}
              filterQuery={filterQuery}
              expandedPaths={expandedPaths}
              onToggleFolder={onToggleFolder}
              onSelectFile={onSelectFile}
            />
          ))}
        </div>
      )}
    </div>
  );
}
