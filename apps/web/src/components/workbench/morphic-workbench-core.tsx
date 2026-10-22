/* SPDX-License-Identifier: AGPL-3.0-only */
"use client";

import React, { FC, useState } from "react";
import { resolveOpticalStanding, OpticalStanding } from "@/lib/design/territory-optical-focus";
import { File, Folder, Terminal, Code2, AlertTriangle } from "lucide-react";

export interface WorkbenchFileNode {
  path: string;
  isDirectory: boolean;
  inTerritory: boolean;
  status: string;
}

export interface MorphicWorkbenchProps {
  activeProjectId: string;
  initialFiles: WorkbenchFileNode[];
  onFileSelect?: (path: string) => void;
}

export const MorphicWorkbenchCore: FC<MorphicWorkbenchProps> = ({
  activeProjectId,
  initialFiles,
  onFileSelect,
}) => {
  const [selectedFile, setSelectedFile] = useState<string | null>(
    initialFiles.find((f) => !f.isDirectory)?.path ?? null
  );
  const [activeTab, setActiveTab] = useState<"editor" | "terminal">("editor");

  const handleSelect = (node: WorkbenchFileNode) => {
    if (!node.isDirectory) {
      setSelectedFile(node.path);
      onFileSelect?.(node.path);
    }
  };

  return (
    <div className="sg-morphic-workbench flex h-full w-full overflow-hidden bg-sg-canvas text-slate-100 rounded-lg border border-slate-800">
      {/* Left Pane: Collapsible File Tree */}
      <aside className="sg-workbench-left-pane w-64 border-r border-slate-800 flex flex-col bg-slate-950/60 backdrop-blur-md">
        <div className="sg-pane-header p-3 border-b border-slate-800/80 flex items-center justify-between text-xs font-semibold uppercase tracking-wider text-slate-400">
          <span>Project Files</span>
          <span className="text-[10px] text-emerald-400">{activeProjectId}</span>
        </div>
        <div className="sg-file-tree flex-1 overflow-y-auto p-2 space-y-1">
          {initialFiles.map((node) => {
            const optical = resolveOpticalStanding(node.path, node.inTerritory ? [node.path] : [], true, false);
            const isSelected = selectedFile === node.path;

            return (
              <div
                key={node.path}
                onClick={() => handleSelect(node)}
                style={{
                  opacity: optical.opacity,
                  filter: optical.filter,
                }}
                className={`sg-file-node flex items-center justify-between px-2.5 py-1.5 rounded cursor-pointer text-xs transition-colors ${
                  isSelected ? "bg-slate-800/90 text-emerald-400 font-medium" : "text-slate-300 hover:bg-slate-900/60"
                }`}
                role="treeitem"
                aria-selected={isSelected}
              >
                <div className="flex items-center gap-2 truncate">
                  {node.isDirectory ? (
                    <Folder className="w-3.5 h-3.5 text-amber-400/80 shrink-0" />
                  ) : (
                    <File className="w-3.5 h-3.5 text-blue-400/80 shrink-0" />
                  )}
                  <span className="truncate">{node.path}</span>
                </div>
                {optical.standing === OpticalStanding.OUT_OF_TERRITORY_RECEDED && (
                  <span className="text-[9px] px-1 py-0.2 rounded bg-slate-800 text-slate-400 border border-slate-700">
                    Off-Scope
                  </span>
                )}
              </div>
            );
          })}
        </div>
      </aside>

      {/* Center/Right Pane: Editor & Terminal */}
      <main className="sg-workbench-center-pane flex-1 flex flex-col overflow-hidden bg-slate-950/30">
        {/* Tab Header */}
        <header className="sg-workbench-tabs flex items-center border-b border-slate-800 bg-slate-900/40 px-2">
          <button
            type="button"
            onClick={() => setActiveTab("editor")}
            className={`flex items-center gap-1.5 px-3 py-2 text-xs font-medium border-b-2 transition-colors ${
              activeTab === "editor"
                ? "border-emerald-500 text-emerald-400 bg-slate-900/50"
                : "border-transparent text-slate-400 hover:text-slate-200"
            }`}
          >
            <Code2 className="w-3.5 h-3.5" />
            <span>Editor: {selectedFile ?? "Untitled"}</span>
          </button>
          <button
            type="button"
            onClick={() => setActiveTab("terminal")}
            className={`flex items-center gap-1.5 px-3 py-2 text-xs font-medium border-b-2 transition-colors ${
              activeTab === "terminal"
                ? "border-emerald-500 text-emerald-400 bg-slate-900/50"
                : "border-transparent text-slate-400 hover:text-slate-200"
            }`}
          >
            <Terminal className="w-3.5 h-3.5" />
            <span>Mirror Terminal (:8787)</span>
          </button>
        </header>

        {/* Viewport Content */}
        <div className="sg-workbench-viewport flex-1 overflow-auto p-4">
          {activeTab === "editor" ? (
            <div className="sg-editor-canvas font-mono text-xs text-slate-300 leading-relaxed">
              <pre className="p-3 bg-slate-900/50 rounded border border-slate-800/80 overflow-x-auto">
                {`// Territory-bounded editor buffer for: ${selectedFile ?? "None"}\n// Auto-synced with CAS content-addressed repository`}
              </pre>
            </div>
          ) : (
            <div className="sg-terminal-canvas h-full bg-black/90 p-3 rounded font-mono text-xs text-emerald-400 border border-slate-800">
              <p>[ATROPOS IPC TERMINAL MIRROR — WebSocket port 8787 connected]</p>
              <p className="text-slate-500">Ready for operator inputs.</p>
            </div>
          )}
        </div>
      </main>
    </div>
  );
};
