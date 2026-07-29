'use client';

import { useEffect, use } from 'react';
import { ProjectHeader } from '@/components/project/project-header';
import { SixAnswersPanel, SixAnswer } from '@/components/ui/six-answers-panel';
import { StatusBadge } from '@/components/ui/status-badge';
import { ControlVerb } from '@/components/ui/control-verbs';
import { useProject, useAgents } from '@/lib/api-atropos/hooks';
import { useAppContext } from '@/lib/contexts/app-context';
import { Users, Plus } from 'lucide-react';

export default function AgentsPage({ params }: { params: Promise<{ id: string }> }) {
  const { id: projectId } = use(params);
  const { data: project, loading: projectLoading, error: projectError } = useProject(projectId);
  const { data: agents, loading: agentsLoading, error: agentsError } = useAgents(projectId);
  const { addError } = useAppContext();

  useEffect(() => {
    if (projectError) {
      addError({
        message: 'Failed to load project',
        context: 'Agents page',
        can_retry: true,
      });
    }
  }, [projectError, addError]);

  useEffect(() => {
    if (agentsError) {
      addError({
        message: 'Failed to load agents',
        context: 'Agents page',
        can_retry: true,
      });
    }
  }, [agentsError, addError]);

  const projectAnswers: SixAnswer = project?.six_answers || {
    objective: 'Assign specialized agents to project responsibilities.',
    currentOperation: agents && agents.length > 0
      ? `${agents.length} agent${agents.length !== 1 ? 's' : ''} assigned`
      : 'No agents assigned. Ready to assign roles.',
    reasoning: 'Agents represent specialized responsibilities, not personalities. One primary responsibility per agent.',
    progress: { percent: 0, stage: project?.status ?? 'Planning' },
    nextAction: 'Assign your first agent to a project responsibility.',
  };

  const agentsAnswers: SixAnswer = {
    objective: 'Display every active agent, assigned responsibility, and workload.',
    currentOperation:
      agents && agents.length > 0
        ? `${agents.length} agent${agents.length !== 1 ? 's' : ''} assigned, ${agents.filter((a) => a.status === 'working').length} active`
        : 'Idle - No agents assigned to this project.',
    reasoning: 'Agent dashboard shows identity, workload, execution status, completion %, resource usage, and history.',
    progress: {
      percent: agents && agents.length > 0 ? 50 : 0,
      stage: agents && agents.length > 0 ? 'Managing agents' : 'Idle',
    },
    nextAction: agents && agents.length > 0 ? 'Monitor agent workload and performance' : 'Assign agents to project tasks.',
  };

  return (
    <div className="space-y-8">
      <ProjectHeader
        projectName={`Project ${projectId}`}
        projectId={projectId}
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
            Agents
          </h2>
          <SixAnswersPanel answers={agentsAnswers} compact={false} expandable={true} />
        </section>

        <button className="inline-flex items-center gap-2 px-4 py-2 bg-sg-red-600 text-white rounded-lg hover:bg-sg-red-700 transition-colors font-semibold">
          <Plus className="w-5 h-5" />
          Assign Agent
        </button>

        {agentsLoading ? (
          <p className="text-sg-neutral-600 dark:text-sg-neutral-400">Loading agents...</p>
        ) : agents && agents.length > 0 ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {agents.map((agent) => (
              <div
                key={agent.id}
                className="p-4 border border-sg-neutral-200 dark:border-sg-neutral-800 rounded-lg hover:border-sg-red-400 dark:hover:border-sg-red-600 transition-colors"
              >
                <div className="flex items-start justify-between mb-3">
                  <div>
                    <h3 className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
                      {agent.name}
                    </h3>
                    {agent.description && (
                      <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400 mt-1">
                        {agent.description}
                      </p>
                    )}
                  </div>
                  <StatusBadge status={agent.status} size="sm" />
                </div>

                <div className="space-y-2 text-sm">
                  {agent.current_work && (
                    <div>
                      <p className="text-xs text-sg-neutral-600 dark:text-sg-neutral-400 uppercase tracking-wider">
                        Current work
                      </p>
                      <p className="text-sg-neutral-900 dark:text-sg-neutral-50">
                        {agent.current_work}
                      </p>
                    </div>
                  )}

                  <div className="grid grid-cols-3 gap-2 pt-2 border-t border-sg-neutral-200 dark:border-sg-neutral-800">
                    <div>
                      <p className="text-xs text-sg-neutral-600 dark:text-sg-neutral-400">Assigned</p>
                      <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
                        {agent.assigned_work}
                      </p>
                    </div>
                    <div>
                      <p className="text-xs text-sg-neutral-600 dark:text-sg-neutral-400">Completed</p>
                      <p className="font-semibold text-sg-green-600">{agent.completed_work}</p>
                    </div>
                    <div>
                      <p className="text-xs text-sg-neutral-600 dark:text-sg-neutral-400">Blocked</p>
                      <p className="font-semibold text-sg-red-600">{agent.blocked_work}</p>
                    </div>
                  </div>

                  {agent.resource_usage && (
                    <div className="pt-2 border-t border-sg-neutral-200 dark:border-sg-neutral-800">
                      <p className="text-xs text-sg-neutral-600 dark:text-sg-neutral-400 uppercase tracking-wider mb-1">
                        Resources
                      </p>
                      <div className="space-y-1 text-xs">
                        <p>
                          CPU:{' '}
                          <span className="font-semibold">
                            {agent.resource_usage.cpu_percent.toFixed(1)}%
                          </span>
                        </p>
                        <p>
                          Memory:{' '}
                          <span className="font-semibold">
                            {agent.resource_usage.memory_mb}MB
                          </span>
                        </p>
                        <p>
                          Tokens:{' '}
                          <span className="font-semibold">
                            {agent.resource_usage.tokens_used.toLocaleString()}
                          </span>
                        </p>
                      </div>
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="text-center py-12 border border-dashed border-sg-neutral-300 dark:border-sg-neutral-700 rounded-lg bg-sg-neutral-50 dark:bg-sg-neutral-900">
            <Users className="w-16 h-16 text-sg-neutral-400 mx-auto mb-3" />
            <h3 className="text-lg font-semibold text-sg-neutral-900 dark:text-sg-neutral-50 mb-1">
              No agents assigned
            </h3>
            <p className="text-sg-neutral-600 dark:text-sg-neutral-400 mb-4">
              Agents execute specialized responsibilities. Assign agents to begin autonomous work.
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
