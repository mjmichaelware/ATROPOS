'use client';

import { ReactNode, useState } from 'react';
import { SessionTabBar } from './session-tab-bar';
import { SplitPane } from './split-pane';
import { InspectorPane } from './inspector-pane';
import { AppHeader } from '../app-shell/app-header';

interface WorkspaceLayoutProps {
  children: ReactNode;
  projectId?: string;
  activeTab?: string;
}

export function WorkspaceLayout({ children, projectId, activeTab }: WorkspaceLayoutProps) {
  const [inspectorOpen, setInspectorOpen] = useState(false);
  const [inspectorContent, setInspectorContent] = useState<ReactNode>(null);

  return (
    <div className="workspace-layout">
      <AppHeader onInspectorToggle={() => setInspectorOpen(!inspectorOpen)} />

      {projectId && (
        <SessionTabBar projectId={projectId} activeTab={activeTab} />
      )}

      <SplitPane
        left={
          <div className="workspace-main">
            {children}
          </div>
        }
        right={
          inspectorOpen && (
            <InspectorPane
              onClose={() => setInspectorOpen(false)}
              content={inspectorContent}
            />
          )
        }
        rightOpen={inspectorOpen}
      />

      <style jsx>{`
        .workspace-layout {
          display: flex;
          flex-direction: column;
          height: 100vh;
          background: var(--sg-surface-canvas);
        }

        .workspace-main {
          flex: 1;
          overflow: auto;
          padding: var(--sg-space-4);
        }
      `}</style>
    </div>
  );
}
