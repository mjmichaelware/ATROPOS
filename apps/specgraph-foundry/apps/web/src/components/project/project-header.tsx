'use client';

import { ChevronRight, ArrowRight } from 'lucide-react';
import { SixAnswersPanel, SixAnswer } from '@/components/ui/six-answers-panel';
import { TrustIndicators, TrustIndicators as TrustIndicatorsType } from '@/components/ui/trust-indicators';
import { ControlVerbs, ControlVerb } from '@/components/ui/control-verbs';
import { StatusBadge } from '@/components/ui/status-badge';
import { CanonicalStatus } from '@/lib/status-system';

interface ProjectHeaderProps {
  projectName: string;
  projectId: string;
  status: CanonicalStatus;
  answers: SixAnswer;
  trustIndicators: TrustIndicatorsType;
  availableActions: ControlVerb[];
  onAction?: (verb: ControlVerb) => void;
  compact?: boolean;
}

export function ProjectHeader({
  projectName,
  projectId,
  status,
  answers,
  trustIndicators,
  availableActions,
  onAction,
  compact = false,
}: ProjectHeaderProps) {
  if (compact) {
    return (
      <div className="flex items-center justify-between p-4 bg-sg-neutral-50 dark:bg-sg-neutral-900 border-b border-sg-neutral-200 dark:border-sg-neutral-800">
        <div>
          <h1 className="font-bold text-sg-neutral-900 dark:text-sg-neutral-50">{projectName}</h1>
          <div className="flex items-center gap-2 text-sm">
            <StatusBadge status={status} size="sm" />
            <span className="text-sg-neutral-600 dark:text-sg-neutral-400">
              {answers.progress?.percent}% complete
            </span>
          </div>
        </div>
        <ControlVerbs available={availableActions} onAction={onAction} size="md" />
      </div>
    );
  }

  return (
    <div className="space-y-6 p-6 bg-gradient-to-br from-sg-neutral-50 to-sg-neutral-100 dark:from-sg-neutral-900 dark:to-sg-neutral-800 border-b border-sg-neutral-200 dark:border-sg-neutral-700">
      {/* Title + Status */}
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold text-sg-neutral-900 dark:text-sg-neutral-50">
            {projectName}
          </h1>
          <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400 mt-1">
            ID: <code className="text-xs">{projectId}</code>
          </p>
        </div>
        <StatusBadge status={status} size="lg" />
      </div>

      {/* Six Continuous Answers */}
      <SixAnswersPanel answers={answers} compact={false} expandable={true} />

      {/* Trust Indicators + Control Verbs */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="space-y-2">
          <h3 className="text-sm font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
            System Health
          </h3>
          <TrustIndicators indicators={trustIndicators} layout="column" />
        </div>

        <div className="space-y-2">
          <h3 className="text-sm font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
            Actions
          </h3>
          <ControlVerbs available={availableActions} onAction={onAction} layout="column" />
        </div>
      </div>
    </div>
  );
}
