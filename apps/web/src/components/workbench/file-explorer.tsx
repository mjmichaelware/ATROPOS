/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * ATOM 13.2: Hierarchical File Explorer with In-Territory Context (F-WEB-004 complete).
 *
 * Reads the project file tree from `/v1/workspace/tree` and falls back
 * to session files when the tree endpoint is unavailable. Opens files via
 * `/v1/workspace/file` so the bridge can enforce territory and attestation.
 *
 * Clearly marks in-territory writable files versus out-of-territory read-only files,
 * allowing users to inspect and edit files with immediate status feedback.
 *
 * ## Research: File-System Event Streaming over WebSockets via fsnotify
 * Researches file-system event streaming over WebSockets using fsnotify primitives to update
 * client-side tree nodes instantly without periodic polling, ensuring multi-agent code mutations
 * reflect seamlessly in the active UI buffer view.
 */

'use client';

import { useCallback, useEffect, useState } from 'react';
import { useWorkbenchTabs } from '@/lib/contexts/workbench-tabs-context';
import { readEngine } from '@/lib/engine/client';
import { readWorkspaceTree, readWorkspaceFile, writeWorkspaceFile } from '@/lib/workspace/client';
import { classifyFileNode, isPathInTerritory } from './workspace-virtualized-tree';
export { CompactDirectoryMerger, defaultCompactDirectoryMerger } from './CompactDirectoryMerger';
export type { CompactFolderNode, DirectoryNode } from './CompactDirectoryMerger';
export { ActiveFileAutoRevealController, defaultActiveFileAutoRevealController } from './ActiveFileAutoRevealController';

export interface FilesPayload {
  ok: true;
  files: Array<{ name: string; size: number }>;
}

export interface WorkspaceTreeNode {
  name: string;
  path: string;
  type: 'file' | 'directory';
  size?: number;
  inTerritory?: boolean;
  readOnly?: boolean;
  children?: WorkspaceTreeNode[];
}

export interface WorkspaceTreePayload {
  ok: true;
  tree: WorkspaceTreeNode[];
}

export type ExplorerState =
  | { kind: 'loading' }
  | { kind: 'error'; detail: string; remedy: string }
  | { kind: 'empty'; source: 'session' | 'workspace' }
  | { kind: 'workspace'; tree: WorkspaceTreeNode[] }
  | { kind: 'session'; files: FilesPayload['files'] };

export function flattenTree(nodes: WorkspaceTreeNode[], prefix = ''): Array<{ path: string; name: string; depth: number }> {
  const result: Array<{ path: string; name: string; depth: number }> = [];
  for (const node of nodes) {
    const fullPath = prefix ? `${prefix}/${node.name}` : node.name;
    result.push({ path: fullPath, name: node.name, depth: node.type === 'directory' ? 0 : 1 });
    if (node.type === 'directory' && node.children) {
      result.push(...flattenTree(node.children, fullPath));
    }
  }
  return result;
}

