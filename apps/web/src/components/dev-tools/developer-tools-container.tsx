/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * Developer Tools container (F-WEB-010).
 *
 * SpecGraph only under /developer/specgraph. Hidden by default; mount
 * point is the hidden Developer Tools nav item. Views rebuilt on ATROPOS
 * design tokens only — no second design system.
 */
'use client';

import { useState } from 'react';
import { type SpecGraphProject } from '@/lib/specgraph/client';
import { SpecGraphProjectList } from './specgraph-project-list';
import { SpecGraphProjectView } from './specgraph-project-view';

interface DeveloperToolsContainerProps {
  projects: SpecGraphProject[];
  selectedProject: string | null;
  onSelectProject: (id: string) => void;
}

export function DeveloperToolsContainer({
  projects,
  selectedProject,
  onSelectProject,
}: DeveloperToolsContainerProps) {
  const [view, setView] = useState<'list' | 'project'>('list');

  return (
    <div className="sg-devtools" data-testid="developer-tools-container">
      <header className="sg-devtools-header">
        <h1 className="text-xl font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
          Developer Tools
        </h1>
        <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">
          SpecGraph integration — logic modules kept, views on ATROPOS tokens
        </p>
      </header>

      <div className="sg-devtools-grid">
        {/* Project list sidebar */}
        <aside className="sg-devtools-sidebar" aria-label="SpecGraph projects">
          <SpecGraphProjectList
            projects={projects}
            selectedProject={selectedProject}
            onSelectProject={(id) => {
              onSelectProject(id);
              setView('project');
            }}
          />
        </aside>

        {/* Main content area */}
        <main className="sg-devtools-main" role="main">
          {view === 'list' ? (
            <div className="sg-devtools-empty">
              <p className="text-sg-neutral-600 dark:text-sg-neutral-400">
                Select a project to explore sources, atoms, research, and executions.
              </p>
            </div>
          ) : selectedProject ? (
            <SpecGraphProjectView projectId={selectedProject} />
          ) : null}
        </main>
      </div>
    </div>
  );
}
