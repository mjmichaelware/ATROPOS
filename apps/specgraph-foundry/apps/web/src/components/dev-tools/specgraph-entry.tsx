'use client';

import Link from 'next/link';
import { useSessionState } from '@/lib/contexts/session-state-context';
import {
  projectGraphRoute,
  projectHandoffRoute,
  projectResearchRoute,
  projectRoutingRoute,
  projectSourcesRoute,
} from '@/components/navigation/routes';

/**
 * The SpecGraph subsystem entry point.
 *
 * §1.3: "SpecGraph is an engine inside ATROPOS, not the application identity."
 * §12.2 keeps compiler outputs, dependency graphs and source authority
 * available without letting them become the primary operating experience, so
 * every SpecGraph workspace is reached from here rather than from the project
 * tab strip it used to occupy.
 */
export function SpecGraphEntry() {
  const { session } = useSessionState();
  const projectId = session.activeProjectId;

  if (!projectId) {
    // No fabricated destination: SpecGraph workspaces are project-scoped, and
    // a link that cannot resolve is worse than a stated precondition.
    return (
      <div className="p-4 border border-sg-neutral-200 dark:border-sg-neutral-800 rounded-lg">
        <h3 className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50 mb-1">
          SpecGraph
        </h3>
        <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">
          SpecGraph workspaces are scoped to a project. Open a project first and
          this section will link to its sources, research, graph, handoff and
          routing surfaces.
        </p>
      </div>
    );
  }

  const workspaces = [
    {
      label: 'Sources',
      href: projectSourcesRoute(projectId),
      description: 'Ingested documents and source authority.',
    },
    {
      label: 'Research',
      href: projectResearchRoute(projectId),
      description: 'Extraction tasks and dimension status.',
    },
    {
      label: 'Graph',
      href: projectGraphRoute(projectId),
      description: 'Atom and dependency visualisation.',
    },
    {
      label: 'Handoff',
      href: projectHandoffRoute(projectId),
      description: 'Exports, bindings and execution runs.',
    },
    {
      label: 'Routing',
      href: projectRoutingRoute(projectId),
      description: 'Renderer and provider routing decisions.',
    },
  ];

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
      {workspaces.map((workspace) => (
        <Link
          key={workspace.label}
          href={workspace.href}
          className="block p-4 border border-sg-neutral-200 dark:border-sg-neutral-800 rounded-lg hover:border-sg-red-400 dark:hover:border-sg-red-600 transition-colors"
        >
          <h3 className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50 mb-1">
            {workspace.label}
          </h3>
          <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">
            {workspace.description}
          </p>
        </Link>
      ))}
    </div>
  );
}
