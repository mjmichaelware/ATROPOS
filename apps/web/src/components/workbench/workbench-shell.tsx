/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * The four-pane workbench arrangement (F-WEB-003).
 *
 * VS Code's parts are named for what they do, and this shell keeps the same
 * spatial grammar so an operator's muscle memory transfers: left explorer,
 * center editor tabs, bottom log/output panel, right auxiliary rail (here the
 * AI/evidence rail instead of Copilot chat). The agent stays outside the
 * editor process — the whole point of HOE-C02 — so panes are presentation over
 * bridge endpoints and nothing more.
 *
 * This component owns geometry only. It renders no data of its own: explorer,
 * tabs, log panel and AI rail arrive as slots, because a layout that fetched
 * would become a second owner of every endpoint its children already use.
 *
 * Panes collapse by viewport rather than by media-query duplication in each
 * child: on a phone the workbench is not four panes, it is tabs with a drawer,
 * and pretending otherwise ships a broken layout.
 */
'use client';

import { type ReactNode } from 'react';

export interface WorkbenchSlots {
  /** Left pane: file tree. Hidden under `md` where it becomes a drawer trigger. */
  explorer?: ReactNode;
  /** Center pane: tab strip + active buffer. Always present. */
  editor: ReactNode;
  /** Bottom pane: streaming logs. Collapsible; hidden under `lg` by default. */
  logs?: ReactNode;
  /** Right pane: conversation/checkpoint/evidence. Hidden under `xl`. */
  aiRail?: ReactNode;
}

const EMPTY = <></>;

export function WorkbenchShell({ explorer, editor, logs, aiRail }: WorkbenchSlots) {
  return (
    <div className="wb-root" data-testid="workbench-shell">
      <div className="wb-explorer" aria-label="File explorer">
        {explorer ?? EMPTY}
      </div>
      <div className="wb-editor" aria-label="Editor">
        {editor}
      </div>
      {logs != null && (
        <div className="wb-logs" aria-label="Output panel">
          {logs}
        </div>
      )}
      {aiRail != null && (
        <div className="wb-airail" aria-label="Assistant rail">
          {aiRail}
        </div>
      )}
    </div>
  );
}
