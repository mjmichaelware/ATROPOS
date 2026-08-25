/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * The workbench explorer, v1 (F-WEB-004 partial).
 *
 * The atom's full scope is a project/git tree, which needs a workspace-tree
 * endpoint this bridge build does not expose yet — that is B-track work
 * running in parallel. What the bridge does expose today is `GET /v1/files`,
 * the session's uploaded files, so v1 renders exactly that list and says so.
 *
 * §4.1 governs every empty state here: "the engine answered and there are no
 * files", "the engine did not answer", and "this build has no tree endpoint"
 * are three different states and render as three different messages. A
 * fabricated tree would be worse than an honest gap.
 */
'use client';

import { useCallback, useEffect, useState } from 'react';
import { useWorkbenchTabs } from '@/lib/contexts/workbench-tabs-context';
import { readEngine } from '@/lib/engine/client';

interface FilesPayload {
  ok: true;
  files: Array<{ name: string; size: number }>;
}

type ExplorerState =
  | { kind: 'loading' }
  | { kind: 'error'; detail: string; remedy: string }
  | { kind: 'empty' }
  | { kind: 'files'; files: FilesPayload['files'] };

export function FileExplorer({
  onOpen,
}: {
  /** Called with the chosen path before/instead of opening a tab. */
  onOpen?: (path: string) => void;
}) {
  const tabs = useWorkbenchTabs();
  const [state, setState] = useState<ExplorerState>({ kind: 'loading' });

  const load = useCallback(async () => {
    setState({ kind: 'loading' });
    const result = await readEngine<FilesPayload>('/v1/files');
    if (!result.ok) {
      setState({ kind: 'error', detail: result.detail, remedy: result.remedy });
    } else if (result.data.files.length === 0) {
      setState({ kind: 'empty' });
    } else {
      setState({ kind: 'files', files: result.data.files });
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      // First load is automatic; retries below are explicit operator gestures.
      const result = await readEngine<FilesPayload>('/v1/files');
      if (cancelled) return;
      if (!result.ok) {
        setState({ kind: 'error', detail: result.detail, remedy: result.remedy });
      } else if (result.data.files.length === 0) {
        setState({ kind: 'empty' });
      } else {
        setState({ kind: 'files', files: result.data.files });
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  function open(name: string) {
    if (onOpen) onOpen(name);
    // Tab opens immediately; contents arrive when a reader endpoint exists.
    tabs.open(name);
  }

  return (
    <div className="wb-explorer-inner" data-testid="file-explorer">
      <p className="wb-pane-title">Explorer</p>
      {state.kind === 'loading' && <p className="wb-pane-note">Reading…</p>}
      {state.kind === 'error' && (
        <div role="status">
          <p className="wb-fault">{state.detail}</p>
          <p className="wb-pane-note">{state.remedy}</p>
          {/* A transient unreachable bridge should not need a page reload. */}
          <button type="button" className="wb-file" onClick={() => void load()}>
            Retry
          </button>
        </div>
      )}
      {state.kind === 'empty' && (
        <p className="wb-pane-note">No files in this session yet.</p>
      )}
      {state.kind === 'files' && (
        <ul className="wb-filelist" aria-label="Session files">
          {state.files.map((file) => (
            <li key={file.name}>
              <button
                type="button"
                className="wb-file"
                title={`Open ${file.name}`}
                onClick={() => open(file.name)}
              >
                {file.name}
              </button>
            </li>
          ))}
        </ul>
      )}
      {/* Honest scope statement, always visible so nobody expects a tree. */}
      <p className="wb-pane-note wb-scope-note">
        Session files. Project tree lands with the workspace API.
      </p>
    </div>
  );
}
