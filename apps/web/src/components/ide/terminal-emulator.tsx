'use client';

import React, { useState, useRef, useEffect } from 'react';
import {
  Terminal as TerminalIcon,
  Play,
  Trash2,
  Copy,
  Check,
  Maximize2,
  CornerDownLeft
} from 'lucide-react';
import { engine } from '@/lib/engine/client';

export interface TerminalLine {
  id: string;
  type: 'input' | 'output' | 'error' | 'system';
  text: string;
  timestamp: string;
}

export function TerminalEmulator() {
  const [lines, setLines] = useState<TerminalLine[]>([
    {
      id: 'l-0',
      type: 'system',
      text: 'ATROPOS Sovereign Terminal Environment v2.0\nType "/help" to view command categories or "/" to browse commands.',
      timestamp: '00:00:01'
    }
  ]);
  const [input, setInput] = useState<string>('');
  const [history, setHistory] = useState<string[]>([]);
  const [historyIndex, setHistoryIndex] = useState<number>(-1);
  const [isRunning, setIsRunning] = useState<boolean>(false);
  const [copied, setCopied] = useState<boolean>(false);

  const terminalEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    terminalEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [lines]);

  const handleKeyDown = async (e: React.KeyboardEvent<HTMLInputElement>) => {
    // Up arrow: history back
    if (e.key === 'ArrowUp') {
      e.preventDefault();
      if (history.length === 0) return;
      const nextIdx = historyIndex < history.length - 1 ? historyIndex + 1 : historyIndex;
      setHistoryIndex(nextIdx);
      setInput(history[history.length - 1 - nextIdx] || '');
      return;
    }

    // Down arrow: history forward
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      if (historyIndex > 0) {
        const nextIdx = historyIndex - 1;
        setHistoryIndex(nextIdx);
        setInput(history[history.length - 1 - nextIdx] || '');
      } else if (historyIndex === 0) {
        setHistoryIndex(-1);
        setInput('');
      }
      return;
    }

    // Enter: execute
    if (e.key === 'Enter') {
      e.preventDefault();
      const trimmed = input.trim();
      if (!trimmed) return;

      const timestamp = new Date().toTimeString().split(' ')[0];
      const newLines: TerminalLine[] = [
        ...lines,
        { id: `in-${Date.now()}`, type: 'input', text: trimmed, timestamp }
      ];
      setLines(newLines);
      setHistory((prev) => [...prev, trimmed]);
      setHistoryIndex(-1);
      setInput('');

      // Built-in commands
      if (trimmed === 'clear') {
        setLines([]);
        return;
      }

      if (trimmed === 'help' || trimmed === '/help') {
        setLines((prev) => [
          ...prev,
          {
            id: `out-${Date.now()}`,
            type: 'system',
            text: `Available Sovereign Command Families:
  /goal <text>       - Launch long-horizon autonomous task
  /plan <text>       - Synthesize multi-step DAG plan
  /model [model]     - Inspect or switch active neural model
  /providers         - Display active provider health & latencies
  /files             - Inspect and edit workspace files
  /git               - Query repository status, diffs, branches
  /specgraph         - Inspect compiler DAG passes
  /quota             - Display live token consumption & burn rate`,
            timestamp
          }
        ]);
        return;
      }

      // Execute via engine
      setIsRunning(true);
      try {
        const cleanCmd = trimmed.replace(/^\//, '');
        const res = await engine.execute(cleanCmd);
        const outText = res.ok
          ? res.data.ok
            ? res.data.output || '(command executed cleanly with zero output)'
            : res.data.failure ?? 'Execution failed'
          : `${res.detail}\n${res.remedy}`;

        setLines((prev) => [
          ...prev,
          {
            id: `out-${Date.now()}`,
            type: res.ok && res.data.ok ? 'output' : 'error',
            text: outText,
            timestamp
          }
        ]);
      } catch (err) {
        setLines((prev) => [
          ...prev,
          {
            id: `err-${Date.now()}`,
            type: 'error',
            text: err instanceof Error ? err.message : String(err),
            timestamp
          }
        ]);
      } finally {
        setIsRunning(false);
      }
    }
  };

  const handleCopyAll = () => {
    const fullText = lines.map((l) => `${l.type === 'input' ? '$ ' : ''}${l.text}`).join('\n');
    navigator.clipboard.writeText(fullText);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  return (
    <div className="flex flex-col h-full w-full bg-slate-950 text-slate-200 border border-slate-800 rounded-xl overflow-hidden font-mono text-xs shadow-2xl">
      {/* Terminal Title Bar */}
      <div className="flex items-center justify-between px-4 py-2.5 bg-slate-900 border-b border-slate-800">
        <div className="flex items-center gap-2">
          <TerminalIcon className="h-4 w-4 text-cyan-400" />
          <span className="font-bold text-white text-xs">SOVEREIGN WORKSTATION TERMINAL</span>
          <span className="text-[10px] px-1.5 py-0.5 rounded bg-cyan-500/10 border border-cyan-500/30 text-cyan-400">
            TTY-1
          </span>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={handleCopyAll}
            title="Copy Terminal Output"
            className="p-1.5 rounded-lg border border-slate-800 bg-slate-900 text-slate-400 hover:text-white transition-colors"
          >
            {copied ? <Check className="h-3.5 w-3.5 text-emerald-400" /> : <Copy className="h-3.5 w-3.5" />}
          </button>
          <button
            onClick={() => setLines([])}
            title="Clear Terminal (clear)"
            className="p-1.5 rounded-lg border border-slate-800 bg-slate-900 text-slate-400 hover:text-rose-400 transition-colors"
          >
            <Trash2 className="h-3.5 w-3.5" />
          </button>
        </div>
      </div>

      {/* Terminal Output Log Area */}
      <div
        onClick={() => inputRef.current?.focus()}
        className="flex-1 p-4 overflow-y-auto space-y-2 select-text cursor-text bg-slate-950"
      >
        {lines.map((l) => (
          <div key={l.id} className="leading-relaxed">
            {l.type === 'input' && (
              <div className="flex items-start gap-2 text-cyan-400">
                <span className="text-slate-500 select-none">$</span>
                <span className="font-bold">{l.text}</span>
              </div>
            )}
            {l.type === 'output' && (
              <pre className="text-slate-300 pl-4 whitespace-pre-wrap font-mono">{l.text}</pre>
            )}
            {l.type === 'error' && (
              <pre className="text-rose-400 pl-4 whitespace-pre-wrap font-mono font-bold">{l.text}</pre>
            )}
            {l.type === 'system' && (
              <pre className="text-purple-300 pl-4 whitespace-pre-wrap font-mono">{l.text}</pre>
            )}
          </div>
        ))}

        {isRunning && (
          <div className="flex items-center gap-2 text-amber-400 pl-4">
            <span className="animate-pulse">●</span>
            <span>Executing engine instruction...</span>
          </div>
        )}

        <div ref={terminalEndRef} />
      </div>

      {/* Terminal Input Line */}
      <div className="flex items-center gap-2 px-4 py-2.5 bg-slate-900 border-t border-slate-800">
        <span className="text-cyan-400 font-bold select-none">atropos $</span>
        <input
          ref={inputRef}
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Type instruction or / to browse commands..."
          disabled={isRunning}
          className="flex-1 bg-transparent outline-none text-white placeholder-slate-600 font-mono text-xs"
        />
        <CornerDownLeft className="h-3.5 w-3.5 text-slate-600" />
      </div>
    </div>
  );
}
