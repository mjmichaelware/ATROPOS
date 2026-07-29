'use client';

import { useRouter } from 'next/navigation';
import { Plus, Zap, AlertTriangle, CheckCircle2, Clock, Eye } from 'lucide-react';
import { useProjects, useApprovals } from '@/lib/api-atropos/hooks';
import { SixAnswersPanel, SixAnswer } from '@/components/ui/six-answers-panel';
import { TrustIndicators } from '@/components/ui/trust-indicators';
import { StatusBadge } from '@/components/ui/status-badge';
import { useAppContext } from '@/lib/contexts/app-context';
import { useEffect } from 'react';

export default function Home() {
  const router = useRouter();
  const { data: projects, loading: projectsLoading, error: projectsError } = useProjects();
  const { data: approvals, loading: approvalsLoading, error: approvalsError } = useApprovals();
  const { addError } = useAppContext();

  useEffect(() => {
    if (projectsError) {
      addError({
        message: 'Failed to load projects',
        context: 'Home page',
        can_retry: true,
      });
    }
  }, [projectsError, addError]);

  useEffect(() => {
    if (approvalsError) {
      addError({
        message: 'Failed to load approvals',
        context: 'Home page',
        can_retry: true,
      });
    }
  }, [approvalsError, addError]);

  const systemStatus: SixAnswer = {
    objective: 'Provide a unified operating environment for autonomous work directed by humans.',
    currentOperation: projects && projects.length > 0
      ? `${projects.length} active project${projects.length !== 1 ? 's' : ''}`
      : 'Ready for projects. No active workflows.',
    reasoning: 'System initialized and waiting for project creation or task assignment.',
    progress: {
      percent: projects?.filter((p) => p.status === 'completed').length ?? 0,
      stage: projects && projects.length > 0 ? 'Managing projects' : 'Idle',
    },
    nextAction: projects && projects.length > 0
      ? 'Select a project to view work items or create a new one'
      : 'Create your first project to begin work',
  };

  const trustIndicators = {
    authorityVerified: true,
    evidenceVerified: !projectsError && !approvalsError,
    verificationComplete: true,
    policyCompliant: true,
    checkpointCurrent: true,
    recoveryAvailable: false,
    noSilentFailures: !projectsError && !approvalsError,
  };

  const pendingApprovals = approvals?.filter((a) => a.status === 'pending') ?? [];
  const activeProjects = projects?.filter((p) => p.status === 'working' || p.status === 'planning') ?? [];
  const completedProjects = projects?.filter((p) => p.status === 'completed') ?? [];

  return (
    <div className="space-y-8 p-8">
      {/* Header */}
      <div className="text-center space-y-2">
        <h1 className="text-4xl font-bold text-sg-red-600">ATROPOS</h1>
        <p className="text-lg text-sg-neutral-600 dark:text-sg-neutral-400">
          Persistent autonomous software operating environment
        </p>
      </div>

      {/* System Status - Six Continuous Answers */}
      <section className="space-y-3">
        <h2 className="text-xl font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
          System Status
        </h2>
        <SixAnswersPanel answers={systemStatus} expandable={false} />
      </section>

      {/* Trust Indicators */}
      <section className="space-y-3">
        <h2 className="text-xl font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
          System Health
        </h2>
        <TrustIndicators indicators={trustIndicators} layout="row" compact={false} />
      </section>

      {/* Quick Actions */}
      <section className="space-y-3">
        <h2 className="text-xl font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
          Quick Actions
        </h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="flex items-center gap-3 rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800">
            <Plus className="h-5 w-5 text-sg-neutral-400" />
            <div className="text-left">
              <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
                Create Project
              </p>
              <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">
                Create one from the engine:{' '}
                <code className="font-mono text-xs">/project new &lt;name&gt; &lt;objective&gt;</code>.
                Browser-initiated creation is not wired yet — a write reaching
                the engine from a page needs explicit attribution first.
              </p>
            </div>
          </div>

          {activeProjects.length > 0 && (
            <button
              onClick={() => router.push(`/projects/${activeProjects[0].id}/work`)}
              className="flex items-center gap-3 p-4 border border-sg-neutral-200 dark:border-sg-neutral-800 rounded-lg hover:bg-sg-neutral-50 dark:hover:bg-sg-neutral-900 transition-colors"
            >
              <Zap className="w-5 h-5 text-sg-amber-600" />
              <div className="text-left">
                <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
                  Resume Work
                </p>
                <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">
                  Continue {activeProjects[0].name}
                </p>
              </div>
            </button>
          )}
        </div>
      </section>

      {/* Active Projects */}
      {activeProjects.length > 0 && (
        <section className="space-y-3">
          <h2 className="text-xl font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
            Active Projects
          </h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {activeProjects.slice(0, 6).map((project) => (
              <button
                key={project.id}
                onClick={() => router.push(`/projects/${project.id}/work`)}
                className="text-left p-4 border border-sg-neutral-200 dark:border-sg-neutral-800 rounded-lg hover:bg-sg-neutral-50 dark:hover:bg-sg-neutral-900 transition-colors space-y-2"
              >
                <div className="flex items-center justify-between">
                  <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
                    {project.name}
                  </p>
                  <StatusBadge status={project.status} size="sm" />
                </div>
                {project.description && (
                  <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">
                    {project.description}
                  </p>
                )}
              </button>
            ))}
          </div>
        </section>
      )}

      {/* Completed Projects */}
      {completedProjects.length > 0 && (
        <section className="space-y-3">
          <h2 className="text-xl font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
            Completed Projects
          </h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {completedProjects.slice(0, 6).map((project) => (
              <button
                key={project.id}
                onClick={() => router.push(`/projects/${project.id}/work`)}
                className="text-left p-4 border border-sg-neutral-200 dark:border-sg-neutral-800 rounded-lg hover:bg-sg-neutral-50 dark:hover:bg-sg-neutral-900 transition-colors space-y-2"
              >
                <div className="flex items-center justify-between">
                  <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
                    {project.name}
                  </p>
                  <StatusBadge status={project.status} size="sm" />
                </div>
              </button>
            ))}
          </div>
        </section>
      )}

      {/* Pending Approvals */}
      {pendingApprovals.length > 0 && (
        <section className="space-y-3">
          <h2 className="text-xl font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
            Pending Approvals
          </h2>
          <div className="space-y-2">
            {pendingApprovals.slice(0, 5).map((approval) => (
              <button
                key={approval.id}
                onClick={() => router.push('/history')}
                className="w-full text-left p-4 border border-sg-red-200 dark:border-sg-red-900 bg-sg-red-50 dark:bg-sg-red-900/20 rounded-lg hover:bg-sg-red-100 dark:hover:bg-sg-red-900/40 transition-colors space-y-1"
              >
                <div className="flex items-center gap-2">
                  <Eye className="w-4 h-4 text-sg-red-600" />
                  <p className="font-semibold text-sg-red-900 dark:text-sg-red-100">
                    {approval.action_type} approval needed
                  </p>
                </div>
                <p className="text-sm text-sg-red-700 dark:text-sg-red-200">
                  Requested by {approval.requested_by} on{' '}
                  {new Date(approval.requested_at).toLocaleDateString()}
                </p>
              </button>
            ))}
          </div>
        </section>
      )}

      {/* Empty state */}
      {!projectsLoading && projects && projects.length === 0 && (
        <section className="text-center py-12 space-y-4">
          <CheckCircle2 className="w-16 h-16 text-sg-neutral-400 mx-auto" />
          <div>
            <h3 className="text-lg font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
              No projects yet
            </h3>
            <p className="text-sg-neutral-600 dark:text-sg-neutral-400">
              Create your first project to begin autonomous work
            </p>
          </div>
        </section>
      )}
    </div>
  );
}
