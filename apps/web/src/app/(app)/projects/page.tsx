'use client';

import { Plus, Folder } from 'lucide-react';
import { SixAnswersPanel, SixAnswer } from '@/components/ui/six-answers-panel';
import { StatusBadge } from '@/components/ui/status-badge';
import Link from 'next/link';

export default function ProjectsPage() {
  // Six answers for the Projects overview page itself
  const pageAnswers: SixAnswer = {
    objective: 'Manage and organize all autonomous work across projects.',
    currentOperation: 'Ready to create new projects or open existing ones.',
    reasoning: 'Projects are the durable organizational boundary for conversations, work, files, and evidence.',
    progress: { percent: 0, stage: 'Idle - No active projects' },
    nextAction: 'Create your first project to begin autonomous work.',
    evidence: {
      link: '/history',
      label: 'View project history and events',
    },
  };

  // TODO: Replace with real project data from API
  const projects = [];

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
      {projects.length === 0 ? (
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
          {projects.map((project: any) => (
            <Link
              key={project.id}
              href={`/projects/${project.id}`}
              className="block p-4 border border-sg-neutral-200 dark:border-sg-neutral-800 rounded-lg hover:border-sg-red-400 dark:hover:border-sg-red-600 transition-colors hover:shadow-md dark:hover:shadow-lg"
            >
              <div className="flex items-start justify-between mb-2">
                <h3 className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
                  {project.name}
                </h3>
                <StatusBadge status={project.status} size="sm" />
              </div>
              <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400 mb-3">
                {project.description}
              </p>
              <div className="text-xs text-sg-neutral-500">
                {project.progress}% complete
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
