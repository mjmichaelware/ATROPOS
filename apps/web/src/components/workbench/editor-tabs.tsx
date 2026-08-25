/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * The center pane: a tab strip over read-mostly buffers (F-WEB-005).
 *
 * "Viewer + light edit scope" — the textarea is the light-edit scope, and its
 * changes are local until they go through the bridge file API. There is
 * deliberately no save-to-filesystem path here: writes cross the bridge or do
 * not happen, because a browser writing into the operator's workspace would
 * bypass territory and attribution.
 *
 * State lives in WorkbenchTabsProvider, not here: the explorer opens tabs and
 * this component renders them, so the store belongs to neither.
 */
'use client';

import { useWorkbenchTabs } from '@/lib/contexts/workbench-tabs-context';

export function EditorTabs() {
  const { store, focus, close, edit } = useWorkbenchTabs();
  const active =
    store.tabs.find((tab) => tab.path === store.activePath) ?? null;

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
        <textarea
          className="wb-buffer"
          aria-label={active.path}
          spellCheck={false}
          value={active.content ?? ''}
          onChange={(event) => edit(active.path, event.target.value)}
        />
      ) : null}
    </div>
  );
}
