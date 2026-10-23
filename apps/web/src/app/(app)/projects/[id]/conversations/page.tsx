/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import React, { use } from 'react';
import { SovereignConversationWorkstation } from '@/components/conversation/sovereign-conversation-workstation';
import { useProject } from '@/lib/api-atropos/hooks';

export default function ProjectConversationsPage({ params }: { params: Promise<{ id: string }> }) {
  const { id: projectId } = use(params);
  const { data: project } = useProject(projectId);

  return (
    <div className="p-4 md:p-6 space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-slate-100">
            Sovereign Autonomous Workstation
          </h1>
          <p className="text-xs text-slate-400">
            Streaming reasoning loop, tool execution timeline, and gated mutation approvals.
          </p>
        </div>
      </div>

      <div className="h-[80vh]">
        <SovereignConversationWorkstation
          projectId={projectId}
          projectName={project?.name || `Project ${projectId}`}
        />
      </div>
    </div>
  );
}
