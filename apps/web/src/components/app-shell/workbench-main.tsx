/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * The main content region under both layouts.
 *
 * The AppShell is a server component and cannot read the client-side layout
 * preference, so this small client boundary owns the branch: session layout
 * passes children through untouched; workbench layout frames them in the
 * four-pane shell (F-WEB-003). Route identity never changes with the layout —
 * the same URL renders the same page either way, which makes the switch
 * reversible without losing the operator's place.
 *
 * Center-pane rule (F-WEB-005): an open file tab replaces the routed page in
 * the center until every tab closes, mirroring how an editor takes over the
 * center of VS Code. With no tabs open the routed page is the center content,
 * so switching layouts never blanks the surface.
 */
'use client';

import { type ReactNode } from 'react';
import { useLayoutTheme } from '@/lib/contexts/layout-theme-context';
import { WorkbenchTabsProvider, useWorkbenchTabs } from '@/lib/contexts/workbench-tabs-context';
import { WorkbenchShell } from '@/components/workbench/workbench-shell';
import { FileExplorer } from '@/components/workbench/file-explorer';
import { EditorTabs } from '@/components/workbench/editor-tabs';
import { LogPanel } from '@/components/workbench/log-panel';
import { CheckpointRail } from '@/components/checkpoint/checkpoint-rail';
import { BridgeApprovalList } from '@/components/approvals/bridge-approval-list';
import { InterruptControls } from '@/components/work-queue/interrupt-controls';
import { VerbosityControl } from '@/components/disclosure/verbosity-control';

export function WorkbenchMain({ children }: { children: ReactNode }) {
  const { layout } = useLayoutTheme();

  if (layout === 'session') {
    return <>{children}</>;
  }

  return (
    <WorkbenchTabsProvider>
      <WorkbenchBody>{children}</WorkbenchBody>
    </WorkbenchTabsProvider>
  );
}

function WorkbenchBody({ children }: { children: ReactNode }) {
  const { store, open } = useWorkbenchTabs();
  const hasOpenTabs = store.tabs.length > 0;

  return (
    <WorkbenchShell
      explorer={<FileExplorer onOpen={open} />}
      editor={
        hasOpenTabs ? (
          <EditorTabs />
        ) : (
          <div className="wb-page">{children}</div>
        )
      }
      logs={<LogPanel />}
      aiRail={
        <div className="wb-airail-inner">
          {/* F-WEB-007: checkpoint (resume) · approvals (decide) — the proof
              rail. Thinking drawer needs a node id, which the routed page
              owns; it mounts inside the rail once a node is in focus. */}
          <CheckpointRail />
          <BridgeApprovalList />
          <InterruptControls />
          <VerbosityControl />
        </div>
      }
    />
  );
}
