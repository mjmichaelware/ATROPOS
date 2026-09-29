/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * The workbench explorer, v2 (F-WEB-004 complete).
 *
 * Now reads the project file tree from `/v1/workspace/tree` and falls back
 * to session files when the tree endpoint is unavailable. Opens files via
 * `/v1/workspace/file` so the bridge can enforce territory and attestation.
 */

'use client';

import { useCallback, useEffect, useState } from 'react';
import { useWorkbenchTabs } from '@/lib/contexts/workbench-tabs-context';
import { readEngine } from '@/lib/engine/client';
import { readWorkspaceTree, readWorkspaceFile } from '@/lib/workspace/client';

interface FilesPayload {
  ok: true;
  files: Array<{ name: string; size: number }>;
}

interface WorkspaceTreeNode {
  name: string;
  path: string;
  type: 'file' | 'directory';
  size?: number;
  children?: WorkspaceTreeNode[];
}

interface WorkspaceTreePayload {
  ok: true;
  tree: WorkspaceTreeNode[];
}

type ExplorerState =
  | { kind: 'loading' }
  | { kind: 'error'; detail: string; remedy: string }
  | { kind: 'empty'; source: 'session' | 'workspace' }
  | { kind: 'workspace'; tree: WorkspaceTreeNode[] }
  | { kind: 'session'; files: FilesPayload['files'] };

function flattenTree(nodes: WorkspaceTreeNode[], prefix = ''): Array<{ path: string; name: string; depth: number }> {
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
      if (!result.ok) {
        setState({ kind: 'error', detail: result.detail, remedy: result.remedy });
        setSource('session');
      } else if (result.data.tree.length === 0) {
        setState({ kind: 'empty', source: 'workspace' });
        setSource('workspace');
      } else {
        setState({ kind: 'workspace', tree: result.data.tree });
        setSource('workspace');
      }
    } catch (error) {
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
        if (!result.ok || result.data.tree.length === 0) {
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
          setState({ kind: 'workspace', tree: result.data.tree });
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

  function renderTree(nodes: WorkspaceTreeNode[], depth = 0): React.ReactNode {
    return (
      <ul className="wb-filelist" aria-label={depth === 0 ? 'Project tree' : 'Directory contents'}>
        {nodes.map((node) => (
          <li key={node.path} style={{ paddingLeft: `${depth * 16}px` }}>
            {node.type === 'directory' ? (
              <DirectoryNode node={node} depth={depth} onOpen={open} />
            ) : (
              <FileNode node={node} onOpen={open} />
            )}
          </li>
        ))}
      </ul>
    );
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
      {state.kind === 'workspace' && renderTree(state.tree)}
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

function DirectoryNode({
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
      {expanded && hasChildren && renderTree(node.children!, depth + 1)}
    </div>
  );
}

function FileNode({
  node,
  onOpen,
}: {
  node: WorkspaceTreeNode;
  onOpen: (item: { path: string; name: string }) => void;
}) {
  return (
    <button
      type="button"
      className="wb-file"
      title={`Open ${node.name}`}
      onClick={() => onOpen({ path: node.path, name: node.name })}
      style={{ paddingLeft: '16px' }}
    >
      {node.name}
    </button>
  );
}