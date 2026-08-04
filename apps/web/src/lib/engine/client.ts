/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * The Web surface's client for the ATROPOS engine bridge.
 *
 * HOE-C02 requires the Web surface to be "thin presentation over existing
 * engine status endpoints; no business logic in Web", and HOE-C05 adds "never
 * reimplement policy". This module is the seam that makes those enforceable:
 * every value the surface renders arrives from the engine, already computed and
 * already redacted, so there is no place for a second implementation to grow.
 *
 * It deliberately does not throw. A cockpit whose data layer throws renders an
 * error boundary, and an error boundary cannot distinguish "the engine is not
 * running" from "the queue is unreadable" — a collapse §4.1 forbids. Every call
 * returns a discriminated result carrying a reason and a remedy instead.
 */

export type EngineFailureReason =
  | 'bridge-unreachable'
  | 'bridge-refused'
  | 'malformed-response';

export interface EngineFailure {
  ok: false;
  reason: EngineFailureReason;
  /** §4.1: a failure states why. */
  detail: string;
  /** §4.1: and what to do about it. */
  remedy: string;
}

export interface EngineSuccess<T> {
  ok: true;
  data: T;
}

export type EngineResult<T> = EngineSuccess<T> | EngineFailure;

export interface EngineAnswer {
  value: string;
  health: 'verified' | 'pending' | 'error' | 'unknown';
  /** The non-colour channel Source Doc 3 §E requires alongside colour. */
  signal: string;
}

export interface EngineSixAnswers {
  objective: EngineAnswer;
  doing: EngineAnswer;
  why: EngineAnswer;
  progress: EngineAnswer;
  next: EngineAnswer;
  evidence: EngineAnswer;
}

export interface EngineWorkItem {
  id: string;
  title: string;
  state: string;
  detail: string;
  attempt: number | null;
  maxAttempts: number | null;
}

export interface EngineAnswersPayload {
  ok: true;
  answers: EngineSixAnswers;
  queue: {
    /** False means the queue could not be read — never the same as empty. */
    readable: boolean;
    queued: number;
    failed: number;
    running: EngineWorkItem[];
  };
  projectsReadable: boolean;
  provider: string;
  heap: { usedMb: number; maxMb: number };
}

export interface EngineProject {
  id: string;
  name: string;
  status: string;
  statusLabel: string;
  signal: string;
  objective: string;
  completionIsVerifiable: boolean;
}

export interface EngineProjectsPayload {
  ok: true;
  readable: boolean;
  projects: EngineProject[];
}

/**
 * Where the bridge listens.
 *
 * The engine only opens this port when the operator sets `ATROPOS_BRIDGE_PORT`,
 * so an unset base URL is a legitimate state rather than a misconfiguration —
 * the surface reports "bridge not running" and tells them how to start it.
 */
export function engineBaseUrl(): string {
  return process.env.NEXT_PUBLIC_ATROPOS_BRIDGE_URL ?? 'http://127.0.0.1:4317';
}

const UNREACHABLE_REMEDY =
  'Start the engine with ATROPOS_BRIDGE_PORT set (for example ATROPOS_BRIDGE_PORT=4317).';

/**
 * The one place this surface talks to the bridge.
 *
 * Exported so every feature client (governance, checkpoint, activity, exports)
 * reads through it. A second copy of this function would be a second answer to
 * "is the engine running?", and the two would diverge on the first day one of
 * them learned about a new refusal shape.
 */
export async function readEngine<T>(path: string): Promise<EngineResult<T>> {
  let response: Response;
  try {
    response = await fetch(`${engineBaseUrl()}${path}`, {
      // The cockpit must never show a cached answer as current.
      cache: 'no-store',
      headers: { accept: 'application/json' },
    });
  } catch {
    return {
      ok: false,
      reason: 'bridge-unreachable',
      detail: 'The ATROPOS engine bridge did not answer.',
      remedy: UNREACHABLE_REMEDY,
    };
  }

  let body: unknown;
  try {
    body = await response.json();
  } catch {
    return {
      ok: false,
      reason: 'malformed-response',
      detail: `The bridge answered ${response.status} with a body this surface could not parse.`,
      remedy: 'Check the engine log, then retry.',
    };
  }

  if (!response.ok) {
    const refusal = body as { detail?: string; remedy?: string };
    return {
      ok: false,
      reason: 'bridge-refused',
      detail: refusal?.detail ?? `The bridge refused with status ${response.status}.`,
      remedy: refusal?.remedy ?? 'Call GET /v1/routes for the routes this build exposes.',
    };
  }

  return { ok: true, data: body as T };
}

export const engine = {
  answers: () => readEngine<EngineAnswersPayload>('/v1/answers'),
  projects: () => readEngine<EngineProjectsPayload>('/v1/projects'),
  commands: () => readEngine<unknown>('/v1/commands'),
  vocabulary: () => readEngine<unknown>('/v1/vocabulary'),
  health: () => readEngine<{ ok: true; engine: string }>('/v1/health'),
};
