/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import React, { useState, useEffect } from 'react';
import {
  FolderTree,
  FileCode,
  GitCompare,
  Terminal,
  Activity,
  Plus,
  Save,
  RotateCcw,
  Maximize2,
  Minimize2,
  ChevronRight,
  FolderPlus,
  Search,
  Code2,
  X
} from 'lucide-react';
import { WorkspaceFileTree, FileNode } from './workspace-file-tree';
import { MonacoCodeEditor } from './monaco-code-editor';
import { UnifiedDiffEditor } from './unified-diff-editor';
import { AstVisualizer } from './ast-visualizer';
import { TerminalEmulator } from './terminal-emulator';

export interface SovereignIdeWorkstationProps {
  projectId: string;
  projectName?: string;
  initialPath?: string;
}

interface OpenTab {
  id: string;
  name: string;
  path: string;
  content: string;
  originalContent: string;
  language: string;
  isDirty: boolean;
}

const SAMPLE_TREE: FileNode[] = [
  {
    id: 'root-src',
    name: 'src',
    path: 'src',
    type: 'directory',
    children: [
      {
        id: 'src-main',
        name: 'main',
        path: 'src/main',
        type: 'directory',
        children: [
          {
            id: 'main-kt',
            name: 'kotlin',
            path: 'src/main/kotlin',
            type: 'directory',
            children: [
              {
                id: 'core-kt',
                name: 'Main.kt',
                path: 'src/main/kotlin/Main.kt',
                type: 'file',
                size: 2450
              },
              {
                id: 'bridge-kt',
                name: 'SovereignBridge.kt',
                path: 'src/main/kotlin/SovereignBridge.kt',
                type: 'file',
                size: 4820
              }
            ]
          }
        ]
      }
    ]
  },
  {
    id: 'root-apps',
    name: 'apps',
    path: 'apps',
    type: 'directory',
    children: [
      {
        id: 'web-pkg',
        name: 'web',
        path: 'apps/web',
        type: 'directory',
        children: [
          {
            id: 'page-tsx',
            name: 'page.tsx',
            path: 'apps/web/src/app/page.tsx',
            type: 'file',
            size: 3200
          },
          {
            id: 'layout-tsx',
            name: 'layout.tsx',
            path: 'apps/web/src/app/layout.tsx',
            type: 'file',
            size: 1980
          }
        ]
      }
    ]
  },
  {
    id: 'build-gradle',
    name: 'build.gradle.kts',
    path: 'build.gradle.kts',
    type: 'file',
    size: 5120
  },
  {
    id: 'package-json',
    name: 'package.json',
    path: 'package.json',
    type: 'file',
    size: 1450
  }
];

