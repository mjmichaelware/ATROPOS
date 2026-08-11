/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import { useEffect, useState } from 'react';
import {
  checkpoint as checkpointClient,
  formatAge,
  primaryOf,
  secondaryOf,
  type CheckpointPayload,
} from '@/lib/checkpoint/client';
import { ThinkingDrawer } from '@/components/thinking/thinking-drawer';

/**
 * The resume rail.
 *
 * `HOE-C04` calls the checkpoint a product object and `HOE-B04` fixes its
 * primary action as Resume — "not new chat". This renders the engine's own
 * `primary` flag, so the rule lives in one place and the surface cannot quietly
 * promote something else by giving it a bigger button.
 *
 * Three states, kept apart on purpose:
 *  - no checkpoint: said in words, with what would produce one;
 *  - a checkpoint that cannot resume: Inspect, with the reason visible;
 *  - a resumable checkpoint: Resume, with its age and evidence count.
 *
 * The unresumable case never offers to start over. Discarding state the
 * operator has not been shown is the failure this atom removes.
 */
export function CheckpointRail({
  onAction,
}: {
  /** Receives the engine's action id — the surface never invents one. */
  onAction?: (actionId: string, goalId: string) => void;
}) {
  const [payload, setPayload] = useState<CheckpointPayload | null>(null);
  const [failure, setFailure] = useState<{ detail: string; remedy: string } | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const result = await checkpointClient.read();
      if (cancelled) return;
      if (result.ok) setPayload(result.data);
      else setFailure({ detail: result.detail, remedy: result.remedy });
      setLoading(false);
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  if (loading) {
    return (
      <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">Reading checkpoint…</p>
    );
  }

  if (failure) {
    return (
      <div
        role="status"
        className="rounded-lg border border-sg-amber-300 bg-sg-amber-50 p-4 dark:border-sg-amber-900 dark:bg-sg-amber-900/20"
      >
        <p className="font-semibold text-sg-amber-900 dark:text-sg-amber-100">
          Checkpoint unknown — the engine did not answer
        </p>
        {/* Unknown, not absent: the operator may well have resumable work. */}
        <p className="text-sm text-sg-amber-800 dark:text-sg-amber-200">
          {failure.detail} {failure.remedy}
        </p>
      </div>
    );
  }

  if (!payload || payload.present === false) {
    return (
      <div
        role="status"
        className="space-y-1 rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800"
      >
        <p className="font-medium text-sg-neutral-900 dark:text-sg-neutral-50">No checkpoint</p>
        <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">
          {payload?.detail ?? 'Nothing has been checkpointed yet.'}{' '}
          {payload?.remedy ?? ''}
        </p>
      </div>
    );
  }

  const primary = primaryOf(payload.actions);
  const secondary = secondaryOf(payload.actions);

  return (
    <section
      aria-label="Resume checkpoint"
      className="space-y-3 rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800"
    >
      <div className="flex items-baseline justify-between gap-3">
        <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
          {payload.phase ?? 'In progress'} · <span className="font-mono">{payload.goalId}</span>
        </p>
        <span className="text-xs text-sg-neutral-600 dark:text-sg-neutral-400">
          {formatAge(payload.ageMinutes)}
        </span>
      </div>

      <p className="text-sm text-sg-neutral-700 dark:text-sg-neutral-300">
        {payload.nextAction ?? 'No next action recorded.'}
      </p>
      <p className="text-xs text-sg-neutral-600 dark:text-sg-neutral-400">
        Node <span className="font-mono">{payload.nodeId ?? 'none'}</span> ·{' '}
        {payload.evidenceCount} piece(s) of evidence
      </p>

      {!payload.resumable && (
        <p className="text-sm font-medium text-sg-amber-800 dark:text-sg-amber-200">
          This checkpoint cannot resume from durable state. Nothing here will start it over —
          inspect it to see what is missing.
        </p>
      )}

      {/* HOE-C06: the reasoning behind the node this checkpoint sits on,
          collapsed by default and expanded on this surface's own channel. */}
      {payload.nodeId && <ThinkingDrawer nodeId={payload.nodeId} />}

      <div className="flex flex-wrap items-center gap-2">
        {/* Every action is live. The engine has already decided which one is
            appropriate for this checkpoint's state, so a disabled control here
            would only re-decide it — wrongly, since when the checkpoint cannot
            resume the primary IS the inspect action. */}
        {primary && (
          <button
            type="button"
            onClick={() => onAction?.(primary.id, payload.goalId)}
            className="rounded-lg bg-sg-neutral-900 px-4 py-2 font-medium text-white dark:bg-sg-neutral-50 dark:text-sg-neutral-900"
          >
            {primary.label}
          </button>
        )}
        {secondary.map((action) => (
          <button
            key={action.id}
            type="button"
            onClick={() => onAction?.(action.id, payload.goalId)}
            className="rounded-lg border border-sg-neutral-300 px-3 py-2 text-sm text-sg-neutral-700 dark:border-sg-neutral-700 dark:text-sg-neutral-300"
          >
            {action.label}
          </button>
        ))}
      </div>
    </section>
  );
}
