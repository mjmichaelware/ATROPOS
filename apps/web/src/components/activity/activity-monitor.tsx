/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import { useEffect, useState } from 'react';
import { subscribeActivity, type ActivityEvent as EventActivityEvent } from '@/lib/events/client';

/**
 * The activity monitor.
 *
 * `C3-P19`: every plan/provider/tool/diff/test/verifier/artifact/deploy state
 * change, in one place. The stage list is rendered whole — a stage that has
 * reported nothing shows as "no events yet" rather than disappearing, because a
 * pipeline that never reached the verifier and a pipeline whose verifier passed
 * look identical in a filtered list.
 *
 * Full coverage is never rendered as success. The summary line says how many
 * stages reported and, separately, whether anything is blocked.
 *
 * Now derives from /v1/events/stream (queue_state_changed, approval_raised,
 * turn_appended, mcp_judged, computer_use) instead of requiring /v1/activity/stream.
 */
export function ActivityMonitor() {
  const [payload, setPayload] = useState<ActivityPayload | null>(null);
  const [failure, setFailure] = useState<{ detail: string; remedy: string } | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        // Use the SSE stream to build up the activity payload incrementally
        for await (const events of subscribeActivity()) {
          if (cancelled) return;
          setPayload((prev) => mergeEvents(prev, events));
          setLoading(false);
        }
      } catch (error) {
        if (!cancelled) {
          setFailure({
            detail: 'Event stream unavailable',
            remedy: 'The engine did not answer at /v1/events/stream. Check engine status.',
          });
          setLoading(false);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  if (loading) {
    return <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">Reading activity…</p>;
  }

  if (failure || !payload) {
    return (
      <div
        role="status"
        className="rounded-lg border border-sg-amber-300 bg-sg-amber-50 p-4 dark:border-sg-amber-900 dark:bg-sg-amber-900/20"
      >
        <p className="font-semibold text-sg-amber-900 dark:text-sg-amber-100">
          Activity unknown — the engine did not answer
        </p>
        <p className="text-sm text-sg-amber-800 dark:text-sg-amber-200">
          {failure?.detail} {failure?.remedy}
        </p>
      </div>
    );
  }

  const rows = byStage(payload);
  const reported = payload.stages.length - payload.missingStages.length;
  const blocked = hasBlockedStage(payload);

  return (
    <div className="space-y-6">
      <p className="text-sm text-sg-neutral-700 dark:text-sg-neutral-300">
        {/* Two independent facts, stated separately on purpose. */}
        {reported} of {payload.stages.length} stages have reported.{' '}
        {blocked ? 'At least one stage is blocked.' : 'Nothing is blocked.'}
      </p>

      <ol className="space-y-3">
        {rows.map((row) => (
          <li
            key={row.stage}
            className="rounded-lg border border-sg-neutral-200 p-3 dark:border-sg-neutral-800"
          >
            <p className="font-mono text-xs uppercase tracking-wide text-sg-neutral-600 dark:text-sg-neutral-400">
              {row.stage}
            </p>
            {row.missing ? (
              <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">
                No events yet — this stage has not reported.
              </p>
            ) : (
              <ul className="mt-1 space-y-1">
                {row.events.map((event) => (
                  <li key={event.id} className="text-sm text-sg-neutral-800 dark:text-sg-neutral-200">
                    <span className="font-mono">{event.subject}</span>{' '}
                    <span
                      className={
                        event.outcome === 'blocked'
                          ? 'font-medium text-sg-red-700 dark:text-sg-red-300'
                          : 'text-sg-neutral-600 dark:text-sg-neutral-400'
                      }
                    >
                      {/* Text carries the state as well as colour — Source Doc 3 §E. */}
                      [{event.outcome}]
                    </span>{' '}
                    {event.detail}
                    <time className="ml-2 text-xs text-sg-neutral-500" dateTime={event.at}>
                      {event.at}
                    </time>
                  </li>
                ))}
              </ul>
            )}
          </li>
        ))}
      </ol>
    </div>
  );
}

interface ActivityPayload {
  stages: string[];
  missingStages: string[];
  events: ActivityEvent[];
}

type ActivityEvent = EventActivityEvent;

const MONITOR_STAGES = [
  'plan',
  'provider',
  'tool',
  'diff',
  'test',
  'verifier',
  'artifact',
  'deploy',
  'queue',
  'approval',
  'conversation',
] as const;

function mergeEvents(
  prev: ActivityPayload | null,
  events: EventActivityEvent[]
): ActivityPayload {
  const existing = prev ?? { stages: [], missingStages: [], events: [] };
  const seenStages = new Set(existing.stages);
  const newEvents = events.filter((e) => {
    if (!seenStages.has(e.stage)) {
      seenStages.add(e.stage);
      return true;
    }
    return true;
  });

  return {
    stages: [...existing.stages, ...Array.from(seenStages).filter((s) => !existing.stages.includes(s))],
    missingStages: MONITOR_STAGES.filter((s) => !seenStages.has(s)),
    events: [...existing.events, ...newEvents].slice(-500),
  };
}

function byStage(payload: ActivityPayload): StageRow[] {
  return MONITOR_STAGES.map((stage) => ({
    stage,
    events: payload.events.filter((event) => event.stage === stage),
    missing: payload.missingStages.includes(stage),
  }));
}

function hasBlockedStage(payload: ActivityPayload): boolean {
  return payload.events.some((event) => event.outcome === 'blocked');
}

interface StageRow {
  stage: string;
  events: EventActivityEvent[];
  missing: boolean;
}
