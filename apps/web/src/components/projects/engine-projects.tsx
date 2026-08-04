/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import { useEffect, useState } from 'react';
import { engine, type EngineFailure, type EngineProjectsPayload } from '@/lib/engine/client';

/**
 * The durable project registry, read from the engine.
 *
 * HOE-A03 makes the project the durable organisational boundary and requires it
 * to survive restart. The registry on disk already is that boundary; what was
 * missing was a surface that reads it. The web's `useProjects()` hook called
 * `/api/atropos/projects`, a route that does not exist, so this page could only
 * ever render an error toast.
 *
 * Three states, none collapsed into another, because §4.1 forbids reporting a
 * fault as a nominal one:
 *  - engine unreachable   -> say so, with the remedy
 *  - registry unreadable  -> a fault, said plainly, not an empty list
 *  - registry empty       -> a real and nominal state
 */
export function EngineProjects() {
  const [payload, setPayload] = useState<EngineProjectsPayload | null>(null);
  const [failure, setFailure] = useState<EngineFailure | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const result = await engine.projects();
      if (cancelled) return;
      if (result.ok) {
        setPayload(result.data);
        setFailure(null);
      } else {
        setPayload(null);
        setFailure(result);
      }
      setLoading(false);
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  if (loading) {
    return <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">Reading the project registry…</p>;
  }

  if (failure) {
    return (
      <div
        role="status"
        className="space-y-1 rounded-lg border border-sg-amber-300 bg-sg-amber-50 p-4 dark:border-sg-amber-900 dark:bg-sg-amber-900/20"
      >
        <p className="font-semibold text-sg-amber-900 dark:text-sg-amber-100">
          Engine not answering — the project registry was not read
        </p>
        <p className="text-sm text-sg-amber-800 dark:text-sg-amber-200">{failure.detail}</p>
        <p className="text-sm text-sg-amber-800 dark:text-sg-amber-200">{failure.remedy}</p>
      </div>
    );
  }

  if (!payload) return null;

  if (!payload.readable) {
    return (
      <div
        role="alert"
        className="rounded-lg border border-sg-red-300 bg-sg-red-50 p-4 dark:border-sg-red-900 dark:bg-sg-red-900/20"
      >
        <p className="font-semibold text-sg-red-900 dark:text-sg-red-100">
          Project registry unreadable
        </p>
        <p className="text-sm text-sg-red-800 dark:text-sg-red-200">
          This is a fault, not an empty registry. No project list is being shown because none
          could be read.
        </p>
      </div>
    );
  }

  if (payload.projects.length === 0) {
    return (
      <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">
        No projects registered yet. Create one from the engine:{' '}
        <code className="font-mono text-xs">/project new &lt;name&gt; &lt;objective&gt;</code>.
      </p>
    );
  }

  return (
    <ul className="grid grid-cols-1 gap-4 md:grid-cols-2">
      {payload.projects.map((project) => (
        <li
          key={project.id}
          className="space-y-2 rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800"
        >
          <div className="flex items-start justify-between gap-3">
            <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
              {project.name}
            </p>
            {/* Doc 4 term plus its non-colour signal; §E forbids colour alone. */}
            <span
              data-status={project.status}
              className="shrink-0 rounded border border-sg-neutral-300 px-2 py-0.5 text-xs uppercase tracking-wide text-sg-neutral-700 dark:border-sg-neutral-700 dark:text-sg-neutral-300"
            >
              {project.statusLabel}
            </span>
          </div>
          {project.objective && (
            <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">
              {project.objective}
            </p>
          )}
          {/* §3.4: a project claiming completion it cannot prove must be
              visibly distinguishable from one that can. */}
          {!project.completionIsVerifiable && (
            <p className="text-xs font-medium text-sg-amber-700 dark:text-sg-amber-300">
              Completion for this project is not independently verifiable.
            </p>
          )}
        </li>
      ))}
    </ul>
  );
}
