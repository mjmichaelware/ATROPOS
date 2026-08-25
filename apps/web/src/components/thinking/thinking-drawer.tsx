/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import { useEffect, useState } from 'react';
import {
  DEFAULT_THINKING_DEPTH,
  canCollapse,
  nextDepth,
  thinking as thinkingClient,
  type ThinkingDepth,
  type ThinkingPayload,
} from '@/lib/thinking/client';
import { useOptionalWebDisclosure } from '@/lib/contexts/web-disclosure-context';

/**
 * The multi-level thinking drawer.
 *
 * `HOE-C06`: L1 → L2 → L3, collapsed by default, expanded only on an explicit
 * gesture. `HOE-E04`: the depth is this surface's own — it lives in component
 * state and is sent as a request parameter, so nothing here can move the
 * terminal's channel or be moved by it.
 *
 * The Show more control is rendered only when the engine says there is more.
 * That is the same rule the evidence affordance follows and it exists for the
 * same reason: a control that sometimes reveals nothing trains the operator to
 * stop using it, including when it would have shown them something.
 *
 * Nothing is trimmed here. The engine filters and this renders what it sent —
 * a client-side trim would put the depth rule in two places, and the copy in
 * the Web is the one nobody would check.
 */
export function ThinkingDrawer({ nodeId }: { nodeId: string }) {
  // ADD-W-004: the drawer opens at this browser's chosen level when the web
  // disclosure channel is mounted; its own local default otherwise.
  const webChannel = useOptionalWebDisclosure();
  const [depth, setDepth] = useState<ThinkingDepth>(
    (webChannel?.level as ThinkingDepth | undefined) ?? DEFAULT_THINKING_DEPTH,
  );
  const [payload, setPayload] = useState<ThinkingPayload | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (!open) return undefined;
    let cancelled = false;
    void (async () => {
      const result = await thinkingClient.read(nodeId, depth);
      if (cancelled) return;
      if (result.ok) {
        setPayload(result.data);
        setFailure(null);
      } else {
        setFailure(`${result.detail} ${result.remedy}`);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [open, depth, nodeId]);

  if (!open) {
    return (
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="text-sm font-medium text-sg-neutral-700 underline underline-offset-4 dark:text-sg-neutral-300"
      >
        Thinking
      </button>
    );
  }

  if (failure) {
    return (
      <div role="status" className="rounded-lg border border-sg-amber-300 p-3 text-sm dark:border-sg-amber-900">
        {/* Unknown, not absent. The node may well have reasoned about this. */}
        <p className="font-medium text-sg-neutral-900 dark:text-sg-neutral-50">
          Reasoning unavailable — the engine did not answer
        </p>
        <p className="text-sg-neutral-700 dark:text-sg-neutral-300">{failure}</p>
      </div>
    );
  }

  if (!payload) {
    return <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">Reading reasoning…</p>;
  }

  if (payload.present === false) {
    return (
      <div role="status" className="rounded-lg border border-sg-neutral-200 p-3 text-sm dark:border-sg-neutral-800">
        <p className="text-sg-neutral-700 dark:text-sg-neutral-300">{payload.detail}</p>
        <p className="text-sg-neutral-600 dark:text-sg-neutral-400">{payload.remedy}</p>
      </div>
    );
  }

  const deeper = nextDepth(payload);

  return (
    <section
      aria-label={`Reasoning for ${payload.nodeId}`}
      className="space-y-3 rounded-lg border border-sg-neutral-200 p-3 dark:border-sg-neutral-800"
    >
      <div className="flex items-baseline justify-between gap-3">
        <p className="text-xs uppercase tracking-wide text-sg-neutral-600 dark:text-sg-neutral-400">
          {payload.depthLabel}
        </p>
        <button
          type="button"
          onClick={() => setOpen(false)}
          className="text-xs text-sg-neutral-500 underline underline-offset-4"
        >
          Hide
        </button>
      </div>

      <ol className="space-y-1">
        {payload.lines.map((line) => (
          <li key={line.id} className="text-sm text-sg-neutral-800 dark:text-sg-neutral-200">
            {line.text}
          </li>
        ))}
      </ol>

      <div className="flex flex-wrap items-center gap-3">
        {/* Rendered only when the engine says expanding would reveal something. */}
        {deeper !== null && (
          <button
            type="button"
            onClick={() => setDepth(deeper)}
            className="text-sm font-medium underline underline-offset-4"
          >
            Show more
          </button>
        )}
        {canCollapse(payload) && (
          <button
            type="button"
            onClick={() => setDepth(DEFAULT_THINKING_DEPTH)}
            className="text-sm text-sg-neutral-600 underline underline-offset-4 dark:text-sg-neutral-400"
          >
            Back to outline
          </button>
        )}
        {deeper === null && (
          <p className="text-xs text-sg-neutral-500">
            This is everything the node recorded.
          </p>
        )}
      </div>
    </section>
  );
}
