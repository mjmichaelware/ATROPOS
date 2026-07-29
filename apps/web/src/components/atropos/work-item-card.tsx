'use client';

import { AlertTriangle } from 'lucide-react';
import type { WorkItem } from '@/lib/api-atropos/types';
import { StatusBadge } from '@/components/ui/status-badge';
import { WhyHowEvidence } from '@/components/ui/why-how-evidence';
import { ProgressiveDisclosure } from '@/components/ui/progressive-disclosure';
import { useOptionalSessionState } from '@/lib/contexts/session-state-context';

/**
 * One work item, with its explainability controls attached.
 *
 * §5.3: "Nothing may claim completion without evidence." The Done column
 * previously rendered a struck-through title and a completed badge with no
 * affordance at all, so a completion claim was final and uninspectable. Here a
 * completed item either links its evidence or is visibly marked as an
 * unsupported claim — the claim is never quietly accepted.
 *
 * §5.0: the operator's information level only ever *adds* rows. Level 1 is the
 * title, status and next action; higher levels reveal more of the same record.
 * Nothing shown at a lower level is removed at a higher one.
 */
export function WorkItemCard({ item }: { item: WorkItem }) {
  const level = useOptionalSessionState()?.session.informationLevel ?? 2;

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
      {/* Level 1 — Simple: what it is and where it stands. */}
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

      {unsupportedCompletion && (
        // Not an error: the work may genuinely be done. It is a statement that
        // the claim cannot currently be checked, which §5.3 requires the
        // operator to be able to see. It shows at every level because a
        // completion nobody can verify is never a detail.
        <p
          className="flex items-start gap-1.5 text-xs text-sg-amber-700 dark:text-sg-amber-400"
          role="note"
        >
          <AlertTriangle className="mt-0.5 h-3 w-3 flex-shrink-0" aria-hidden="true" />
          Marked complete with no linked evidence — this claim cannot be verified here.
        </p>
      )}

      {/* Level 2 — Professional: description and progress. */}
      {level >= 2 && item.description && (
        <p className="text-xs text-sg-neutral-600 dark:text-sg-neutral-400">
          {item.description}
        </p>
      )}

      {level >= 2 && typeof item.progress === 'number' && (
        <p className="text-xs text-sg-neutral-500">{item.progress}% complete</p>
      )}

      {/* Level 3 — Engineering: identity and priority, for correlating with
          the engine's own records. */}
      {level >= 3 && (
        <dl className="grid grid-cols-2 gap-x-3 gap-y-1 text-xs text-sg-neutral-500">
          <dt>Task ID</dt>
          <dd className="font-mono">{item.id}</dd>
          <dt>Priority</dt>
          <dd>{item.priority}</dd>
          <dt>Updated</dt>
          <dd>{new Date(item.updated_at).toLocaleString()}</dd>
        </dl>
      )}

      <WhyHowEvidence
        answers={item.six_answers}
        evidence={item.evidence}
        subject={`"${item.title}"`}
      />

      {/* Level 4 — Internal: the record as the engine returned it. Collapsed,
          so it never competes with everyday work (§0.6). */}
      {level >= 4 && (
        <ProgressiveDisclosure title="Raw record" level="expert">
          <pre className="overflow-x-auto rounded bg-sg-neutral-100 p-2 text-[10px] leading-relaxed dark:bg-sg-neutral-900">
            {JSON.stringify(item, null, 2)}
          </pre>
        </ProgressiveDisclosure>
      )}
    </div>
  );
}
