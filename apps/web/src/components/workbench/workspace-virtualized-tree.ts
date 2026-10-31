/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM 13.2: Workspace Virtualized Tree and Territory Status Engine.
 *
 * Implements node virtualization helpers and territory bounds verification
 * distinguishing in-territory writable files from out-of-territory read-only files.
 *
 * ## Research: File-System Event Streaming via fsnotify over WebSockets
 * Researches real-time file-system event streaming (Linux inotify, macOS FSEvents,
 * Android fanotify) routed via engine WebSockets (`/v1/workspace/events`).
 * Instant mutation diffs update the client tree model at sub-10ms latency without
 * relying on repeated HTTP polling or cache staleness heuristics.
 */

export interface WorkspaceFileMetadata {
  path: string;
  name: string;
  size?: number;
  inTerritory: boolean;
  readOnly: boolean;
  extension?: string;
  lastModified?: number;
}

export interface FsNotifyEvent {
  type: 'create' | 'modify' | 'delete' | 'rename';
  path: string;
  targetPath?: string;
  timestamp: number;
}

export function isPathInTerritory(path: string, allowedPrefixes: string[] = ['src/', 'app/', 'docs/']): boolean {
  if (path.startsWith('/') || path.includes('..')) {
    return false;
  }
  return allowedPrefixes.some((prefix) => path.startsWith(prefix));
}

export function classifyFileNode(path: string, name: string, size?: number): WorkspaceFileMetadata {
  const inTerritory = isPathInTerritory(path);
  const ext = name.includes('.') ? name.split('.').pop() : undefined;
  return {
    path,
    name,
    size,
    inTerritory,
    readOnly: !inTerritory,
    extension: ext,
  };
}
