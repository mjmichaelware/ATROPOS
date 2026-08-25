/* SPDX-License-Identifier: AGPL-3.0-only */

import { readEngine, writeEngine, WEB_ACTOR, type EngineFailure } from '@/lib/engine/client';

/**
 * The queue-interrupt surface (ADD-W-003).
 *
 * The bridge serves interrupt verbs:
 * - `POST /v1/queue/cancel` (soft interrupt)
 * - `POST /v1/queue/hard-interrupt` (hard interrupt - terminal cancel)
 * - `POST /v1/queue/freeze` (freeze queue)
 * - `POST /v1/queue/resume` (resume from freeze)
 * - `GET /v1/queue/freeze` (check freeze status)
 *
 * This client refuses to pretend otherwise — [INTERRUPT_GAPS] is rendered
 * by the control so the operator sees the boundary instead of buttons that
 * would silently no-op.
 */

export interface RunningWork {
  readonly id: string;
  readonly title: string;
}

export interface FreezeStatus {
  readonly ok: true;
  readonly frozen: boolean;
  readonly changed?: boolean;
}

export interface InterruptResult {
  readonly ok: true;
  readonly id: string;
  readonly state?: string;
  readonly terminal?: boolean;
  readonly changed?: boolean;
}

export type QueueResult<T> =
  | { ok: true; data: T }
  | ({ ok: false } & Omit<EngineFailure, 'ok'>);

/** What the six-answers payload already carries about live work. */
export async function runningWork(): Promise<QueueResult<RunningWork[]>> {
  const result = await readEngine<{
    answers: unknown;
    queue: { readable: boolean; running: Array<{ id: string; title: string }> };
  }>('/v1/answers');
  if (!result.ok) return result;
  return { ok: true, data: result.data.queue.running ?? [] };
}

/**
 * Soft interrupt: ask the engine to cancel one entry. Attributed like every
 * other write from this surface.
 */
export async function cancelWork(id: string): Promise<QueueResult<{ ok: boolean }>> {
  return writeEngine<{ ok: boolean }>('/v1/queue/cancel', { id, decidedBy: WEB_ACTOR });
}

/**
 * Hard interrupt: terminal cancel that prevents re-run.
 */
export async function hardInterrupt(id: string): Promise<QueueResult<InterruptResult>> {
  return writeEngine<InterruptResult>('/v1/queue/hard-interrupt', { id, decidedBy: WEB_ACTOR });
}

/**
 * Freeze the queue: take no new work; durable across restart.
 */
export async function freezeQueue(): Promise<QueueResult<FreezeStatus>> {
  return writeEngine<FreezeStatus>('/v1/queue/freeze', { decidedBy: WEB_ACTOR });
}

/**
 * Resume the queue from freeze.
 */
export async function resumeQueue(): Promise<QueueResult<FreezeStatus>> {
  return writeEngine<FreezeStatus>('/v1/queue/resume', { decidedBy: WEB_ACTOR });
}

/**
 * Get the current freeze status.
 */
export async function getFreezeStatus(): Promise<QueueResult<FreezeStatus>> {
  return readEngine<FreezeStatus>('/v1/queue/freeze');
}

/** The verbs this bridge build does not serve, named for display. */
export const INTERRUPT_GAPS: readonly string[] = Object.freeze([]);