export function SovereignIdeWorkstation({
  projectId,
  projectName = 'Sovereign Project',
  initialPath = 'src/main/kotlin/Main.kt'
}: SovereignIdeWorkstationProps) {
  const [activeView, setActiveView] = useState<'editor' | 'diff' | 'ast'>('editor');
  const [isSidebarOpen, setIsSidebarOpen] = useState(true);
  const [isTerminalOpen, setIsTerminalOpen] = useState(false);
  const [showNewFileModal, setShowNewFileModal] = useState(false);
  const [newFileName, setNewFileName] = useState('');
  const [statusMessage, setStatusMessage] = useState<string | null>(null);

  const [tabs, setTabs] = useState<OpenTab[]>([
    {
      id: 'tab-1',
      name: 'Main.kt',
      path: 'src/main/kotlin/Main.kt',
      content: `package atropos.core

import kotlinx.coroutines.*
import java.security.MessageDigest

/**
 * Sovereign Engine Core Entry Point
 * Three Laws of Robotics Verification Loop
 */
class SovereignEngine(private val nodeId: String) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun initializeMerkleTree(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(nodeId.toByteArray())
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun executeGoal(goalSpec: String): Result<Boolean> {
        println("[SOVEREIGN] Synthesizing goal: $goalSpec")
        return Result.success(true)
    }
}`,
      originalContent: `package atropos.core

import kotlinx.coroutines.*
import java.security.MessageDigest

/**
 * Sovereign Engine Core Entry Point
 * Three Laws of Robotics Verification Loop
 */
class SovereignEngine(private val nodeId: String) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun initializeMerkleTree(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(nodeId.toByteArray())
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun executeGoal(goalSpec: String): Result<Boolean> {
        println("[SOVEREIGN] Synthesizing goal: $goalSpec")
        return Result.success(true)
    }
}`,
      language: 'kotlin',
      isDirty: false
    }
  ]);

  const [activeTabId, setActiveTabId] = useState<string>('tab-1');

  const currentTab = tabs.find((t) => t.id === activeTabId) || tabs[0];

  const handleSelectFile = (file: FileNode) => {
    const existing = tabs.find((t) => t.path === file.path);
    if (existing) {
      setActiveTabId(existing.id);
      return;
    }

    const ext = file.name.split('.').pop() || '';
    const langMap: Record<string, string> = {
      kt: 'kotlin',
      kts: 'kotlin',
      ts: 'typescript',
      tsx: 'typescript',
      js: 'javascript',
      json: 'json',
      md: 'markdown'
    };

    const newTab: OpenTab = {
      id: `tab-${Date.now()}`,
      name: file.name,
      path: file.path,
      content: `// Source buffer for ${file.path}\n// Loaded via sovereign remote bridge\n\nexport const buffer = {\n  path: "${file.path}",\n  timestamp: "${new Date().toISOString()}"\n};\n`,
      originalContent: `// Source buffer for ${file.path}\n// Loaded via sovereign remote bridge\n\nexport const buffer = {\n  path: "${file.path}",\n  timestamp: "${new Date().toISOString()}"\n};\n`,
      language: langMap[ext] || 'text',
      isDirty: false
    };

    setTabs((prev) => [...prev, newTab]);
    setActiveTabId(newTab.id);
  };

  const handleCloseTab = (tabId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (tabs.length === 1) return; // Keep at least one tab
    const filtered = tabs.filter((t) => t.id !== tabId);
    setTabs(filtered);
    if (activeTabId === tabId) {
      setActiveTabId(filtered[filtered.length - 1].id);
    }
  };

  const handleContentChange = (newVal: string) => {
    setTabs((prev) =>
      prev.map((t) =>
        t.id === activeTabId
          ? { ...t, content: newVal, isDirty: newVal !== t.originalContent }
          : t
      )
    );
  };

  const handleSave = () => {
    setTabs((prev) =>
      prev.map((t) =>
        t.id === activeTabId ? { ...t, originalContent: t.content, isDirty: false } : t
      )
    );
    setStatusMessage(`Saved ${currentTab.name}`);
    setTimeout(() => setStatusMessage(null), 2500);
  };

  const handleCreateFile = () => {
    if (!newFileName.trim()) return;
    const name = newFileName.trim();
    const newTab: OpenTab = {
      id: `tab-${Date.now()}`,
      name,
      path: `src/${name}`,
      content: `// New file: ${name}\n`,
      originalContent: `// New file: ${name}\n`,
      language: name.endsWith('.kt') ? 'kotlin' : name.endsWith('.ts') ? 'typescript' : 'text',
      isDirty: false
    };
    setTabs((prev) => [...prev, newTab]);
    setActiveTabId(newTab.id);
    setNewFileName('');
    setShowNewFileModal(false);
    setStatusMessage(`Created ${name}`);
    setTimeout(() => setStatusMessage(null), 2500);
  };

  return (
    <div className="flex flex-col h-[85vh] bg-slate-950 border border-slate-800 rounded-xl overflow-hidden shadow-2xl text-slate-100 font-sans">
      {/* Top Workstation Bar */}
      <div className="flex items-center justify-between px-4 py-2.5 bg-slate-900 border-b border-slate-800 text-xs">
        <div className="flex items-center gap-3">
          <button
            onClick={() => setIsSidebarOpen(!isSidebarOpen)}
            className={`p-1.5 rounded hover:bg-slate-800 transition-colors ${
              isSidebarOpen ? 'text-cyan-400' : 'text-slate-400'
            }`}
            title="Toggle File Explorer"
          >
            <FolderTree className="w-4 h-4" />
          </button>
          <span className="font-semibold text-slate-300">{projectName}</span>
          <span className="text-slate-600">/</span>
          <span className="font-mono text-slate-400">{currentTab?.path}</span>
          {currentTab?.isDirty && (
            <span className="w-2 h-2 rounded-full bg-amber-400 animate-pulse" title="Unsaved changes" />
          )}
        </div>

        <div className="flex items-center gap-2">
          {statusMessage && (
            <span className="text-emerald-400 font-mono text-[11px] animate-fade-in mr-2">
              {statusMessage}
            </span>
          )}

          {/* View Mode Switcher */}
          <div className="flex items-center bg-slate-800 p-0.5 rounded-lg border border-slate-700">
            <button
              onClick={() => setActiveView('editor')}
              className={`flex items-center gap-1.5 px-2.5 py-1 rounded-md text-[11px] font-medium transition-colors ${
                activeView === 'editor' ? 'bg-cyan-600 text-white' : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              <FileCode className="w-3.5 h-3.5" />
              Editor
            </button>
            <button
              onClick={() => setActiveView('diff')}
              className={`flex items-center gap-1.5 px-2.5 py-1 rounded-md text-[11px] font-medium transition-colors ${
                activeView === 'diff' ? 'bg-cyan-600 text-white' : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              <GitCompare className="w-3.5 h-3.5" />
              Unified Diff
            </button>
            <button
              onClick={() => setActiveView('ast')}
              className={`flex items-center gap-1.5 px-2.5 py-1 rounded-md text-[11px] font-medium transition-colors ${
                activeView === 'ast' ? 'bg-cyan-600 text-white' : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              <Code2 className="w-3.5 h-3.5" />
              AST Symbols
            </button>
          </div>

          <button
            onClick={handleSave}
            disabled={!currentTab?.isDirty}
            className="flex items-center gap-1.5 px-3 py-1 bg-cyan-700 hover:bg-cyan-600 disabled:opacity-40 disabled:hover:bg-cyan-700 text-white rounded-lg text-xs font-semibold transition-colors"
          >
            <Save className="w-3.5 h-3.5" />
            Save
          </button>

          <button
            onClick={() => setIsTerminalOpen(!isTerminalOpen)}
            className={`p-1.5 rounded-lg border border-slate-700 transition-colors ${
              isTerminalOpen ? 'bg-cyan-950/60 border-cyan-500 text-cyan-400' : 'bg-slate-800 text-slate-400 hover:bg-slate-700'
            }`}
            title="Toggle Terminal Emulator"
          >
            <Terminal className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Main Workbench Body */}
      <div className="flex-1 flex overflow-hidden">
        {/* Left Explorer Sidebar */}
        {isSidebarOpen && (
          <div className="w-64 border-r border-slate-800 bg-slate-950 flex flex-col">
            <div className="flex items-center justify-between p-3 border-b border-slate-800">
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                Explorer
              </span>
              <button
                onClick={() => setShowNewFileModal(true)}
                className="p-1 rounded hover:bg-slate-800 text-slate-400 hover:text-cyan-400 transition-colors"
                title="New File"
              >
                <Plus className="w-3.5 h-3.5" />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto p-2">
              <WorkspaceFileTree
                nodes={SAMPLE_TREE}
                selectedPath={currentTab?.path}
                onSelectFile={handleSelectFile}
              />
            </div>
          </div>
        )}

        {/* Center Workspace */}
        <div className="flex-1 flex flex-col overflow-hidden bg-slate-900/50">
          {/* Editor Tabs Bar */}
          <div className="flex items-center border-b border-slate-800 bg-slate-950 overflow-x-auto">
            {tabs.map((tab) => (
              <div
                key={tab.id}
                onClick={() => setActiveTabId(tab.id)}
                className={`flex items-center gap-2 px-3 py-2 text-xs font-mono border-r border-slate-800 cursor-pointer select-none transition-colors ${
                  tab.id === activeTabId
                    ? 'bg-slate-900 text-cyan-300 border-t-2 border-t-cyan-500 font-semibold'
                    : 'text-slate-400 hover:bg-slate-900/60 hover:text-slate-200'
                }`}
              >
                <FileCode className="w-3.5 h-3.5 text-slate-500" />
                <span>{tab.name}</span>
                {tab.isDirty && <span className="w-1.5 h-1.5 rounded-full bg-amber-400" />}
                {tabs.length > 1 && (
                  <button
                    onClick={(e) => handleCloseTab(tab.id, e)}
                    className="p-0.5 rounded hover:bg-slate-800 text-slate-500 hover:text-slate-300"
                  >
                    <X className="w-3 h-3" />
                  </button>
                )}
              </div>
            ))}
          </div>

          {/* Central Active View */}
          <div className="flex-1 overflow-hidden relative">
            {activeView === 'editor' && currentTab && (
              <MonacoCodeEditor
                value={currentTab.content}
                language={currentTab.language}
                filename={currentTab.name}
                isDirty={currentTab.isDirty}
                onChange={handleContentChange}
                onSave={handleSave}
              />
            )}

            {activeView === 'diff' && currentTab && (
              <UnifiedDiffEditor
                originalContent={currentTab.originalContent}
                modifiedContent={currentTab.content}
                filePath={currentTab.path}
                filename={currentTab.name}
              />
            )}

            {activeView === 'ast' && currentTab && (
              <div className="h-full overflow-y-auto p-4 bg-slate-950">
                <AstVisualizer code={currentTab.content} filename={currentTab.name} />
              </div>
            )}
          </div>

          {/* Bottom Docked Terminal */}
          {isTerminalOpen && (
            <div className="h-64 border-t border-slate-800 bg-slate-950 flex flex-col animate-in slide-in-from-bottom duration-150">
              <div className="flex items-center justify-between px-3 py-1.5 bg-slate-900 border-b border-slate-800 text-xs">
                <div className="flex items-center gap-2 font-mono text-slate-400">
                  <Terminal className="w-3.5 h-3.5 text-cyan-400" />
                  <span>Terminal TTY</span>
                </div>
                <button
                  onClick={() => setIsTerminalOpen(false)}
                  className="p-1 text-slate-400 hover:text-slate-200"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              </div>
              <div className="flex-1 overflow-hidden">
                <TerminalEmulator />
              </div>
            </div>
          )}
        </div>
      </div>

      {/* New File Modal */}
      {showNewFileModal && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4 z-50 animate-fade-in">
          <div className="bg-slate-900 border border-slate-700 rounded-xl p-6 w-full max-w-md shadow-2xl space-y-4">
            <h3 className="text-base font-semibold text-slate-100 flex items-center gap-2">
              <FileCode className="w-4 h-4 text-cyan-400" />
              Create Sovereign Source File
            </h3>

            <input
              type="text"
              value={newFileName}
              onChange={(e) => setNewFileName(e.target.value)}
              placeholder="e.g. MyVerificationService.kt"
              autoFocus
              onKeyDown={(e) => {
                if (e.key === 'Enter') handleCreateFile();
                if (e.key === 'Escape') setShowNewFileModal(false);
              }}
              className="w-full bg-slate-950 border border-slate-700 rounded-lg p-2.5 text-sm text-slate-100 font-mono focus:outline-none focus:border-cyan-500"
            />

            <div className="flex items-center justify-end gap-3 pt-2">
              <button
                onClick={() => setShowNewFileModal(false)}
                className="px-4 py-2 rounded-lg text-xs font-semibold text-slate-400 hover:bg-slate-800"
              >
                Cancel
              </button>
              <button
                onClick={handleCreateFile}
                disabled={!newFileName.trim()}
                className="px-4 py-2 bg-cyan-600 hover:bg-cyan-500 disabled:opacity-40 text-white rounded-lg text-xs font-semibold shadow-lg shadow-cyan-900/30"
              >
                Create File
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