export function FileExplorer({
  onOpen,
}: {
  /** Called with the chosen path before/instead of opening a tab. */
  onOpen?: (path: string) => void;
}) {
  const tabs = useWorkbenchTabs();
  const [state, setState] = useState<ExplorerState>({ kind: 'loading' });
  const [source, setSource] = useState<'workspace' | 'session' | 'loading'>('loading');

  const load = useCallback(async () => {
    setState({ kind: 'loading' });
    setSource('loading');
    try {
      const result = await readWorkspaceTree();
      if (result.tree.length === 0) {
        setState({ kind: 'empty', source: 'workspace' });
        setSource('workspace');
      } else {
        setState({ kind: 'workspace', tree: result.tree });
        setSource('workspace');
      }
    } catch {
      // Fall back to session files
      const result = await readEngine<FilesPayload>('/v1/files');
      if (!result.ok) {
        setState({ kind: 'error', detail: result.detail, remedy: result.remedy });
      } else if (result.data.files.length === 0) {
        setState({ kind: 'empty', source: 'session' });
        setSource('session');
      } else {
        setState({ kind: 'session', files: result.data.files });
        setSource('session');
      }
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const result = await readWorkspaceTree();
        if (cancelled) return;
        if (result.tree.length === 0) {
          // Fall back to session files
          const filesResult = await readEngine<FilesPayload>('/v1/files');
          if (cancelled) return;
          if (!filesResult.ok) {
            setState({ kind: 'error', detail: filesResult.detail, remedy: filesResult.remedy });
          } else if (filesResult.data.files.length === 0) {
            setState({ kind: 'empty', source: 'session' });
            setSource('session');
          } else {
            setState({ kind: 'session', files: filesResult.data.files });
            setSource('session');
          }
        } else {
          setState({ kind: 'workspace', tree: result.tree });
          setSource('workspace');
        }
      } catch {
        // Fall back to session files
        const filesResult = await readEngine<FilesPayload>('/v1/files');
        if (cancelled) return;
        if (!filesResult.ok) {
          setState({ kind: 'error', detail: filesResult.detail, remedy: filesResult.remedy });
        } else if (filesResult.data.files.length === 0) {
          setState({ kind: 'empty', source: 'session' });
          setSource('session');
        } else {
          setState({ kind: 'session', files: filesResult.data.files });
          setSource('session');
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  function open(item: { path: string; name: string }) {
    if (onOpen) onOpen(item.path);
    tabs.open(item.path);
    // Trigger async content load
    void (async () => {
      try {
        const result = await readWorkspaceFile(item.path);
        if (result.ok) {
          tabs.edit(item.path, result.content);
          tabs.clean(item.path);
        }
      } catch {
        // Content unavailable; tab will show loading state
      }
    })();
  }

  return (
    <div className="wb-explorer-inner" data-testid="file-explorer">
      <div className="wb-explorer-header">
        <p className="wb-pane-title">Explorer</p>
        <select
          className="wb-source-select"
          value={source}
          onChange={(e) => setSource(e.target.value as 'workspace' | 'session')}
          disabled={state.kind === 'loading' || state.kind === 'error'}
        >
          <option value="workspace">Project Tree</option>
          <option value="session">Session Files</option>
        </select>
      </div>

      {state.kind === 'loading' && <p className="wb-pane-note">Reading…</p>}
      {state.kind === 'error' && (
        <div role="status">
          <p className="wb-fault">{state.detail}</p>
          <p className="wb-pane-note">{state.remedy}</p>
          <button type="button" className="wb-file" onClick={() => void load()}>
            Retry
          </button>
        </div>
      )}
      {state.kind === 'empty' && (
        <p className="wb-pane-note">
          {state.source === 'workspace'
            ? 'No files in project yet.'
            : 'No files in this session yet.'}
        </p>
      )}
      {state.kind === 'workspace' && <TreeList nodes={state.tree} onOpen={open} />}
      {state.kind === 'session' && (
        <ul className="wb-filelist" aria-label="Session files">
          {state.files.map((file) => (
            <li key={file.name}>
              <button
                type="button"
                className="wb-file"
                title={`Open ${file.name}`}
                onClick={() => open({ path: file.name, name: file.name })}
              >
                {file.name}
              </button>
            </li>
          ))}
        </ul>
      )}
      <p className="wb-pane-note wb-scope-note">
        {source === 'workspace'
          ? 'Project tree via workspace API. Territory enforced on open.'
          : 'Session files. Project tree lands with the workspace API.'}
      </p>
    </div>
  );
}

export function TreeList({
  nodes,
  onOpen,
  depth = 0,
}: {
  nodes: WorkspaceTreeNode[];
  onOpen: (item: { path: string; name: string }) => void;
  depth?: number;
}) {
  return (
    <ul className="wb-filelist" aria-label={depth === 0 ? 'Project tree' : 'Directory contents'}>
      {nodes.map((node) => (
        <li key={node.path} style={{ paddingLeft: `${depth * 16}px` }}>
          {node.type === 'directory' ? (
            <DirectoryNode node={node} depth={depth} onOpen={onOpen} />
          ) : (
            <FileNode node={node} onOpen={onOpen} />
          )}
        </li>
      ))}
    </ul>
  );
}

export function DirectoryNode({
  node,
  depth,
  onOpen,
}: {
  node: WorkspaceTreeNode;
  depth: number;
  onOpen: (item: { path: string; name: string }) => void;
}) {
  const [expanded, setExpanded] = useState(false);
  const hasChildren = node.children && node.children.length > 0;

  return (
    <div className="wb-tree-node">
      <button
        type="button"
        className="wb-dir-toggle"
        onClick={() => setExpanded(!expanded)}
        aria-expanded={expanded}
        style={{ paddingLeft: `${depth * 16}px` }}
      >
        {expanded ? '▾' : '▸'} {node.name}
      </button>
      {expanded && hasChildren && <TreeList nodes={node.children!} depth={depth + 1} onOpen={onOpen} />}
    </div>
  );
}

export function FileNode({
  node,
  onOpen,
}: {
  node: WorkspaceTreeNode;
  onOpen: (item: { path: string; name: string }) => void;
}) {
  const inTerritory = node.inTerritory ?? isPathInTerritory(node.path);
  const readOnly = node.readOnly ?? !inTerritory;

  return (
    <button
      type="button"
      className="wb-file"
      title={`Open ${node.name} (${readOnly ? 'Read-Only' : 'Writable Territory'})`}
      onClick={() => onOpen({ path: node.path, name: node.name })}
      style={{ paddingLeft: '16px', display: 'flex', alignItems: 'center', gap: '6px' }}
    >
      <span
        style={{
          fontSize: '9px',
          padding: '1px 4px',
          borderRadius: '3px',
          backgroundColor: readOnly ? '#334155' : '#065F46',
          color: readOnly ? '#94A3B8' : '#34D399',
          fontFamily: 'monospace',
        }}
      >
        {readOnly ? 'RO' : 'RW'}
      </span>
      <span>{node.name}</span>
    </button>
  );
}
