'use client';

import { useEffect } from 'react';
import { Plus, Folder } from 'lucide-react';
import { SixAnswersPanel, SixAnswer } from '@/components/ui/six-answers-panel';
import { StatusBadge } from '@/components/ui/status-badge';
import { WhyHowEvidence } from '@/components/ui/why-how-evidence';
import { globalRoutes } from '@/components/navigation/routes';
import { useProjects } from '@/lib/api-atropos/hooks';
import { useAppContext } from '@/lib/contexts/app-context';
import type { CanonicalStatus } from '@/lib/status-system';
import Link from 'next/link';

export default function ProjectsPage() {
  const { data: projects, loading, error } = useProjects();
  const { addError } = useAppContext();

  useEffect(() => {
    if (error) {
      addError({
        message: 'Failed to load projects',
        context: 'Projects page',
        can_retry: true,
      });
    }
  }, [error, addError]);

  // §3.3 has no 'archived' status; active means "not in a terminal state".
  const TERMINAL: CanonicalStatus[] = ['completed', 'failed', 'cancelled'];
  const activeProjects = projects?.filter((p) => !TERMINAL.includes(p.status)) ?? [];
  const completedCount = projects?.filter((p) => p.status === 'completed').length ?? 0;

  const pageAnswers: SixAnswer = {
    objective: 'Manage and organize all autonomous work across projects.',
    currentOperation: projects
      ? `${activeProjects.length} active project${activeProjects.length !== 1 ? 's' : ''}`
      : 'Loading projects...',
    reasoning: 'Projects are the durable organizational boundary for conversations, work, files, and evidence.',
    progress: {
      percent: projects && projects.length > 0 ? completedCount : 0,
      stage: projects && projects.length > 0 ? `${completedCount} completed` : 'No projects yet',
    },
    nextAction:
      projects && projects.length > 0
        ? 'Select a project to view work items or create a new one'
        : 'Create your first project to begin autonomous work',
  };

  return (
    <div className="space-y-8 p-8">
      {/* Page Context */}
      <section className="space-y-3">
        <h1 className="text-3xl font-bold text-sg-neutral-900 dark:text-sg-neutral-50">
          Projects
        </h1>
        <SixAnswersPanel answers={pageAnswers} compact={false} />
      </section>

      {/* Creation is not offered: there is no ATROPOS project store behind
          this surface, and a button that cannot do what it says is the same
          defect as a dead evidence link. */}
      <p className="flex items-center gap-2 text-sm text-sg-neutral-600 dark:text-sg-neutral-400">
        <Plus className="h-4 w-4" aria-hidden="true" />
        Project creation is not available from this surface yet.
      </p>

      {/* Projects Grid */}
      {loading ? (
        <div className="text-center py-12">
          <p className="text-sg-neutral-600 dark:text-sg-neutral-400">Loading projects...</p>
        </div>
      ) : projects && projects.length === 0 ? (
        <div className="text-center py-12 border border-dashed border-sg-neutral-300 dark:border-sg-neutral-700 rounded-lg bg-sg-neutral-50 dark:bg-sg-neutral-900">
          <Folder className="w-16 h-16 text-sg-neutral-400 mx-auto mb-3" />
          <h2 className="text-lg font-semibold text-sg-neutral-900 dark:text-sg-neutral-50 mb-1">
            No projects yet
          </h2>
          <p className="text-sg-neutral-600 dark:text-sg-neutral-400 mb-4">
            Create your first project to start autonomous work
          </p>
          <Link
            href={globalRoutes.projects}
            className="inline-flex items-center gap-2 px-4 py-2 text-sg-neutral-600 dark:text-sg-neutral-400"
            aria-disabled="true"
          >
            <Plus className="w-4 h-4" />
            Project creation is not available from this surface yet
          </Link>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {/* The card is a container rather than one big link: §5.3's
              explainability controls are buttons, and nesting them inside an
              anchor is both invalid markup and unreachable by keyboard. */}
          {projects?.map((project) => (
            <div
              key={project.id}
              className="space-y-3 p-4 border border-sg-neutral-200 dark:border-sg-neutral-800 rounded-lg hover:border-sg-red-400 dark:hover:border-sg-red-600 transition-colors hover:shadow-md dark:hover:shadow-lg"
            >
              <div className="flex items-start justify-between">
                <Link
                  href={`/projects/${project.id}/work`}
                  className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50 hover:text-sg-red-600"
                >
                  {project.name}
                </Link>
                <StatusBadge status={project.status} size="sm" />
              </div>
              {project.description && (
                <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">
                  {project.description}
                </p>
              )}
              {project.six_answers?.progress && (
                <div className="text-xs text-sg-neutral-500">
                  {project.six_answers.progress.percent}% complete
                </div>
              )}
              <WhyHowEvidence
                answers={project.six_answers}
                evidence={project.evidence}
                subject={`"${project.name}"`}
              />
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
