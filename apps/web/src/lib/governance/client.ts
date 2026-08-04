/* SPDX-License-Identifier: AGPL-3.0-only */

import { engineBaseUrl, type EngineFailure } from '@/lib/engine/client';

/**
 * Reads the Phase 20 governance surfaces.
 *
 * Every numeric metric is `number | null` rather than `number`, and that is the
 * whole contract. `P20-S04`'s rates are undefined when their denominator is
 * zero, and a client type that promised `number` would force a default at the
 * boundary — turning "never measured" into "measured zero", which for a
 * false-VERIFIED rate is the most flattering possible lie.
 */

export interface ProposalMetric {
  name: string;
  baseline: number;
  target: number;
  lowerIsBetter: boolean;
  /** False when no metric was declared before the change. */
  declared: boolean;
}

export interface Proposal {
  id: string;
  proposedBy: string;
  summary: string;
  state: string;
  metric: ProposalMetric;
  baseline: string;
  target: string;
  guardrails: string[];
  territory: string[];
  risk: string;
  rollback: string;
  necessityHashes: string[];
  complete: boolean;
  /** Named, not counted — an operator fixing a proposal needs to know which. */
  missing: string[];
  failureCount: number;
}

export interface Cooldown {
  subsystem: string;
  remainingSeconds: number;
}

export interface Amendment {
  id: string;
  proposalId: string;
  sha256: string;
  /** The authority hash this amends, left intact. */
  supersedes: string;
  acceptedBy: string;
  acceptedAt: string;
  evidenceHashes: string[];
}

export interface GovernanceMetrics {
  healthy: boolean;
  falseVerifiedRate: number | null;
  territoryViolationRate: number | null;
  recoveryCompleteness: number | null;
  observationSuccess: number | null;
  tokensPerVerifiedChange: number | null;
  unmeasured: string[];
}

export interface StorageClass {
  id: string;
  tier: string;
  bytes: number;
  reclaimable: boolean;
}

export interface StorageReport {
  usedBytes: number;
  ceilingBytes: number;
  remainingBytes: number;
  fractionUsed: number;
  reclaimableBytes: number;
  classes: StorageClass[];
}

export type Result<T> = { ok: true; data: T } | ({ ok: false } & Omit<EngineFailure, 'ok'>);

async function read<T>(path: string): Promise<Result<T>> {
  let response: Response;
  try {
    response = await fetch(`${engineBaseUrl()}${path}`, {
      cache: 'no-store',
      headers: { accept: 'application/json' },
    });
  } catch {
    return {
      ok: false,
      reason: 'bridge-unreachable',
      detail: 'The ATROPOS engine bridge did not answer.',
      remedy: 'Start the engine with ATROPOS_BRIDGE_PORT set (for example 4317).',
    };
  }
  const body = await response.json().catch(() => null);
  if (!response.ok) {
    const refusal = body as { detail?: string; remedy?: string } | null;
    return {
      ok: false,
      reason: 'bridge-refused',
      detail: refusal?.detail ?? `The bridge refused with status ${response.status}.`,
      remedy: refusal?.remedy ?? 'Call GET /v1/routes for the routes this build exposes.',
    };
  }
  return { ok: true, data: body as T };
}

export const governance = {
  proposals: () => read<{ proposals: Proposal[]; cooldowns: Cooldown[] }>('/v1/proposals'),
  amendments: () => read<{ amendments: Amendment[] }>('/v1/amendments'),
  metrics: () => read<GovernanceMetrics>('/v1/metrics'),
  storage: () => read<StorageReport>('/v1/storage'),
};

/**
 * Formats a rate for display, or says it was never measured.
 *
 * Returns the word rather than a dash so the distinction survives being read
 * aloud by a screen reader — "—" and "0%" are equally silent, and only one of
 * them is honest here.
 */
export function formatRate(value: number | null): string {
  if (value === null) return 'not measured';
  return `${(value * 100).toFixed(1)}%`;
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const units = ['KB', 'MB', 'GB'];
  let value = bytes / 1024;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${value.toFixed(1)} ${units[unit]}`;
}
