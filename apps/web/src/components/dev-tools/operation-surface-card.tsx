'use client';

import { Eye, Shield, Smartphone, Users, Images } from 'lucide-react';
import type { OperationSurfaceSpec } from './operation-surface-model';
import { ControlVerbs } from '@/components/ui/control-verbs';

const ICONS = { snapshots: Images, security: Shield, autonomous: Smartphone, swarm: Users, platform: Eye } as const;

export function OperationSurfaceCard({ spec }: { spec: OperationSurfaceSpec }) {
  const Icon = ICONS[spec.kind];
  return (
    <article className="border border-sg-neutral-200 dark:border-sg-neutral-800 rounded-lg p-4 min-w-0" data-operation-surface={spec.id}>
      <div className="flex items-start gap-3">
        <Icon className="w-5 h-5 text-sg-red-600 shrink-0" aria-hidden="true" />
        <div className="min-w-0 flex-1">
          <h3 className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">{spec.label}</h3>
          <p className="mt-1 text-sm text-sg-neutral-600 dark:text-sg-neutral-400 break-words">{spec.summary}</p>
          <p className="mt-2 text-xs uppercase tracking-wide text-sg-neutral-500" data-surface-status={spec.status}>
            {spec.status.replace('-', ' ')}
          </p>
          <div className="mt-3">
            <ControlVerbs available={[...spec.controlVerbs]} size="sm" hideLabels />
          </div>
        </div>
      </div>
    </article>
  );
}
