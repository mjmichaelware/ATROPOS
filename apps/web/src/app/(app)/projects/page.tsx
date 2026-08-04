'use client';

import { useEffect } from 'react';
import { Plus, Folder } from 'lucide-react';
import { SixAnswersPanel, SixAnswer } from '@/components/ui/six-answers-panel';
import { StatusBadge } from '@/components/ui/status-badge';
import { WhyHowEvidence } from '@/components/ui/why-how-evidence';
import { globalRoutes } from '@/components/navigation/routes';
import { EngineProjects } from '@/components/projects/engine-projects';
import { useAppContext } from '@/lib/contexts/app-context';
import type { CanonicalStatus } from '@/lib/status-system';
import Link from 'next/link';

export default function ProjectsPage() {
  return (
    <div className="space-y-6 p-8">
      <header className="space-y-1">
        <h1 className="text-2xl font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
          Projects
        </h1>
        <p className="text-sg-neutral-600 dark:text-sg-neutral-400">
          The durable project registry, read from the engine.
        </p>
      </header>
      <EngineProjects />
    </div>
  );
}
