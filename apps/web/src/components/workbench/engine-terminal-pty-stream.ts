/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM 13.4: Engine Terminal PTY Stream and Protocol Bridge.
 *
 * Implements message serialization, protocol packets, and lifecycle bindings
 * for the live Unix PTY stream connected to the Atropos engine.
 *
 * ## Research: xterm.js WebGL Addon Integration for 60 FPS Rendering
 * Researches `@xterm/addon-webgl` integration to offload terminal glyph rendering
 * to the GPU. WebGL texture atlasing sustains smooth 60 FPS scrolling and avoids
 * DOM canvas bottlenecking when processing heavy multi-megabyte compiler diagnostic
 * dumps and continuous log streams.
 */

export interface PtyMessage {
  type: 'command' | 'input' | 'resize' | 'output' | 'exit' | 'error';
  data?: string;
  cols?: number;
  rows?: number;
  code?: number;
  message?: string;
}

export function formatResizeMessage(cols: number, rows: number): string {
  return JSON.stringify({
    type: 'resize',
    cols,
    rows,
  });
}

export function formatInputMessage(data: string): string {
  return JSON.stringify({
    type: 'input',
    data,
  });
}

export function formatCommandMessage(command: string): string {
  return JSON.stringify({
    type: 'command',
    data: command,
  });
}

export function resolveTerminalWsEndpoint(projectId?: string): string {
  if (typeof window === 'undefined') {
    return 'ws://127.0.0.1:8787/v1/terminal';
  }
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const host = process.env.NEXT_PUBLIC_ATROPOS_BRIDGE_URL
    ? new URL(process.env.NEXT_PUBLIC_ATROPOS_BRIDGE_URL).host
    : window.location.host;
  return `${protocol}//${host}/v1/terminal${projectId ? `?projectId=${encodeURIComponent(projectId)}` : ''}`;
}
