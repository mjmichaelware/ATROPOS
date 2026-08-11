'use client';

import { SixAnswersPanel, SixAnswer } from '@/components/ui/six-answers-panel';
import { Clock, Search } from 'lucide-react';

export default function HistoryPage() {
  const historyAnswers: SixAnswer = {
    objective: 'Display every important event within projects as a searchable, permanent record.',
    currentOperation: 'System ready to record events. No events yet.',
    reasoning:
      'History is permanent and searchable by timestamp, actor, action, evidence, affected artifacts, and result. Events never deleted.',
    progress: { percent: 0, stage: 'Recording' },
    nextAction: 'Create a project or workflow to begin recording events.',
  };

  const eventAnswers: SixAnswer = {
    objective: 'Present all events: timestamp, actor, action, evidence, artifacts, result.',
    currentOperation: 'Idle - No events recorded yet.',
    reasoning:
      'Every meaningful action in ATROPOS is recorded. Search and filter by project, agent, status, and time.',
    progress: { percent: 0, stage: 'Idle' },
    nextAction: 'Start project work to generate events.',
  };

  return (
    <div className="space-y-8 p-8 max-w-4xl mx-auto">
      {/* Page Context */}
      <section className="space-y-3">
        <h1 className="text-3xl font-bold text-sg-neutral-900 dark:text-sg-neutral-50">
          History
        </h1>
        <SixAnswersPanel answers={historyAnswers} compact={false} />
      </section>

      {/* Event Timeline */}
      <section className="space-y-4">
        <div className="flex gap-2">
          <div className="flex-1 relative">
            <Search className="absolute left-3 top-3 w-5 h-5 text-sg-neutral-400" />
            <input
              type="search"
              placeholder="Search events by project, agent, or status..."
              className="w-full pl-10 pr-4 py-2 border border-sg-neutral-300 dark:border-sg-neutral-700 rounded-lg bg-white dark:bg-sg-neutral-900 text-sg-neutral-900 dark:text-sg-neutral-50"
            />
          </div>
          <button className="px-4 py-2 border border-sg-neutral-300 dark:border-sg-neutral-700 rounded-lg hover:bg-sg-neutral-50 dark:hover:bg-sg-neutral-900 transition-colors">
            Filter
          </button>
        </div>

        <SixAnswersPanel answers={eventAnswers} compact={false} expandable={true} />

        <div className="text-center py-12 border border-dashed border-sg-neutral-300 dark:border-sg-neutral-700 rounded-lg bg-sg-neutral-50 dark:bg-sg-neutral-900">
          <Clock className="w-16 h-16 text-sg-neutral-400 mx-auto mb-3" />
          <h3 className="text-lg font-semibold text-sg-neutral-900 dark:text-sg-neutral-50 mb-1">
            No events recorded
          </h3>
          <p className="text-sg-neutral-600 dark:text-sg-neutral-400 mb-4">
            Events are recorded as you create projects, assign work, and execute workflows.
          </p>
          <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">
            Every event is permanent, searchable, and linked to evidence.
          </p>
        </div>
      </section>
    </div>
  );
}
