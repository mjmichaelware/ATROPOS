/* SPDX-License-Identifier: AGPL-3.0-only */

import { useEffect, useState } from 'react';
import { quota, type QuotaPayload } from '@/lib/quota/client';

/**
 * The quota summary chips for the home page (ADD-W-007).
 *
 * Shows healthy/cooling counts and a clickable chip for each provider.
 */
export function QuotaChips() {
  const [data, setData] = useState<{ providers: readonly { id: string; state: string; costMode: string; quotaWeight: number; configured: boolean; verified: boolean; usedRequests?: number; usedTokens?: number }[] } | null>(null);
  const [failure, setFailure] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const response = await fetch('/v1/quota');
      if (!response.ok) return;
      const result = await response.json();
      if (!cancelled) {
        if (result.ok) {
          setData(result.data);
        }
      }
    })();
    return () => { cancelled = true; };
  }, []);

  if (!data) return null;

  return (
    <div className="wb-quota-chips" role="status" aria-label="Provider quota summary">
      <div className="wb-quota-summary">
        <span className="wb-quota-healthy" title="Healthy providers">
          ✓ {data.providers.filter(p => p.state === 'ready').length} healthy
        </span>
        <span className="wb-quota-cooling" title="Providers cooling down">
          ⏳ {data.providers.filter(p => p.state === 'cooldown').length} cooling
        </span>
        <span className="wb-quota-total" title="Total providers">
          Σ {data.providers.length}
        </span>
      </div>
      <ul className="wb-quota-list" aria-label="Provider quota details">
        {data.providers.map((p) => (
          <li key={p.id} className="wb-quota-chip" title={`Weight: ${p.quotaWeight} · Requests: ${p.usedRequests ?? 0} · Tokens: ${p.usedTokens ?? 0}`}>
            <span className={`wb-quota-chip-state wb-quota-state-${p.state.toLowerCase()}`}>{p.id}</span>
            <span className="wb-quota-chip-mode">{p.costMode}</span>
            <span className="wb-quota-chip-weight">w:{p.quotaWeight}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
