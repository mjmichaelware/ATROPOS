/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import React, { use } from 'react';
import { SovereignIdeWorkstation } from '@/components/ide/sovereign-ide-workstation';
import { useProject } from '@/lib/api-atropos/hooks';

export default function ProjectFilesPage({ params }: { params: Promise<{ id: string }> }) {
  const { id: projectId } = use(params);
  const { data: project } = useProject(projectId);

  return (
    <div className="p-4 md:p-6 space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-slate-100">
            Project Codebase & Workspace IDE
          </h1>
          <p className="text-xs text-slate-400">
            Real-time file tree explorer, Monaco editor, unified diff visualizer, and AST symbol inspector.
          </p>
        </div>
      </div>

      <SovereignIdeWorkstation
        projectId={projectId}
        projectName={project?.name || `Project ${projectId}`}
      />
    </div>
  );
}
