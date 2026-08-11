'use client';

import { SixAnswersPanel, SixAnswer } from '@/components/ui/six-answers-panel';
import { Zap, Plus } from 'lucide-react';

export default function AutomationPage() {
  const automationAnswers: SixAnswer = {
    objective: 'Display recurring tasks, background workflows, checkpoints, schedules, and notifications.',
    currentOperation: 'No automation configured. Ready to create workflows.',
    reasoning: 'Automation dashboard shows recurring execution history, trigger configuration, and checkpoint state.',
    progress: { percent: 0, stage: 'Setup' },
    nextAction: 'Create your first automated workflow.',
  };

  const workflowAnswers: SixAnswer = {
    objective: 'Manage recurring tasks and autonomous execution schedules.',
    currentOperation: 'Idle - No workflows scheduled.',
    reasoning: 'Workflows enable autonomous continuation after intermediate work, with checkpoints for safety and restart.',
    progress: { percent: 0, stage: 'Idle' },
    nextAction: 'Schedule your first automation workflow.',
  };

  return (
    <div className="space-y-8 p-8 max-w-4xl mx-auto">
      {/* Page Context */}
      <section className="space-y-3">
        <h1 className="text-3xl font-bold text-sg-neutral-900 dark:text-sg-neutral-50">
          Automation
        </h1>
        <SixAnswersPanel answers={automationAnswers} compact={false} />
      </section>

      {/* Workflow Management */}
      <section className="space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-2xl font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
            Scheduled Workflows
          </h2>
          <button className="inline-flex items-center gap-2 px-4 py-2 bg-sg-red-600 text-white rounded-lg hover:bg-sg-red-700 transition-colors font-semibold">
            <Plus className="w-5 h-5" />
            New Workflow
          </button>
        </div>

        <SixAnswersPanel answers={workflowAnswers} compact={false} expandable={true} />

        <div className="text-center py-12 border border-dashed border-sg-neutral-300 dark:border-sg-neutral-700 rounded-lg bg-sg-neutral-50 dark:bg-sg-neutral-900">
          <Zap className="w-16 h-16 text-sg-neutral-400 mx-auto mb-3" />
          <h3 className="text-lg font-semibold text-sg-neutral-900 dark:text-sg-neutral-50 mb-1">
            No workflows scheduled
          </h3>
          <p className="text-sg-neutral-600 dark:text-sg-neutral-400 mb-4">
            Create automated workflows to enable autonomous work execution and checkpoints.
          </p>
        </div>
      </section>
    </div>
  );
}
