/* SPDX-License-Identifier: AGPL-3.0-only */

import { readEngine, type EngineFailure } from '@/lib/engine/client';

/**
 * The bridge conversation list (F-WEB-002).
 *
 * Mirrors `BridgeSessionHandler.sessionJson` exactly: id, title, turnCount,
 * createdAt, updatedAt. A session-first home needs a session list that is the
 * engine's own — a locally invented list would show conversations the engine
 * does not know it had, and hide ones it did.
 */

export interface BridgeSession {
  readonly id: string;
  readonly title: string;
  readonly turnCount: number;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export type SessionsResult =
  | { ok: true; data: BridgeSession[] }
  | ({ ok: false } & Omit<EngineFailure, 'ok'>);

export async function readSessions(): Promise<SessionsResult> {
  const result = await readEngine<{ count: number; sessions: BridgeSession[] }>('/v1/sessions');
  if (!result.ok) return result;
  // The wire carries a count beside the list; the surface consumes the list.
  return { ok: true, data: result.data.sessions ?? [] };
}

export const sessions = { read: readSessions };

/** Most recently updated first — what an operator returns to. */
export function mostRecentFirst(list: readonly BridgeSession[]): BridgeSession[] {
  return [...list].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
}
