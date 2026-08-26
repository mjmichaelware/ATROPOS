/* SPDX-License-Identifier: AGPL-3.0-only */

import { useEffect, useState } from 'react';
import { governance, type EvidenceListPayload } from '@/lib/governance/client';
import { formatBytes } from '@/lib/governance/client';
import { isEvidenceRef, type EvidenceRef } from '@atropos/web-contracts';

/**
 * ADD-W-020: Evidence ledger browser under /developer/ledger.
 *
 * Read-only browser over the engine's evidence list API at `/v1/evidence/list`.
 * Shows evidence refs with kind, path, SHA-256, bytes, and created timestamp.
 * No second evidence store — reads directly from the engine's projection.
 */
export function EvidenceLedgerBrowser() {
  const [payload, setPayload] = useState<EvidenceListPayload | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const result = await governance.evidenceList();
        if (cancelled) return;
        if (result.ok) {
          setPayload(result.data);
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
    return <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">Loading evidence ledger…</p>;
  }

  if (error) {
    return (
      <div role="status" className="rounded-lg border border-sg-amber-300 bg-sg-amber-50 p-4 dark:border-sg-amber-900 dark:bg-sg-amber-900/20">
        <p className="wb-fault">Evidence ledger unavailable</p>
        <p className="wb-pane-note">{error}</p>
      </div>
    );
  }

  if (!payload || payload.items.length === 0) {
    return (
      <div className="rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800">
        <p className="wb-pane-title">Evidence ledger</p>
        <p className="wb-pane-note">No evidence recorded.</p>
      </div>
    );
  }

  return (
    <section className="space-y-3" data-testid="evidence-ledger-browser">
      <header className="flex items-baseline justify-between gap-3">
        <p className="wb-pane-title">Evidence ledger</p>
        <span className="text-xs text-sg-neutral-500">{payload.items.length} item(s)</span>
      </header>
      <ul className="space-y-2">
        {payload.items.map((item) => (
          <li key={item.id} className="rounded-lg border border-sg-neutral-200 p-3 dark:border-sg-neutral-800">
            <div className="flex items-baseline justify-between gap-3">
              <span className="font-mono text-sm text-sg-neutral-900 dark:text-sg-neutral-100">{item.kind}</span>
              <span className="text-xs text-sg-neutral-500">{formatBytes(item.bytes)}</span>
            </div>
            <div className="mt-1 flex items-baseline gap-2 text-xs">
              <span className="font-mono text-sg-neutral-700 dark:text-sg-neutral-300 truncate flex-1" title={item.path}>
                {item.path}
              </span>
              <span className="text-sg-neutral-500 flex-shrink-0">{new Date(item.createdAt).toLocaleString()}</span>
            </div>
            <div className="mt-1">
              <code className="font-mono text-xs text-sg-neutral-500 break-all">{item.sha256}</code>
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}