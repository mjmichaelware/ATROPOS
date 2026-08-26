/* SPDX-License-Identifier: AGPL-3.0-only */

import { useEffect, useState } from 'react';
import { governance, type GovernanceMetrics } from '@/lib/governance/client';
import { formatRate, formatBytes } from '@/lib/governance/client';

/**
 * ADD-W-025: P20 ops dashboard charts ONLY from metrics endpoints that exist.
 *
 * Charts ONLY from real metrics endpoints; zero invented series.
 * If no metrics: explicit empty state + BLOCKED note.
 */
export function OpsDashboard() {
  const [metrics, setMetrics] = useState<GovernanceMetrics | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const result = await governance.metrics();
        if (cancelled) return;
        if (result.ok) {
          setMetrics(result.data);
        } else {
          setError(`${result.detail} ${result.remedy}`);
        }
      } catch (error) {
        setError(String(error));
      } finally {
        if (!cancelled) setLoading(false);
      }
      return () => { cancelled = true; };
    })();
  }, []);

  if (loading) {
    return <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">Loading metrics…</p>;
  }

  if (error) {
    return (
      <div role="status" className="rounded-lg border border-sg-amber-300 bg-sg-amber-50 p-4 dark:border-sg-amber-900 dark:bg-sg-amber-900/20">
        <p className="wb-fault">Metrics unavailable</p>
        <p className="wb-pane-note">{error}</p>
      </div>
    );
  }

  if (!metrics) {
    return (
      <div className="rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800">
        <p className="wb-pane-title">P20 ops dashboard</p>
        <p className="wb-pane-note">No metrics data available.</p>
      </div>
    );
  }

  const metricItems = [
    { label: 'Healthy', value: metrics.healthy ? 'yes' : 'no', color: metrics.healthy ? 'text-sg-green-600' : 'text-sg-red-600' },
    { label: 'False-verified rate', value: formatRate(metrics.falseVerifiedRate) },
    { label: 'Territory violation rate', value: formatRate(metrics.territoryViolationRate) },
    { label: 'Recovery completeness', value: formatRate(metrics.recoveryCompleteness) },
    { label: 'Observation success', value: formatRate(metrics.observationSuccess) },
    { label: 'Tokens/verified change', value: metrics.tokensPerVerifiedChange !== null ? formatBytes(metrics.tokensPerVerifiedChange) : 'not measured' },
  ];

  return (
    <section className="space-y-3" data-testid="ops-dashboard">
      <h3 className="wb-pane-title">P20 ops dashboard</h3>
      {metrics.unmeasured.length > 0 && (
        <p className="text-xs text-sg-amber-600 dark:text-sg-amber-400">
          Unmeasured: {metrics.unmeasured.join(', ')}
        </p>
      )}
      <dl className="grid grid-cols-1 md:grid-cols-2 gap-3">
        {metricItems.map((item) => (
          <div key={item.label} className="rounded-lg border border-sg-neutral-200 p-3 dark:border-sg-neutral-800">
            <dt className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">{item.label}</dt>
            <dd className={`font-mono text-lg ${item.color || ''}`}>{item.value}</dd>
          </div>
        ))}
      </dl>
      {metrics.unmeasured.length > 0 && (
        <p className="text-xs text-sg-amber-600 dark:text-sg-amber-400">
          BLOCKED: Some metrics are unmeasured — charts only render measured series.
        </p>
      )}
    </section>
  );
}