/* SPDX-License-Identifier: AGPL-3.0-only */

import { readEngine, type EngineFailure } from '@/lib/engine/client';

/**
 * Reads the activity monitor's single stream.
 *
 * `C3-P19` requires the monitor to show every plan/provider/tool/diff/test/
 * verifier/artifact/deploy state change, and constrains it: "Monitor is
 * presentation of existing evidence; no second event system." So this client
 * has no store, no merge and no local event buffer — it reads what the engine
 * already ordered and hands it on.
 *
 * `everyStageReported` is coverage, deliberately not health. Nothing here maps
 * it to a pass, because a run where all eight stages reported `blocked` has
 * full coverage and failed completely.
 */

export interface ActivityEvent {
  id: string;
  at: string;
  stage: string;
  subject: string;
  /** A completion vocabulary term, never free-form text. */
  outcome: string;
  detail: string;
}

export interface ActivityPayload {
  ok: true;
  stages: string[];
  /** Named so a gap renders as a gap rather than a shorter list. */
  missingStages: string[];
  everyStageReported: boolean;
  events: ActivityEvent[];
}

export type ActivityResult =
  | { ok: true; data: ActivityPayload }
  | ({ ok: false } & Omit<EngineFailure, 'ok'>);

export const activity = {
  read: (): Promise<ActivityResult> => readEngine<ActivityPayload>('/v1/activity'),
};

export interface StageRow {
  stage: string;
  events: ActivityEvent[];
  /** True when this stage has produced nothing yet. */
  missing: boolean;
}

/**
 * Groups the stream by stage without dropping empty stages.
 *
 * A monitor that listed only the stages with events would show a run that never
 * reached `test` as a run with nothing to say about testing. The empty row is
 * the finding.
 */
export function byStage(payload: ActivityPayload): StageRow[] {
  return payload.stages.map((stage) => ({
    stage,
    events: payload.events.filter((event) => event.stage === stage),
    missing: payload.missingStages.includes(stage),
  }));
}

/**
 * Whether the operator should be told something failed.
 *
 * Derived from outcomes, not from coverage. `blocked` is the completion term
 * for work that could not proceed, and one of them anywhere in the stream is
 * enough — the monitor's job is to surface it, not to average it away.
 */
export function hasBlockedStage(payload: ActivityPayload): boolean {
  return payload.events.some((event) => event.outcome === 'blocked');
}

/** Returns a subject-scoped How? explanation from recorded activity only. */
export function pipelineForSubject(payload: ActivityPayload, subject: string): string | undefined {
  const needle = subject.trim().toLowerCase();
  if (!needle) return undefined;
  const events = payload.events.filter((event) => {
    const recorded = event.subject.trim().toLowerCase();
    return recorded === needle || recorded.includes(needle);
  });
  if (events.length === 0) return undefined;
  const stages = [...new Set(events.map((event) => event.stage))];
  const outcomes = [...new Set(events.map((event) => event.outcome))];
  return `Stages: ${stages.join(', ')}. Outcomes: ${outcomes.join(', ')}. ` +
    `Recorded events: ${events.length}.`;
}
