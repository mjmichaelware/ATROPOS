'use client';

import { ProjectHeader } from '@/components/project/project-header';
import { SixAnswersPanel, SixAnswer } from '@/components/ui/six-answers-panel';
import { ControlVerb } from '@/components/ui/control-verbs';
import { Users, Plus } from 'lucide-react';

export default function AgentsPage({ params }: { params: { id: string } }) {
  const projectAnswers: SixAnswer = {
    objective: 'Assign specialized agents to project responsibilities.',
    currentOperation: 'No agents assigned. Ready to assign roles.',
    reasoning: 'Agents represent specialized responsibilities, not personalities. One primary responsibility per agent.',
    progress: { percent: 0, stage: 'Planning' },
    nextAction: 'Assign your first agent to a project responsibility.',
  };

  const agentsAnswers: SixAnswer = {
    objective: 'Display every active agent, assigned responsibility, and workload.',
    currentOperation: 'Idle - No agents assigned to this project.',
    reasoning: 'Agent dashboard shows identity, workload, execution status, completion %, resource usage, and history.',
    progress: { percent: 0, stage: 'Idle' },
    nextAction: 'Assign agents to project tasks.',
    evidence: { link: '#', label: 'View agent history and artifacts' },
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
            Agents
          </h2>
          <SixAnswersPanel answers={agentsAnswers} compact={false} expandable={true} />
        </section>

        <button className="inline-flex items-center gap-2 px-4 py-2 bg-sg-red-600 text-white rounded-lg hover:bg-sg-red-700 transition-colors font-semibold">
          <Plus className="w-5 h-5" />
          Assign Agent
        </button>

        <div className="text-center py-12 border border-dashed border-sg-neutral-300 dark:border-sg-neutral-700 rounded-lg bg-sg-neutral-50 dark:bg-sg-neutral-900">
          <Users className="w-16 h-16 text-sg-neutral-400 mx-auto mb-3" />
          <h3 className="text-lg font-semibold text-sg-neutral-900 dark:text-sg-neutral-50 mb-1">
            No agents assigned
          </h3>
          <p className="text-sg-neutral-600 dark:text-sg-neutral-400 mb-4">
            Agents execute specialized responsibilities. Assign agents to begin autonomous work.
          </p>
        </div>
      </div>
    </div>
  );
}
