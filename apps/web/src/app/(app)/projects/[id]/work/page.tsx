'use client';

import { Metadata } from 'next';
import { ProjectHeader } from '@/components/project/project-header';
import { SixAnswersPanel, SixAnswer } from '@/components/ui/six-answers-panel';
import { StatusBadge } from '@/components/ui/status-badge';
import { ControlVerbs, ControlVerb } from '@/components/ui/control-verbs';
import { Plus, Filter } from 'lucide-react';

export default function WorkPage({ params }: { params: { id: string } }) {
  // Example project data (TODO: Replace with real API call)
  const projectName = `Project ${params.id}`;
  const projectStatus = 'planning' as const;

  const projectAnswers: SixAnswer = {
    objective: 'Execute the planned workflow for this project with autonomous agents.',
    currentOperation: 'No active work items. Ready to assign or create tasks.',
    reasoning: 'Work items represent the human-directed tasks that ATROPOS executes with autonomous agents.',
    progress: { percent: 0, stage: 'Planning' },
    nextAction: 'Create your first work item to begin autonomous execution.',
    evidence: {
      link: '#',
      label: 'View work execution history',
    },
  };

  const trustIndicators = {
    authorityVerified: true,
    evidenceVerified: true,
    verificationComplete: false,
    policyCompliant: true,
    checkpointCurrent: true,
    recoveryAvailable: false,
    noSilentFailures: true,
  };

  const workAnswers: SixAnswer = {
    objective: 'Display active goals, queued work, approvals, and pending decisions.',
    currentOperation: 'Idle - No work items to display.',
    reasoning: 'Work is the human queue of attention, not the internal scheduler log.',
    progress: { percent: 0, stage: 'Idle' },
    nextAction: 'Create a new work item or import existing tasks.',
  };

  const handleControlAction = (verb: ControlVerb) => {
    console.log('Control action:', verb);
    // TODO: Wire to actual API
  };

  return (
    <div className="space-y-8">
      {/* Project Header */}
      <ProjectHeader
        projectName={projectName}
        projectId={params.id}
        status={projectStatus}
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
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {/* To Do Column */}
            <div className="bg-sg-neutral-50 dark:bg-sg-neutral-900 border border-sg-neutral-200 dark:border-sg-neutral-800 rounded-lg p-4">
              <h4 className="text-sm font-semibold text-sg-neutral-700 dark:text-sg-neutral-300 uppercase tracking-wider mb-4">
                To Do
              </h4>
              <div className="min-h-[200px] flex items-center justify-center">
                <p className="text-sm text-sg-neutral-500 text-center">No work items</p>
              </div>
            </div>

            {/* In Progress Column */}
            <div className="bg-sg-neutral-50 dark:bg-sg-neutral-900 border border-sg-neutral-200 dark:border-sg-neutral-800 rounded-lg p-4">
              <h4 className="text-sm font-semibold text-sg-neutral-700 dark:text-sg-neutral-300 uppercase tracking-wider mb-4">
                In Progress
              </h4>
              <div className="min-h-[200px] flex items-center justify-center">
                <p className="text-sm text-sg-neutral-500 text-center">No work items</p>
              </div>
            </div>

            {/* Done Column */}
            <div className="bg-sg-neutral-50 dark:bg-sg-neutral-900 border border-sg-neutral-200 dark:border-sg-neutral-800 rounded-lg p-4">
              <h4 className="text-sm font-semibold text-sg-neutral-700 dark:text-sg-neutral-300 uppercase tracking-wider mb-4">
                Done
              </h4>
              <div className="min-h-[200px] flex items-center justify-center">
                <p className="text-sm text-sg-neutral-500 text-center">No work items</p>
              </div>
            </div>
          </div>
        </section>
      </div>
    </div>
  );
}
