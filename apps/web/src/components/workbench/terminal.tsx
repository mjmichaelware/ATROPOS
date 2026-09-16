/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * Terminal Component (ADD-W-014).
 *
 * First-class terminal in the workbench using xterm.js.
 * Connects to `/v1/terminal` WebSocket for PTY access.
 * Includes fit addon for responsive sizing, web links addon,
 * and serialize addon for session persistence.
 */

'use client';

import { useEffect, useRef, useState } from 'react';
import { Terminal } from '@xterm/xterm';
import { FitAddon } from '@xterm/addon-fit';
import { WebLinksAddon } from '@xterm/addon-web-links';
import { SerializeAddon } from '@xterm/addon-serialize';
import '@xterm/xterm/css/xterm.css';

interface TerminalProps {
  /** Optional project ID for scoping the terminal session. */
  projectId?: string;
  /** Optional initial command to run. */
  initialCommand?: string;
  /** Callback when terminal process exits. */
  onExit?: (code: number) => void;
}

export function TerminalComponent({ projectId, initialCommand, onExit }: TerminalProps) {
  const terminalRef = useRef<HTMLDivElement>(null);
  const terminal = useRef<Terminal | null>(null);
  const fitAddon = useRef<FitAddon | null>(null);
  const [connected, setConnected] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const socketRef = useRef<WebSocket | null>(null);

  useEffect(() => {
    if (!terminalRef.current) return;

    // Create xterm.js terminal
    const term = new Terminal({
      cursorBlink: true,
      theme: {
        background: '#0d1117',
        foreground: '#e6edf3',
        cursor: '#e6edf3',
        cursorAccent: '#0d1117',
        selectionBackground: '#264f78',
        black: '#484f58',
        red: '#ff7b72',
        green: '#3fb950',
        yellow: '#d29922',
        blue: '#58a6ff',
        magenta: '#bc8cff',
        cyan: '#39c5cf',
        white: '#e6edf3',
        brightBlack: '#6e7681',
        brightRed: '#ff9779',
        brightGreen: '#56d364',
        brightYellow: '#e3b341',
        brightBlue: '#79c0ff',
        brightMagenta: '#d2a8ff',
        brightCyan: '#56d4dd',
        brightWhite: '#ffffff',
      },
      fontFamily: '"JetBrains Mono", "Fira Code", "Monaco", "Menlo", monospace',
      fontSize: 13,
      lineHeight: 1.4,
      letterSpacing: 0,
      allowProposedApi: true,
      scrollback: 10000,
    });

    const fit = new FitAddon();
    const webLinks = new WebLinksAddon();
    const serialize = new SerializeAddon();

    term.loadAddon(fit);
    term.loadAddon(webLinks);
    term.loadAddon(serialize);

    terminal.current = term;
    fitAddon.current = fit;

    term.open(terminalRef.current!);
    fit.fit();

    // Connect to terminal WebSocket
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = process.env.NEXT_PUBLIC_ATROPOS_BRIDGE_URL
      ? new URL(process.env.NEXT_PUBLIC_ATROPOS_BRIDGE_URL).host
      : window.location.host;
    const wsUrl = `${protocol}//${host}/v1/terminal${projectId ? `?projectId=${encodeURIComponent(projectId)}` : ''}`;

    const socket = new WebSocket(wsUrl);
    socketRef.current = socket;

    socket.onopen = () => {
      setConnected(true);
      setError(null);
      if (initialCommand) {
        socket.send(JSON.stringify({ type: 'command', data: initialCommand }));
      }
      // Send initial resize
      socket.send(JSON.stringify({
        type: 'resize',
        cols: term.cols,
        rows: term.rows,
      }));
    };

    socket.onmessage = (event) => {
      try {
        const message = JSON.parse(event.data);
        switch (message.type) {
          case 'output':
            term.write(message.data);
            break;
          case 'exit':
            setConnected(false);
            onExit?.(message.code ?? 0);
            break;
          case 'error':
            setError(message.message);
            break;
        }
      } catch {
        // Ignore malformed messages
      }
    };

    socket.onclose = () => {
      setConnected(false);
      if (!error) {
        setError('Terminal connection closed');
      }
    };

    socket.onerror = () => {
      setError('Terminal connection error');
    };

    // Handle terminal input
    term.onData((data) => {
      if (socket.readyState === WebSocket.OPEN) {
        socket.send(JSON.stringify({ type: 'input', data }));
      }
    });

    // Handle resize
    const handleResize = () => {
      if (terminalRef.current) {
        fit.fit();
        if (socket.readyState === WebSocket.OPEN) {
          socket.send(JSON.stringify({
            type: 'resize',
            cols: term.cols,
            rows: term.rows,
          }));
        }
      }
    };

    window.addEventListener('resize', handleResize);

    // Restore serialized state if available
    try {
      const saved = localStorage.getItem(`atropos-terminal-${projectId ?? 'global'}`);
      if (saved) {
        const data = JSON.parse(saved);
        if (data.serialized) {
          serialize.deserialize(data.serialized);
        }
      }
    } catch {
      // Ignore restore errors
    }

    // Persist state periodically
    const persistInterval = setInterval(() => {
      try {
        const serialized = serialize.serialize();
        if (serialized) {
          localStorage.setItem(`atropos-terminal-${projectId ?? 'global'}`, JSON.stringify({ serialized }));
        }
      } catch {
        // Ignore persist errors
      }
    }, 5000);

    return () => {
      window.removeEventListener('resize', handleResize);
      clearInterval(persistInterval);
      socket.close();
      term.dispose();
    };
  }, [projectId, initialCommand, onExit]);

  if (!terminalRef.current) {
    return (
      <div className="wb-terminal wb-terminal-loading" data-testid="terminal">
        <p className="wb-terminal-note">Initializing terminal…</p>
      </div>
    );
  }

  return (
    <div className="wb-terminal" data-testid="terminal" data-connected={connected}>
      <div className="wb-terminal-header">
        <span className="wb-terminal-title">Terminal</span>
        <span className={`wb-terminal-status ${connected ? 'connected' : 'disconnected'}`}>
          {connected ? '● Connected' : '○ Disconnected'}
        </span>
        {error && (
          <span className="wb-terminal-error" role="alert">
            {error}
          </span>
        )}
      </div>
      <div ref={terminalRef} className="wb-terminal-body" />
    </div>
  );
}

/**
 * Hook for programmatic terminal access.
 */
export function useTerminal() {
  const terminalRef = useRef<Terminal | null>(null);

  return {
    terminal: terminalRef.current,
    setTerminal: (term: Terminal | null) => { terminalRef.current = term; },
  };
}