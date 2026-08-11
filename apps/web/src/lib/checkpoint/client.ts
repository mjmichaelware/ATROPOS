/* SPDX-License-Identifier: AGPL-3.0-only */

import { readEngine, type EngineFailure } from '@/lib/engine/client';

/**
 * Reads the resume checkpoint.
 *
 * `HOE-C04`/`HOE-B04` make Resume the checkpoint's primary action and rule out
 * "new chat" as its peer. The rule is enforced in the engine, and this client's
 * job is to not undo it: [CheckpointAction] carries `primary` as data, so the
 * surface renders the engine's decision rather than making its own.
 *
 * `present: false` is a first-class state. A workspace with no checkpoint is
 * not a checkpoint at age zero, and collapsing the two would show a Resume
 * button that resumes nothing.
 */

export interface CheckpointAction {
  id: string;
  label: string;
  primary: boolean;
}

export interface CheckpointAbsent {
  ok: true;
  present: false;
  detail: string;
  remedy: string;
}

export interface CheckpointPresent {
  ok: true;
  present: true;
  goalId: string;
  nodeId: string | null;
  phase: string | null;
  recordedAt: string;
  ageMinutes: number;
  resumable: boolean;
  evidenceCount: number;
  nextAction: string | null;
  primaryAction: { id: string; label: string };
  actions: CheckpointAction[];
}

export type CheckpointPayload = CheckpointAbsent | CheckpointPresent;

export type CheckpointResult =
  | { ok: true; data: CheckpointPayload }
  | ({ ok: false } & Omit<EngineFailure, 'ok'>);

export const checkpoint = {
  read: (): Promise<CheckpointResult> => readEngine<CheckpointPayload>('/v1/checkpoint'),
};

/**
 * How old the checkpoint is, in words.
 *
 * Age is shown because a resumable checkpoint from three weeks ago and one from
 * three minutes ago warrant different confidence, and a bare timestamp makes
 * the operator do that arithmetic while deciding whether to resume.
 */
export function formatAge(minutes: number): string {
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  return `${Math.floor(hours / 24)}d ago`;
}

/**
 * The action a surface may render most prominently.
 *
 * Reads the engine's `primary` flag rather than searching for `resume` by name:
 * hard-coding the id here would reimplement the policy that decides it, which
 * is exactly what `HOE-C05` forbids.
 */
export function primaryOf(actions: readonly CheckpointAction[]): CheckpointAction | null {
  return actions.find((action) => action.primary) ?? null;
}

export function secondaryOf(actions: readonly CheckpointAction[]): readonly CheckpointAction[] {
  return actions.filter((action) => !action.primary);
}
