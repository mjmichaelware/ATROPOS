'use client';

import { useEffect, useMemo, use } from 'react';
import { ProjectHeader } from '@/components/project/project-header';
import { SixAnswersPanel, SixAnswer } from '@/components/ui/six-answers-panel';
import { WorkItemCard } from '@/components/atropos/work-item-card';
import type { TrustIndicators } from '@/components/ui/trust-indicators';
import { ControlVerbs, ControlVerb } from '@/components/ui/control-verbs';
import { useProject, useWorkItems } from '@/lib/api-atropos/hooks';
import { useAppContext } from '@/lib/contexts/app-context';
import { Plus, Filter } from 'lucide-react';

export default function WorkPage({ params }: { params: Promise<{ id: string }> }) {
  const { id: projectId } = use(params);
  const { data: project, loading: projectLoading, error: projectError } = useProject(projectId);
  const { data: workItems, loading: itemsLoading, error: itemsError } = useWorkItems(projectId);
  const { addError } = useAppContext();

  useEffect(() => {
    if (projectError) {
      addError({
        message: 'Failed to load project',
        context: 'Work page',
        can_retry: true,
      });
    }
  }, [projectError, addError]);

  useEffect(() => {
    if (itemsError) {
      addError({
        message: 'Failed to load work items',
        context: 'Work page',
        can_retry: true,
      });
    }
  }, [itemsError, addError]);

  const todoItems = workItems?.filter((item) => item.status === 'idle') ?? [];
  const inProgressItems = workItems?.filter((item) => item.status === 'working') ?? [];
  const doneItems = workItems?.filter((item) => item.status === 'completed') ?? [];
  const completedPercent = workItems
    ? Math.round((doneItems.length / workItems.length) * 100)
    : 0;

  const projectAnswers: SixAnswer = project?.six_answers || {
    objective: 'Execute the planned workflow for this project with autonomous agents.',
    currentOperation: workItems && workItems.length > 0
      ? `${workItems.length} work item${workItems.length !== 1 ? 's' : ''}`
      : 'No active work items. Ready to assign or create tasks.',
    reasoning: 'Work items represent the human-directed tasks that ATROPOS executes with autonomous agents.',
    progress: { percent: completedPercent, stage: project?.status ?? 'Planning' },
    nextAction: workItems && workItems.length > 0
      ? 'Review and prioritize work items'
      : 'Create your first work item to begin autonomous execution.',
    evidence: project?.evidence,
  };

  // §4.2: only indicators this page can actually observe are claimed. The
  // rest are left undefined and render as "unknown" — asserting authority,
  // policy or checkpoint state that nothing verified is the exact false green
  // this surface is meant to expose.
  const trustIndicators: TrustIndicators = {
    evidenceVerified: projectError || itemsError ? false : undefined,
    verificationComplete: workItems
      ? workItems.length === doneItems.length
      : undefined,
    noSilentFailures: projectError || itemsError ? false : undefined,
  };

  const workAnswers: SixAnswer = {
    objective: 'Display active goals, queued work, approvals, and pending decisions.',
    currentOperation:
      workItems && workItems.length > 0
        ? `${inProgressItems.length} in progress, ${todoItems.length} queued`
        : 'Idle - No work items to display.',
    reasoning: 'Work is the human queue of attention, not the internal scheduler log.',
    progress: {
      percent: completedPercent,
      stage: workItems && workItems.length > 0 ? 'In progress' : 'Idle',
    },
    nextAction: workItems && workItems.length > 0
      ? 'Monitor active work and approvals'
      : 'Create a new work item or import existing tasks.',
  };

  const handleControlAction = (verb: ControlVerb) => {
    console.log('Control action:', verb);
    // TODO: Wire to actual API via useProjectActions hook
  };

  return (
    <div className="space-y-8">
      {/* Project Header */}
      <ProjectHeader
        projectName={project?.name ?? 'Unknown project'}
        projectId={projectId}
        status={project?.status ?? 'idle'}
        answers={projectAnswers}
        trustIndicators={trustIndicators}
        availableActions={['pause', 'cancel', 'inspect']}
        onAction={handleControlAction}
        compact={false}
      />

      {/* Work Page Section */}
      <div className="px-8 space-y-6">
        {/* Work Context */}
        <section className="space-y-3">
          <h2 className="text-2xl font-bold text-sg-neutral-900 dark:text-sg-neutral-50">
            Active Work
          </h2>
          <SixAnswersPanel answers={workAnswers} compact={false} expandable={true} />
        </section>

        {/* Filters + Actions */}
        <div className="flex items-center gap-3">
          <button className="inline-flex items-center gap-2 px-4 py-2 bg-sg-red-600 text-white rounded-lg hover:bg-sg-red-700 transition-colors font-semibold">
            <Plus className="w-5 h-5" />
            New Work Item
          </button>
          <button className="inline-flex items-center gap-2 px-3 py-2 border border-sg-neutral-300 dark:border-sg-neutral-700 rounded-lg hover:bg-sg-neutral-50 dark:hover:bg-sg-neutral-900 transition-colors">
            <Filter className="w-4 h-4" />
            Filter
          </button>
        </div>

        {/* Kanban Board */}
        <section className="space-y-4">
          <h3 className="text-lg font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
            Work Items
          </h3>
          {itemsLoading ? (
            <p className="text-sg-neutral-600 dark:text-sg-neutral-400">Loading work items...</p>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              {/* To Do Column */}
              <div className="bg-sg-neutral-50 dark:bg-sg-neutral-900 border border-sg-neutral-200 dark:border-sg-neutral-800 rounded-lg p-4">
                <h4 className="text-sm font-semibold text-sg-neutral-700 dark:text-sg-neutral-300 uppercase tracking-wider mb-4">
                  To Do ({todoItems.length})
                </h4>
                <div className="space-y-2 min-h-[200px]">
                  {todoItems.length === 0 ? (
                    <p className="text-sm text-sg-neutral-500 text-center py-8">No items</p>
                  ) : (
                    todoItems.map((item) => (
                      <WorkItemCard key={item.id} item={item} />
                    ))
                  )}
                </div>
              </div>

              {/* In Progress Column */}
              <div className="bg-sg-neutral-50 dark:bg-sg-neutral-900 border border-sg-neutral-200 dark:border-sg-neutral-800 rounded-lg p-4">
                <h4 className="text-sm font-semibold text-sg-neutral-700 dark:text-sg-neutral-300 uppercase tracking-wider mb-4">
                  In Progress ({inProgressItems.length})
                </h4>
                <div className="space-y-2 min-h-[200px]">
                  {inProgressItems.length === 0 ? (
                    <p className="text-sm text-sg-neutral-500 text-center py-8">No items</p>
                  ) : (
                    inProgressItems.map((item) => (
                      <WorkItemCard key={item.id} item={item} />
                    ))
                  )}
                </div>
              </div>

              {/* Done Column */}
              <div className="bg-sg-neutral-50 dark:bg-sg-neutral-900 border border-sg-neutral-200 dark:border-sg-neutral-800 rounded-lg p-4">
                <h4 className="text-sm font-semibold text-sg-neutral-700 dark:text-sg-neutral-300 uppercase tracking-wider mb-4">
                  Done ({doneItems.length})
                </h4>
                <div className="space-y-2 min-h-[200px]">
                  {doneItems.length === 0 ? (
                    <p className="text-sm text-sg-neutral-500 text-center py-8">No items</p>
                  ) : (
                    doneItems.map((item) => (
                      <WorkItemCard key={item.id} item={item} />
                    ))
                  )}
                </div>
              </div>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
