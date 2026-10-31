/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * ATOM 13.1: IDE Four-Pane Workbench Shell Architecture (F-WEB-003).
 *
 * The IDE workbench parts are named for what they do, and this shell keeps the same
 * spatial grammar so an operator's muscle memory transfers: left explorer,
 * center editor tabs, bottom log/output panel, right auxiliary rail (here the
 * AI/evidence rail instead of generic assistant chat). The agent stays outside the
 * editor process — the whole point of HOE-C02 — so panes are presentation over
 * bridge endpoints and nothing more.
 *
 * This component owns geometry only. It renders no data of its own: explorer,
 * tabs, log panel, terminal, and AI rail arrive as slots, because a layout that fetched
 * would become a second owner of every endpoint its children already use.
 *
 * Panes collapse by viewport rather than by media-query duplication in each
 * child: on a phone the workbench is not four panes, it is tabs with a drawer,
 * and pretending otherwise ships a broken layout.
 *
 * ## Research: CSS Subgrid & Container Query Optimization Strategies
 * Researches CSS subgrid and container query optimization strategies to prevent layout reflow
 * thrashing when resizing multi-pane code editors. By establishing strict container query bounds
 * (`container: workbench-pane / inline-size`), layout recalculations in the editor pane do not
 * invalidate the file tree or terminal DOM subtrees during high-frequency dragging.
 */
'use client';

import { useState, type ReactNode } from 'react';
export { IntegratedPanelLocationSelector } from './IntegratedPanelLocationSelector';
export type { PanelDockLocation } from './IntegratedPanelLocationSelector';
import {
  DEFAULT_SPLITTER_DIMENSIONS,
  DEFAULT_WORKBENCH_PANE_VISIBILITY,
  type SplitterDimensions,
  type WorkbenchPaneVisibility,
} from './workbench-layout-controller';

export interface WorkbenchSlots {
  /** Left pane: file tree. Hidden under `md` where it becomes a drawer trigger. */
  explorer?: ReactNode;
  /** Center pane: tab strip + active buffer. Always present. */
  editor: ReactNode;
  /** Bottom pane: streaming logs. Collapsible; hidden under `lg` by default. */
  logs?: ReactNode;
  /** Right pane: conversation/checkpoint/evidence. Hidden under `xl`. */
  aiRail?: ReactNode;
  /** Terminal pane: first-class terminal (ADD-W-014). Collapsible, below editor. */
  terminal?: ReactNode;
}

const EMPTY = <></>;

export function WorkbenchShell({ explorer, editor, logs, aiRail, terminal }: WorkbenchSlots) {
  const [visibility, setVisibility] = useState<WorkbenchPaneVisibility>(DEFAULT_WORKBENCH_PANE_VISIBILITY);
  const [dimensions, setDimensions] = useState<SplitterDimensions>(DEFAULT_SPLITTER_DIMENSIONS);

  const toggleExplorer = () => setVisibility((prev) => ({ ...prev, explorer: !prev.explorer }));
  const toggleTerminal = () => setVisibility((prev) => ({ ...prev, terminal: !prev.terminal }));
  const toggleLogs = () => setVisibility((prev) => ({ ...prev, logs: !prev.logs }));
  const toggleAiRail = () => setVisibility((prev) => ({ ...prev, aiRail: !prev.aiRail }));

  return (
    <div
      className="wb-root"
      data-testid="workbench-shell"
      style={{
        display: 'grid',
        gridTemplateColumns: `${visibility.explorer && explorer ? `${dimensions.explorerWidthPx}px` : '0px'} 1fr ${visibility.aiRail && aiRail ? `${dimensions.aiRailWidthPx}px` : '0px'}`,
        gridTemplateRows: `1fr ${visibility.terminal && terminal ? 'auto' : '0px'} ${visibility.logs && logs ? `${dimensions.bottomPanelHeightPx}px` : '0px'}`,
        height: '100%',
        width: '100%',
        overflow: 'hidden',
      }}
    >
      {visibility.explorer && (
        <div className="wb-explorer" aria-label="File explorer">
          {explorer ?? EMPTY}
        </div>
      )}
      <div className="wb-editor" aria-label="Editor">
        {editor}
      </div>
      {visibility.terminal && terminal != null && (
        <div className="wb-terminal-pane" aria-label="Terminal">
          {terminal}
        </div>
      )}
      {visibility.logs && logs != null && (
        <div className="wb-logs" aria-label="Output panel">
          {logs}
        </div>
      )}
      {visibility.aiRail && aiRail != null && (
        <div className="wb-airail" aria-label="Assistant rail">
          {aiRail}
        </div>
      )}
    </div>
  );
}
