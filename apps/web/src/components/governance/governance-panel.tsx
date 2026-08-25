/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import { useEffect, useState } from 'react';
import { governance, formatRate, type Amendment, type Cooldown, type GovernanceMetrics, type Proposal } from '@/lib/governance/client';
import { QuarantineSnapshot } from './quarantine-snapshot';

/**
 * The Phase 20 governance surface: proposals, amendments, cooldowns, metrics, quarantine.
 *
 * `C4-IF-02..05` and `P20-S04`. §20.20 sets the standard this has to meet —
 * "the system must explain why, where, how it changed and why the result is
 * better" — so every proposal shows its predeclared metric, its territory, and
 * its rollback rather than a summary and a button.
 *
 * Two refusals are built in. A proposal that is structurally incomplete cannot
 * be presented as approvable, because §20.6 makes the six declarations a
 * condition of the proposal existing at all. And a metric with no measurement
 * renders the words "not measured" rather than a zero — the distinction
 * `P20-S04` depends on is exactly the one a `?? 0` would erase.
 */
export function GovernancePanel() {
  const [proposals, setProposals] = useState<Proposal[]>([]);
  const [cooldowns, setCooldowns] = useState<Cooldown[]>([]);
  const [amendments, setAmendments] = useState<Amendment[]>([]);
  const [metrics, setMetrics] = useState<GovernanceMetrics | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const [p, a, m] = await Promise.all([
        governance.proposals(),
        governance.amendments(),
        governance.metrics(),
      ]);
      if (cancelled) return;
      if (!p.ok) setFailure(`${p.detail} ${p.remedy}`);
      else {
        setProposals(p.data.proposals);
        setCooldowns(p.data.cooldowns);
      }
      if (a.ok) setAmendments(a.data.amendments);
      if (m.ok) setMetrics(m.data);
      setLoading(false);
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  if (loading) {
    return <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">Reading governance state…</p>;
  }

  if (failure) {
    return (
      <div role="status" className="rounded-lg border border-sg-amber-300 bg-sg-amber-50 p-4 dark:border-sg-amber-900 dark:bg-sg-amber-900/20">
        <p className="font-semibold text-sg-amber-900 dark:text-sg-amber-100">
          Engine not answering — governance state unknown
        </p>
        <p className="text-sm text-sg-amber-800 dark:text-sg-amber-200">{failure}</p>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <section className="space-y-3">
        <h2 className="text-xl font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
          Metrics
        </h2>
        {metrics ? (
          <dl className="grid grid-cols-2 gap-3 md:grid-cols-3">
            {[
              ['False VERIFIED rate', metrics.falseVerifiedRate],
              ['Territory violations', metrics.territoryViolationRate],
              ['Recovery completeness', metrics.recoveryCompleteness],
              ['Observation success', metrics.observationSuccess],
            ].map(([label, value]) => (
              <div key={label as string} className="rounded-lg border border-sg-neutral-200 p-3 dark:border-sg-neutral-800">
                <dt className="text-xs text-sg-neutral-600 dark:text-sg-neutral-400">{label as string}</dt>
                {/* "not measured" rather than 0% — the distinction P20-S04 needs. */}
                <dd className="font-medium text-sg-neutral-900 dark:text-sg-neutral-50">
                  {formatRate(value as number | null)}
                </dd>
              </div>
            ))}
          </dl>
        ) : (
          <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">No metrics served.</p>
        )}
        {metrics && metrics.unmeasured.length > 0 && (
          <p className="text-xs text-sg-neutral-500">
            Unmeasured: {metrics.unmeasured.join(', ')} — absence of a measurement is not a passing score.
          </p>
        )}
      </section>

      {cooldowns.length > 0 && (
        <section className="space-y-2">
          <h2 className="text-xl font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
            Observation periods
          </h2>
          <ul className="space-y-1 text-sm">
            {cooldowns.map((cooldown) => (
              <li key={cooldown.subsystem} className="text-sg-neutral-700 dark:text-sg-neutral-300">
                <span className="font-mono">{cooldown.subsystem}</span> cannot change again for{' '}
                {cooldown.remainingSeconds}s.
              </li>
            ))}
          </ul>
        </section>
      )}

      <section className="space-y-3">
        <h2 className="text-xl font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
          Quarantine
        </h2>
        <QuarantineSnapshot />
      </section>

      <section className="space-y-3">
        <h2 className="text-xl font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
          Proposals
        </h2>
        {proposals.length === 0 ? (
          <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">
            No proposals. The system has not proposed a change to itself.
          </p>
        ) : (
          <ul className="space-y-3">
            {proposals.map((proposal) => (
              <li key={proposal.id} className="space-y-2 rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800">
                <div className="flex items-start justify-between gap-3">
                  <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
                    {proposal.summary}
                  </p>
                  <span className="shrink-0 rounded border border-sg-neutral-300 px-2 py-0.5 text-xs uppercase dark:border-sg-neutral-700">
                    {proposal.state}
                  </span>
                </div>
                <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">
                  Proposed by <span className="font-mono">{proposal.proposedBy}</span> · territory{' '}
                  <span className="font-mono">{proposal.territory.join(', ') || 'none declared'}</span>
                </p>
                <p className="text-sm text-sg-neutral-700 dark:text-sg-neutral-300">
                  Metric <span className="font-mono">{proposal.metric.name}</span>:{' '}
                  {proposal.metric.baseline} → {proposal.metric.target}{' '}
                  {proposal.metric.declared ? '(declared before the change)' : '(NOT declared)'}
                </p>
                <p className="text-sm text-sg-neutral-700 dark:text-sg-neutral-300">
                  Rollback: {proposal.rollback || 'none stated'}
                </p>
                {/* 20.6: a proposal missing a declaration is not approvable. */}
                {!proposal.complete && (
                  <p className="text-sm font-medium text-sg-red-700 dark:text-sg-red-300">
                    Not approvable — missing {proposal.missing.join(', ')}.
                  </p>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="space-y-3">
        <h2 className="text-xl font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
          Accepted amendments
        </h2>
        {amendments.length === 0 ? (
          <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">
            No amendments. Original authority is unmodified.
          </p>
        ) : (
          <ul className="space-y-2">
            {amendments.map((amendment) => (
              <li key={amendment.id} className="rounded-lg border border-sg-neutral-200 p-3 text-sm dark:border-sg-neutral-800">
                <p className="font-mono text-xs text-sg-neutral-700 dark:text-sg-neutral-300">
                  {amendment.sha256}
                </p>
                {/* The superseded hash stays visible: 20.1 keeps the original
                    readable, so a reader can always tell what was added later. */}
                <p className="text-xs text-sg-neutral-600 dark:text-sg-neutral-400">
                  supersedes <span className="font-mono">{amendment.supersedes}</span> · accepted by{' '}
                  {amendment.acceptedBy} · {amendment.evidenceHashes.length} evidence hash(es)
                </p>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}