/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * Developer Tools — SpecGraph container (F-WEB-010).
 *
 * SpecGraph is isolated at /developer/specgraph behind a hidden Developer Tools
 * item. The shell already existed and was reused unchanged (per batch record).
 * This page mounts the SpecGraph views rebuilt on ATROPOS design tokens only.
 *
 * Logic modules (lib/graph/*, planning cycle, execution receipts/redaction,
 * ETag/idempotency) are kept; views are rebuilt on ATROPOS tokens.
 */
'use client';

import { useEffect, useState } from 'react';
import {
  specgraph,
  type SpecGraphProject,
  type SpecGraphExecution,
} from '@/lib/specgraph/client';
import { DeveloperToolsContainer } from '@/components/dev-tools/developer-tools-container';

export default function SpecGraphPage() {
  const [projects, setProjects] = useState<SpecGraphProject[]>([]);
  const [selectedProject, setSelectedProject] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const result = await specgraph.projects();
      if (cancelled) return;
      if (result.ok) {
        setProjects(result.data.projects);
        if (result.data.projects.length > 0) {
          setSelectedProject(result.data.projects[0].id);
        }
      } else {
        setError(`${result.detail} ${result.remedy}`);
      }
      setLoading(false);
    })();
    return () => { cancelled = true; };
  }, []);

  if (loading) {
    return (
      <div className="p-8 text-center">
        <p className="text-sg-neutral-600 dark:text-sg-neutral-400">Loading SpecGraph…</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-8" role="status">
        <p className="font-semibold text-sg-red-900 dark:text-sg-red-100">
          SpecGraph unavailable
        </p>
        <p className="text-sg-red-700 dark:text-sg-red-200">{error}</p>
      </div>
    );
  }

  return (
    <DeveloperToolsContainer
      projects={projects}
      selectedProject={selectedProject}
      onSelectProject={setSelectedProject}
    />
  );
}
