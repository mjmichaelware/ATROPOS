/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * The center pane: a tab strip over read-mostly buffers (F-WEB-005 complete).
 *
 * Now saves via `/v1/workspace/file` so the bridge enforces territory and
 * writes an attestation. The surface never writes directly to the operator's
 * filesystem — all mutations cross the bridge.
 */

'use client';

import { useCallback, useEffect, useState } from 'react';
import { useWorkbenchTabs } from '@/lib/contexts/workbench-tabs-context';
import { writeWorkspaceFile } from '@/lib/workspace/client';

export function EditorTabs() {
  const { store, focus, close, edit, clean } = useWorkbenchTabs();
  const [saving, setSaving] = useState<Record<string, boolean>>({});
  const [saveError, setSaveError] = useState<Record<string, string>>({});

  const active =
    store.tabs.find((tab) => tab.path === store.activePath) ?? null;

  const save = useCallback(async (path: string) => {
    const tab = store.tabs.find((tab) => tab.path === path);
    if (!tab || tab.content === undefined) return;

    setSaving((prev) => ({ ...prev, [path]: true }));
    setSaveError((prev) => ({ ...prev, [path]: '' }));

    try {
      await writeWorkspaceFile(path, tab.content);
      clean(path);
      setSaveError((prev) => ({ ...prev, [path]: '' }));
    } catch (error) {
      setSaveError((prev) => ({ ...prev, [path]: error instanceof Error ? error.message : 'Save failed' }));
    } finally {
      setSaving((prev) => ({ ...prev, [path]: false }));
    }
  }, [store.tabs, clean]);

  // Auto-save on blur (optional, can be toggled)
  useEffect(() => {
    const handleBeforeUnload = () => {
      store.tabs.forEach((tab) => {
        if (tab.dirty && tab.content !== undefined) {
          // Fire-and-forget save on unload
          writeWorkspaceFile(tab.path, tab.content).catch(() => {});
        }
      });
    };
    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => window.removeEventListener('beforeunload', handleBeforeUnload);
  }, [store.tabs]);

  if (store.tabs.length === 0) {
    return (
      <div className="wb-buffer wb-buffer-empty" data-testid="editor-tabs">
        No files open. Pick one from the explorer.
      </div>
    );
  }

  return (
    <div className="wb-tabs" data-testid="editor-tabs">
      <div role="tablist" aria-label="Open files" className="wb-tabstrip">
        {store.tabs.map((tab) => (
          <div
            key={tab.path}
            role="tab"
            aria-selected={tab.path === store.activePath}
            title={tab.path}
            tabIndex={0}
            className={tab.path === store.activePath ? 'wb-tab wb-tab-active' : 'wb-tab'}
            onClick={() => focus(tab.path)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                focus(tab.path);
              }
            }}
          >
            {tab.title}
            {/* Dirty marker is text, not color-only (§E). */}
            {tab.dirty && (
              <span className="wb-dirty" aria-label="unsaved changes">●</span>
            )}
            {saving[tab.path] && <span className="wb-saving" aria-label="saving">⟳</span>}
            {saveError[tab.path] && (
              <span className="wb-save-error" aria-label="save error">⚠</span>
            )}
            <button
              type="button"
              aria-label={`Close ${tab.title}`}
              className="wb-tab-close"
              onClick={(event) => {
                event.stopPropagation();
                close(tab.path);
              }}
            >
              ×
            </button>
          </div>
        ))}
      </div>

      {active?.content === undefined ? (
        <div className="wb-buffer wb-buffer-loading" aria-busy="true">
          {active?.path} — contents unavailable in this build (no workspace
          read endpoint on the bridge yet).
        </div>
      ) : active ? (
        <div className="wb-editor-wrapper">
          <div className="wb-editor-toolbar">
            <span className="wb-editor-path" title={active.path}>{active.path}</span>
            {active.dirty && (
              <button
                type="button"
                className="wb-save-btn"
                onClick={() => save(active.path)}
                disabled={saving[active.path]}
                aria-label="Save (Ctrl+S)"
              >
                {saving[active.path] ? 'Saving…' : 'Save'}
              </button>
            )}
            {saveError[active.path] && (
              <span className="wb-save-error-msg" role="alert">
                {saveError[active.path]}
              </span>
            )}
          </div>
          <textarea
            className="wb-buffer"
            aria-label={active.path}
            spellCheck={false}
            value={active.content ?? ''}
            onChange={(event) => edit(active.path, event.target.value)}
            onKeyDown={(event) => {
              if ((event.metaKey || event.ctrlKey) && event.key === 's') {
                event.preventDefault();
                save(active.path);
              }
            }}
          />
        </div>
      ) : null}
    </div>
  );
}