/* SPDX-License-Identifier: AGPL-3.0-only */

import { readEngine, type EngineFailure } from '@/lib/engine/client';

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

export interface AuthorityDocumentState {
  path: string;
  state: 'attested' | 'mismatch' | 'missing';
  sha256?: string;
  rank?: number;
  nonOverridable?: boolean;
  detail?: string;
}

export interface AuthorityReport {
  /** Attested and unviolated — never merely "a document was found". */
  resolved: boolean;
  source: string | null;
  documents: AuthorityDocumentState[];
  violations: { key: string; heldBy: string; attemptedBy: string[]; detail: string }[];
}

export const governance = {
  proposals: () => readEngine<{ proposals: Proposal[]; cooldowns: Cooldown[] }>('/v1/proposals'),
  amendments: () => readEngine<{ amendments: Amendment[] }>('/v1/amendments'),
  metrics: () => readEngine<GovernanceMetrics>('/v1/metrics'),
  storage: () => readEngine<StorageReport>('/v1/storage'),
  authority: () => readEngine<AuthorityReport>('/v1/authority'),
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
