/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import Link from 'next/link';
import { useOptionalSessionState } from '@/lib/contexts/session-state-context';
import { globalRoutes, projectRoute } from '@/components/navigation/routes';

/**
 * The shell for a spine section that is scoped to a project.
 *
 * HOE-A02 puts Work, Conversations, Files and Agents in the primary spine, but
 * each of them describes work *within* a project — the pages that implement
 * them live at `/projects/[id]/…`. That leaves a real question the spine cannot
 * answer on its own: which project?
 *
 * The honest answer is to say so. When a project is active the section links
 * straight into it; when none is, the page states that plainly and offers the
 * project list. What it never does is invent a default project or render an
 * empty section as though the operator's work were finished — §4.1 treats a
 * fault presented as a nominal state as the failure, not the inconvenience.
 *
 * One owner rather than four near-identical pages: the four sections differ
 * only in name and destination, and four copies of this logic would drift the
 * moment one of them learned something the others did not.
 */
export function GlobalSection({
  title,
  description,
  segment,
}: {
  title: string;
  description: string;
  /** The `/projects/[id]/<segment>` page this section opens. */
  segment: 'work' | 'conversations' | 'files' | 'agents';
}) {
  const activeProjectId = useOptionalSessionState()?.session.activeProjectId ?? null;

  return (
    <div className="space-y-6 p-8">
      <header className="space-y-1">
        <h1 className="text-2xl font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
          {title}
        </h1>
        <p className="text-sg-neutral-600 dark:text-sg-neutral-400">{description}</p>
      </header>

      {activeProjectId ? (
        <Link
          href={`${projectRoute(activeProjectId)}/${segment}` as never}
          className="inline-flex items-center rounded-lg border border-sg-neutral-200 px-4 py-3 font-medium text-sg-neutral-900 hover:bg-sg-neutral-50 dark:border-sg-neutral-800 dark:text-sg-neutral-50 dark:hover:bg-sg-neutral-900"
        >
          Open {title.toLowerCase()} for the active project
        </Link>
      ) : (
        <div
          role="status"
          className="space-y-2 rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800"
        >
          <p className="font-medium text-sg-neutral-900 dark:text-sg-neutral-50">
            No active project
          </p>
          <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">
            {title} is scoped to a project. Choose one to see its {title.toLowerCase()} — this
            page is not showing an empty {title.toLowerCase()} list.
          </p>
          <Link
            href={globalRoutes.projects}
            className="inline-block text-sm font-medium underline underline-offset-4"
          >
            Go to Projects
          </Link>
        </div>
      )}
    </div>
  );
}
