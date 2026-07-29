'use client';

import { AlertTriangle } from 'lucide-react';
import type { WorkItem } from '@/lib/api-atropos/types';
import { StatusBadge } from '@/components/ui/status-badge';
import { WhyHowEvidence } from '@/components/ui/why-how-evidence';

/**
 * One work item, with its explainability controls attached.
 *
 * §5.3: "Nothing may claim completion without evidence." The Done column
 * previously rendered a struck-through title and a completed badge with no
 * affordance at all, so a completion claim was final and uninspectable. Here a
 * completed item either links its evidence or is visibly marked as an
 * unsupported claim — the claim is never quietly accepted.
 */
export function WorkItemCard({ item }: { item: WorkItem }) {
  const isComplete = item.status === 'completed';
  const hasEvidence = Boolean(item.evidence && item.evidence.length > 0);
  const unsupportedCompletion = isComplete && !hasEvidence;

  return (
    <div
      className={`space-y-2 rounded border p-3 transition-colors ${
        isComplete
          ? 'border-sg-green-200 bg-white hover:border-sg-green-400 dark:border-sg-green-800 dark:bg-sg-neutral-800 dark:hover:border-sg-green-600'
          : 'border-sg-neutral-200 bg-white hover:border-sg-red-400 dark:border-sg-neutral-700 dark:bg-sg-neutral-800 dark:hover:border-sg-red-600'
      }`}
    >
      <div className="flex items-start justify-between gap-2">
        <h5
          className={`text-sm font-medium text-sg-neutral-900 dark:text-sg-neutral-50 ${
            isComplete ? 'line-through' : ''
          }`}
        >
          {item.title}
        </h5>
        <StatusBadge status={item.status} size="sm" />
      </div>

      {item.description && (
        <p className="text-xs text-sg-neutral-600 dark:text-sg-neutral-400">
          {item.description}
        </p>
      )}

      {unsupportedCompletion && (
        // Not an error: the work may genuinely be done. It is a statement that
        // the claim cannot currently be checked, which §5.3 requires the
        // operator to be able to see.
        <p
          className="flex items-start gap-1.5 text-xs text-sg-amber-700 dark:text-sg-amber-400"
          role="note"
        >
          <AlertTriangle className="mt-0.5 h-3 w-3 flex-shrink-0" aria-hidden="true" />
          Marked complete with no linked evidence — this claim cannot be verified here.
        </p>
      )}

      <WhyHowEvidence
        answers={item.six_answers}
        evidence={item.evidence}
        subject={`"${item.title}"`}
      />
    </div>
  );
}
