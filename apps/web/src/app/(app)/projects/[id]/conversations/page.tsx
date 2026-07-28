'use client';

import { useEffect } from 'react';
import { ProjectHeader } from '@/components/project/project-header';
import { SixAnswersPanel, SixAnswer } from '@/components/ui/six-answers-panel';
import { ControlVerb } from '@/components/ui/control-verbs';
import { useProject, useConversations } from '@/lib/api-atropos/hooks';
import { useAppContext } from '@/lib/contexts/app-context';
import { MessageSquare, MessageCircle } from 'lucide-react';

export default function ConversationsPage({ params }: { params: { id: string } }) {
  const { data: project, loading: projectLoading, error: projectError } = useProject(params.id);
  const { data: conversations, loading: convsLoading, error: convsError } = useConversations(
    params.id
  );
  const { addError } = useAppContext();

  useEffect(() => {
    if (projectError) {
      addError({
        message: 'Failed to load project',
        context: 'Conversations page',
        can_retry: true,
      });
    }
  }, [projectError, addError]);

  useEffect(() => {
    if (convsError) {
      addError({
        message: 'Failed to load conversations',
        context: 'Conversations page',
        can_retry: true,
      });
    }
  }, [convsError, addError]);

  const projectAnswers: SixAnswer = project?.six_answers || {
    objective: 'Maintain conversation history as one view into project execution.',
    currentOperation: conversations && conversations.length > 0
      ? `${conversations.length} conversation${conversations.length !== 1 ? 's' : ''}`
      : 'No conversations yet. Create the first one.',
    reasoning: 'Conversation history belongs to the project and remains synchronized with work, files, and evidence.',
    progress: {
      percent: 0,
      stage: project?.status ?? 'Planning',
    },
    nextAction: 'Start a new conversation or review project history.',
  };

  const conversationAnswers: SixAnswer = {
    objective: 'Display all conversations scoped to this project.',
    currentOperation:
      conversations && conversations.length > 0
        ? `${conversations.length} conversation${conversations.length !== 1 ? 's' : ''} recorded`
        : 'Idle - No conversations recorded.',
    reasoning: 'Every conversation is project-scoped and searchable by agent, type, and timestamp.',
    progress: {
      percent: 0,
      stage: conversations && conversations.length > 0 ? 'Active' : 'Idle',
    },
    nextAction: conversations && conversations.length > 0
      ? 'Review and search conversations'
      : 'Create your first project conversation.',
    evidence: { link: '/history', label: 'View conversation evidence' },
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

        {convsLoading ? (
          <p className="text-sg-neutral-600 dark:text-sg-neutral-400">Loading conversations...</p>
        ) : conversations && conversations.length > 0 ? (
          <div className="space-y-3">
            {conversations.map((conv) => (
              <div
                key={conv.id}
                className="p-4 border border-sg-neutral-200 dark:border-sg-neutral-800 rounded-lg hover:border-sg-red-400 dark:hover:border-sg-red-600 transition-colors cursor-pointer"
              >
                <div className="flex items-start justify-between mb-2">
                  <div className="flex items-start gap-3">
                    <MessageCircle className="w-5 h-5 text-sg-red-600 flex-shrink-0 mt-0.5" />
                    <div>
                      <h3 className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
                        {conv.title}
                      </h3>
                      <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">
                        {conv.message_count} message{conv.message_count !== 1 ? 's' : ''}
                      </p>
                    </div>
                  </div>
                  <div className="text-right">
                    <p className="text-xs text-sg-neutral-600 dark:text-sg-neutral-400">
                      Updated {new Date(conv.updated_at).toLocaleDateString()}
                    </p>
                  </div>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="text-center py-12 border border-dashed border-sg-neutral-300 dark:border-sg-neutral-700 rounded-lg bg-sg-neutral-50 dark:bg-sg-neutral-900">
            <MessageSquare className="w-16 h-16 text-sg-neutral-400 mx-auto mb-3" />
            <h3 className="text-lg font-semibold text-sg-neutral-900 dark:text-sg-neutral-50 mb-1">
              No conversations yet
            </h3>
            <p className="text-sg-neutral-600 dark:text-sg-neutral-400">
              Conversations are created during project collaboration and remain searchable by agent, type, and time.
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
