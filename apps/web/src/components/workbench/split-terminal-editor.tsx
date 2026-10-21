/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * SplitTerminalEditor (WB-SPLIT-003)
 *
 * Integrated split-pane workbench component combining full-fidelity xterm.js
 * terminal session with live file editor and diff inspector.
 * Supports horizontal/vertical split orientation, responsive drag-divider,
 * and binary IPC streaming synchronization.
 */

'use client';

import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Terminal } from 'xterm';
import { FitAddon } from '@xterm/addon-fit';
import { WebLinksAddon } from '@xterm/addon-web-links';
import { BinaryIpcSocket, SocketState } from '../../lib/bridge/binary-ipc-socket';
import { Split, Maximize2, Minimize2, Terminal as TerminalIcon, FileCode, RefreshCw } from 'lucide-react';
import 'xterm/css/xterm.css';

export interface FileEditorTab {
  id: string;
  filename: string;
  content: string;
  language: string;
  isDirty?: boolean;
}

export interface SplitTerminalEditorProps {
  initialTabs?: FileEditorTab[];
  activeTabId?: string;
  socketUrl?: string;
  orientation?: 'horizontal' | 'vertical';
  onFileSave?: (tabId: string, content: string) => void;
  className?: string;
}

export function SplitTerminalEditor({
  initialTabs = [],
  activeTabId: defaultActiveId,
  socketUrl = 'ws://127.0.0.1:8787/',
  orientation: initialOrientation = 'horizontal',
  onFileSave,
  className = '',
}: SplitTerminalEditorProps) {
  const [tabs, setTabs] = useState<FileEditorTab[]>(
    initialTabs.length > 0
      ? initialTabs
      : [
          {
            id: 'sample-1',
            filename: 'Contract.kt',
            content: '// ATROPOS Architectural Contract\npackage atropos.core\n\ninterface SystemContract {\n    fun verify(): Boolean\n}',
            language: 'kotlin',
          },
        ]
  );

  const [activeId, setActiveId] = useState<string>(
    defaultActiveId ?? tabs[0]?.id ?? 'sample-1'
  );
  const [splitRatio, setSplitRatio] = useState<number>(50); // 50% split
  const [orientation, setOrientation] = useState<'horizontal' | 'vertical'>(initialOrientation);
  const [isDragging, setIsDragging] = useState(false);
  const [socketState, setSocketState] = useState<SocketState>('disconnected');
  const [activePane, setActivePane] = useState<'both' | 'terminal' | 'editor'>('both');

  const terminalRef = useRef<HTMLDivElement>(null);
  const termInstance = useRef<Terminal | null>(null);
  const fitAddon = useRef<FitAddon | null>(null);
  const socketRef = useRef<BinaryIpcSocket | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  // Active file tab lookup
  const activeTab = tabs.find((t) => t.id === activeId) ?? tabs[0];

  // Initialize Terminal and WebSocket
  useEffect(() => {
    if (!terminalRef.current) return;

    const term = new Terminal({
      cursorBlink: true,
      fontFamily: 'JetBrains Mono, Menlo, Monaco, "Courier New", monospace',
      fontSize: 13,
      theme: {
        background: '#0d1117',
        foreground: '#e6edf3',
        cursor: '#58a6ff',
        selectionBackground: '#264f78',
      },
    });

    const fit = new FitAddon();
    term.loadAddon(fit);
    term.loadAddon(new WebLinksAddon());

    term.open(terminalRef.current);
    fit.fit();
    termInstance.current = term;
    fitAddon.current = fit;

    const socket = new BinaryIpcSocket({ url: socketUrl });
    socketRef.current = socket;

    socket.onStateChange((state) => {
      setSocketState(state);
    });

    socket.onFrame((_bytes, text) => {
      term.write(text);
    });

    term.onData((data) => {
      socket.sendInput(data);
    });

    term.onResize((size) => {
      socket.sendResize(size.cols, size.rows);
    });

    socket.connect();

    const handleResize = () => {
      try {
        fit.fit();
      } catch (_e) {
        // Ignored
      }
    };
    window.addEventListener('resize', handleResize);

    return () => {
      window.removeEventListener('resize', handleResize);
      socket.disconnect();
      term.dispose();
      termInstance.current = null;
    };
  }, [socketUrl]);

  // Handle Drag Resizing
  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    setIsDragging(true);
  }, []);

  useEffect(() => {
    if (!isDragging) return;

    const handleMouseMove = (e: MouseEvent) => {
      if (!containerRef.current) return;
      const rect = containerRef.current.getBoundingClientRect();
      let percent: number;

      if (orientation === 'horizontal') {
        const x = e.clientX - rect.left;
        percent = (x / rect.width) * 100;
      } else {
        const y = e.clientY - rect.top;
        percent = (y / rect.height) * 100;
      }

      setSplitRatio(Math.max(15, Math.min(85, percent)));
      fitAddon.current?.fit();
    };

    const handleMouseUp = () => {
      setIsDragging(false);
      fitAddon.current?.fit();
    };

    window.addEventListener('mousemove', handleMouseMove);
    window.addEventListener('mouseup', handleMouseUp);

    return () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    };
  }, [isDragging, orientation]);

  // Editor content changes
  const handleContentChange = (newContent: string) => {
    setTabs((prev) =>
      prev.map((tab) =>
        tab.id === activeId ? { ...tab, content: newContent, isDirty: true } : tab
      )
    );
  };

  const handleSave = () => {
    if (activeTab && onFileSave) {
      onFileSave(activeTab.id, activeTab.content);
      setTabs((prev) =>
        prev.map((t) => (t.id === activeTab.id ? { ...t, isDirty: false } : t))
      );
    }
  };

  return (
    <div
      ref={containerRef}
      className={`flex flex-col h-full w-full bg-slate-950 text-slate-100 overflow-hidden border border-slate-800 rounded-lg select-none ${className}`}
    >
      {/* Top Action Toolbar */}
      <div className="flex items-center justify-between px-3 py-1.5 bg-slate-900 border-b border-slate-800 text-xs">
        <div className="flex items-center space-x-2">
          <TerminalIcon className="w-4 h-4 text-emerald-400" />
          <span className="font-semibold text-slate-300">Workbench View</span>
          <span
            className={`inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-mono ${
              socketState === 'connected'
                ? 'bg-emerald-950 text-emerald-300 border border-emerald-800'
                : 'bg-amber-950 text-amber-300 border border-amber-800'
            }`}
          >
            {socketState}
          </span>
        </div>

        <div className="flex items-center space-x-1">
          <button
            onClick={() => setOrientation((o) => (o === 'horizontal' ? 'vertical' : 'horizontal'))}
            className="p-1 rounded hover:bg-slate-800 text-slate-400 hover:text-slate-200 transition-colors"
            title="Toggle Split Direction"
          >
            <Split className="w-3.5 h-3.5" />
          </button>
          <button
            onClick={() =>
              setActivePane((p) => (p === 'both' ? 'terminal' : p === 'terminal' ? 'editor' : 'both'))
            }
            className="p-1 rounded hover:bg-slate-800 text-slate-400 hover:text-slate-200 transition-colors"
            title="Cycle Pane Focus"
          >
            {activePane === 'both' ? (
              <Maximize2 className="w-3.5 h-3.5" />
            ) : (
              <Minimize2 className="w-3.5 h-3.5" />
            )}
          </button>
          <button
            onClick={() => socketRef.current?.connect()}
            className="p-1 rounded hover:bg-slate-800 text-slate-400 hover:text-slate-200 transition-colors"
            title="Reconnect Socket"
          >
            <RefreshCw className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      {/* Main Split Content Area */}
      <div
        className={`flex-1 flex overflow-hidden ${
          orientation === 'horizontal' ? 'flex-row' : 'flex-col'
        }`}
      >
        {/* Left / Top: Code Editor Pane */}
        {(activePane === 'both' || activePane === 'editor') && (
          <div
            style={{
              flex: activePane === 'both' ? `0 0 ${splitRatio}%` : '1 1 100%',
            }}
            className="flex flex-col bg-slate-900 overflow-hidden"
          >
            {/* File Tab Strip */}
            <div className="flex items-center bg-slate-950 border-b border-slate-800 overflow-x-auto">
              {tabs.map((tab) => (
                <button
                  key={tab.id}
                  onClick={() => setActiveId(tab.id)}
                  className={`flex items-center space-x-1.5 px-3 py-1.5 text-xs font-mono border-r border-slate-800 transition-colors ${
                    tab.id === activeId
                      ? 'bg-slate-900 text-slate-200 border-t-2 border-t-indigo-500'
                      : 'text-slate-400 hover:bg-slate-900 hover:text-slate-300'
                  }`}
                >
                  <FileCode className="w-3.5 h-3.5 text-indigo-400" />
                  <span>{tab.filename}</span>
                  {tab.isDirty && <span className="w-1.5 h-1.5 rounded-full bg-amber-400" />}
                </button>
              ))}
            </div>

            {/* Code Textarea / Viewer */}
            <div className="flex-1 relative flex flex-col p-2 bg-slate-950">
              <textarea
                value={activeTab?.content ?? ''}
                onChange={(e) => handleContentChange(e.target.value)}
                onKeyDown={(e) => {
                  if ((e.metaKey || e.ctrlKey) && e.key === 's') {
                    e.preventDefault();
                    handleSave();
                  }
                }}
                spellCheck={false}
                className="w-full h-full bg-transparent text-slate-200 font-mono text-xs resize-none outline-none leading-relaxed p-1"
                aria-label="Code Editor Input"
              />
            </div>
          </div>
        )}

        {/* Resizable Divider Handle */}
        {activePane === 'both' && (
          <div
            onMouseDown={handleMouseDown}
            className={`transition-colors duration-150 z-10 select-none ${
              orientation === 'horizontal'
                ? 'w-1.5 cursor-col-resize hover:bg-indigo-500 bg-slate-800'
                : 'h-1.5 cursor-row-resize hover:bg-indigo-500 bg-slate-800'
            }`}
          />
        )}

        {/* Right / Bottom: xterm.js Terminal Pane */}
        {(activePane === 'both' || activePane === 'terminal') && (
          <div
            style={{
              flex: activePane === 'both' ? `0 0 ${100 - splitRatio}%` : '1 1 100%',
            }}
            className="flex flex-col bg-[#0d1117] overflow-hidden"
          >
            <div className="flex items-center justify-between px-3 py-1 bg-slate-900 border-b border-slate-800 text-[11px] text-slate-400">
              <span className="font-mono">xterm.js mirror (ws://127.0.0.1:8787)</span>
              <span className="text-slate-500">Ctrl+C / Ctrl+D supported</span>
            </div>
            <div
              ref={terminalRef}
              className="flex-1 w-full h-full overflow-hidden p-1.5 bg-[#0d1117]"
            />
          </div>
        )}
      </div>
    </div>
  );
}
