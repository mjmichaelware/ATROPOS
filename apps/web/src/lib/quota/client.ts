/* SPDX-License-Identifier: AGPL-3.0-only */

import { readEngine, type EngineFailure } from '@/lib/engine/client';

/**
 * The quota ledger client (ADD-W-007).
 *
 * Reads the per-provider quota ledger from the bridge.
 */

export interface QuotaProvider {
  readonly id: string;
  readonly state: string;
  readonly costMode: string;
  readonly quotaWeight: number;
  readonly configured: boolean;
  readonly verified: boolean;
  readonly usedRequests: number;
  readonly usedTokens: number;
  readonly lastErrorClass?: string | null;
}

export interface QuotaPayload {
  readonly ok: true;
  readonly count: number;
  readonly healthyCount: number;
  readonly coolingCount: number;
  readonly providers: readonly QuotaProvider[];
}

export type QuotaResult =
  | { ok: true; data: QuotaPayload }
  | ({ ok: false } & Omit<EngineFailure, 'ok'>);

export const quota = {
  read: (): Promise<QuotaResult> => readEngine<QuotaPayload>('/v1/quota'),
};

export function healthyCount(payload: { providers: readonly { state: string }[] }): number {
  return payload.providers.filter(p => p.state === 'ready').length;
}

export function coolingCount(payload: { providers: readonly { state: string }[] }): number {
  return payload.providers.filter(p => p.state === 'cooldown').length;
}

export function sortByQuotaWeight(providers: readonly { quotaWeight: number; id: string }[]): readonly { quotaWeight: number; id: string }[] {
  return [...providers].sort((a, b) => a.quotaWeight - b.quotaWeight || a.id.localeCompare(b.id));
}
