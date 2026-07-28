'use client';

import { useEffect } from 'react';
import { Plus, Folder } from 'lucide-react';
import { SixAnswersPanel, SixAnswer } from '@/components/ui/six-answers-panel';
import { StatusBadge } from '@/components/ui/status-badge';
import { useProjects } from '@/lib/api-atropos/hooks';
import { useAppContext } from '@/lib/contexts/app-context';
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

  const activeProjects = projects?.filter((p) => p.status !== 'archived') ?? [];
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
    evidence: {
      link: '/history',
      label: 'View project history and events',
    },
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

      {/* Create Project Button */}
      <div className="flex gap-3">
        <Link
          href="/projects/new"
          className="inline-flex items-center gap-2 px-4 py-2 bg-sg-red-600 text-white rounded-lg hover:bg-sg-red-700 transition-colors font-semibold"
        >
          <Plus className="w-5 h-5" />
          Create Project
        </Link>
      </div>

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
            href="/projects/new"
            className="inline-flex items-center gap-2 px-4 py-2 bg-sg-red-600 text-white rounded-lg hover:bg-sg-red-700 transition-colors"
          >
            <Plus className="w-4 h-4" />
            Create First Project
          </Link>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {projects?.map((project) => (
            <Link
              key={project.id}
              href={`/projects/${project.id}/work`}
              className="block p-4 border border-sg-neutral-200 dark:border-sg-neutral-800 rounded-lg hover:border-sg-red-400 dark:hover:border-sg-red-600 transition-colors hover:shadow-md dark:hover:shadow-lg"
            >
              <div className="flex items-start justify-between mb-2">
                <h3 className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
                  {project.name}
                </h3>
                <StatusBadge status={project.status} size="sm" />
              </div>
              {project.description && (
                <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400 mb-3">
                  {project.description}
                </p>
              )}
              {project.six_answers?.progress && (
                <div className="text-xs text-sg-neutral-500">
                  {project.six_answers.progress.percent}% complete
                </div>
              )}
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
