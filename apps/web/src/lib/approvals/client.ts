/* SPDX-License-Identifier: AGPL-3.0-only */

import { readEngine, writeEngine, WEB_ACTOR, type EngineFailure } from '@/lib/engine/client';
import { isApprovalCard, type ApprovalCard } from '@atropos/web-contracts';

export type { ApprovalCard } from '@atropos/web-contracts';

/**
 * The bridge approvals surface (F-WEB-008).
 *
 * Reads `GET /v1/approvals` and validates every row against the shared
 * contract before it can become a card; decides through
 * `POST /v1/approvals/decide` with this surface's actor id so the engine —
 * not the browser — records who decided and through which surface.
 *
 * This is a reader over the engine's durable pending set, deliberately
 * separate from the SSE stream in StreamingApprovalCards: the stream shows
 * moments ("an approval was raised"), this shows state ("these are still
 * waiting"). Neither is derived from the other, because a dropped SSE frame
 * must never make a pending approval look resolved.
 */

export type ApprovalsResult =
  | { ok: true; data: ApprovalCard[] }
  | ({ ok: false } & Omit<EngineFailure, 'ok'>);

export async function readPendingApprovals(): Promise<ApprovalsResult> {
  const result = await readEngine<{ ok: true; pending: unknown[] }>(
    '/v1/approvals',
  );
  if (!result.ok) return result;

  // One malformed row is dropped and counted rather than poisoning the list;
  // a card that fails its own contract cannot be rendered as a decision the
  // operator is asked to make.
  const cards: ApprovalCard[] = [];
  let dropped = 0;
  for (const row of result.data.pending ?? []) {
    if (isApprovalCard(row)) cards.push(row);
    else dropped += 1;
  }
  return { ok: true, data: cards };
}

export interface DecideResult {
  ok: boolean;
  id: string;
  approved: boolean;
}

export type DecideOutcome =
  | { ok: true; data: DecideResult }
  | ({ ok: false } & Omit<EngineFailure, 'ok'>);

/**
 * Records a human decision. The engine attributes it to BRIDGE with
 * [WEB_ACTOR] as decider; an unattributed decision is refused server-side and
 * this client does not pretend otherwise by supplying a fallback identity.
 */
export function decideApproval(id: string, approved: boolean): Promise<DecideOutcome> {
  return writeEngine<DecideResult>('/v1/approvals/decide', {
    id,
    approved,
    decidedBy: WEB_ACTOR,
  });
}

/** Pending first, then oldest request — what needs a human, soonest-asked. */
export function sortForDecision(cards: readonly ApprovalCard[]): ApprovalCard[] {
  return [...cards]
    .filter((card) => card.pending)
    .sort((a, b) => a.requestedAt.localeCompare(b.requestedAt));
}
