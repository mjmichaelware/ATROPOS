'use client';

import { ProjectHeader } from '@/components/project/project-header';
import { SixAnswersPanel, SixAnswer } from '@/components/ui/six-answers-panel';
import { ControlVerb } from '@/components/ui/control-verbs';
import { MessageSquare } from 'lucide-react';

export default function ConversationsPage({ params }: { params: { id: string } }) {
  const projectAnswers: SixAnswer = {
    objective: 'Maintain conversation history as one view into project execution.',
    currentOperation: 'No conversations yet. Create the first one.',
    reasoning: 'Conversation history belongs to the project and remains synchronized with work, files, and evidence.',
    progress: { percent: 0, stage: 'Planning' },
    nextAction: 'Start a new conversation or review project history.',
  };

  const conversationAnswers: SixAnswer = {
    objective: 'Display all conversations scoped to this project.',
    currentOperation: 'Idle - No conversations recorded.',
    reasoning: 'Every conversation is project-scoped and searchable by agent, type, and timestamp.',
    progress: { percent: 0, stage: 'Idle' },
    nextAction: 'Create your first project conversation.',
    evidence: { link: '#', label: 'View conversation evidence' },
  };

  return (
    <div className="space-y-8">
      <ProjectHeader
        projectName={`Project ${params.id}`}
        projectId={params.id}
        status="planning"
        answers={projectAnswers}
        trustIndicators={{
          authorityVerified: true,
          evidenceVerified: false,
          verificationComplete: false,
          policyCompliant: true,
          checkpointCurrent: true,
          recoveryAvailable: false,
          noSilentFailures: true,
        }}
        availableActions={['inspect']}
        compact={false}
      />

      <div className="px-8 space-y-6">
        <section className="space-y-3">
          <h2 className="text-2xl font-bold text-sg-neutral-900 dark:text-sg-neutral-50">
            Conversations
          </h2>
          <SixAnswersPanel answers={conversationAnswers} compact={false} expandable={true} />
        </section>

        <div className="text-center py-12 border border-dashed border-sg-neutral-300 dark:border-sg-neutral-700 rounded-lg bg-sg-neutral-50 dark:bg-sg-neutral-900">
          <MessageSquare className="w-16 h-16 text-sg-neutral-400 mx-auto mb-3" />
          <h3 className="text-lg font-semibold text-sg-neutral-900 dark:text-sg-neutral-50 mb-1">
            No conversations yet
          </h3>
          <p className="text-sg-neutral-600 dark:text-sg-neutral-400">
            Conversations are created during project collaboration and remain searchable by agent, type, and time.
          </p>
        </div>
      </div>
    </div>
  );
}
