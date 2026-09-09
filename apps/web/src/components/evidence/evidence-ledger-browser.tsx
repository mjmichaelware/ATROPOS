/* SPDX-License-Identifier: AGPL-3.0-only */

import { useEffect, useState } from 'react';
import { readEngine } from '@/lib/engine/client';
import { formatBytes } from '@/lib/governance/client';
import { isEvidenceRef, type EvidenceRef } from '@atropos/web-contracts';

/**
 * ADD-W-020: Evidence ledger browser under /developer/ledger.
 *
 * Now reads from `/v1/evidence/ledger` which provides a richer view
 * with evidence store, memory ledger, and proposal store projections.
 * Read-only browser over the engine's evidence ledger API.
 */

interface EvidenceLedgerItem {
  id: string;
  kind: string;
  path: string;
  sha256: string;
  bytes: number;
  createdAt: string;
  evidenceRef?: EvidenceRef;
  source?: string;
}

interface EvidenceLedgerPayload {
  ok: true;
  items: EvidenceLedgerItem[];
  memoryLedger?: Array<{ id: string; kind: string; sha256: string; createdAt: string }>;
  proposalStore?: Array<{ id: string; sha256: string; status: string; createdAt: string }>;
}

export function EvidenceLedgerBrowser() {
  const [payload, setPayload] = useState<EvidenceLedgerPayload | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const result = await readEngine<EvidenceLedgerPayload>('/v1/evidence/ledger');
        if (cancelled) return;
        if (result.ok) {
          setPayload(result.data);
        } else {
          // Fall back to /v1/evidence/list
          const fallback = await readEngine<{ ok: true; items: Array<{ id: string; kind: string; path: string; sha256: string; bytes: number; createdAt: string }> }>('/v1/evidence/list');
          if (cancelled) return;
          if (fallback.ok) {
            setPayload({ items: fallback.data.items });
          } else {
            setError(`${fallback.detail} ${fallback.remedy}`);
          }
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
    return <p className="wb-repro-note">Loading evidence ledger…</p>;
  }

  if (error) {
    return (
      <div className="wb-repro wb-repro-error" role="status">
        <p className="wb-fault">Evidence ledger unavailable</p>
        <p className="wb-pane-note">{error}</p>
      </div>
    );
  }

  if (!payload || payload.items.length === 0) {
    return (
      <div className="wb-repro wb-repro-empty">
        <p className="wb-pane-title">Evidence ledger</p>
        <p className="wb-pane-note">No evidence recorded.</p>
      </div>
    );
  }

  return (
    <section className="wb-evidence-ledger" data-testid="evidence-ledger-browser">
      <header className="wb-evidence-header">
        <h2 className="wb-pane-title">Evidence Ledger</h2>
        <span className="wb-evidence-count">{payload.items.length} item(s)</span>
      </header>

      <ul className="wb-evidence-list" role="list" aria-label="Evidence items">
        {payload.items.map((item) => (
          <li key={item.id} className="wb-evidence-item">
            <div className="wb-evidence-row wb-evidence-main">
              <span className="wb-evidence-kind">{item.kind}</span>
              <span className="wb-evidence-path" title={item.path}>{item.path}</span>
              <span className="wb-evidence-size">{formatBytes(item.bytes)}</span>
              <span className="wb-evidence-time">{new Date(item.createdAt).toLocaleString()}</span>
            </div>
            <div className="wb-evidence-row wb-evidence-hash">
              <code className="wb-evidence-sha256">{item.sha256}</code>
              {item.evidenceRef && isEvidenceRef(item.evidenceRef) && (
                <details className="wb-evidence-ref">
                  <summary>Evidence Reference</summary>
                  <dl className="wb-evidence-ref-dl">
                    <dt>CAS Hash</dt>
                    <dd className="wb-mono">{item.evidenceRef.casHash}</dd>
                    <dt>Claim ID</dt>
                    <dd className="wb-mono">{item.evidenceRef.claimId}</dd>
                    <dt>Gates</dt>
                    <dd>
                      <ul>
                        {item.evidenceRef.gateIds.map((gate) => (
                          <li key={gate} className="wb-mono">{gate}</li>
                        ))}
                      </ul>
                    </dd>
                  </dl>
                </details>
              )}
            </div>
            {item.source && <span className="wb-evidence-source">Source: {item.source}</span>}
          </li>
        ))}
      </ul>

      {payload.memoryLedger && payload.memoryLedger.length > 0 && (
        <section className="wb-evidence-section">
          <h3 className="wb-evidence-section-title">Memory Ledger</h3>
          <ul className="wb-evidence-list">
            {payload.memoryLedger.map((entry) => (
              <li key={entry.id} className="wb-evidence-item wb-evidence-memory">
                <div className="wb-evidence-row">
                  <span className="wb-evidence-kind">{entry.kind}</span>
                  <span className="wb-evidence-hash">{entry.sha256}</span>
                  <span className="wb-evidence-time">{new Date(entry.createdAt).toLocaleString()}</span>
                </div>
              </li>
            ))}
          </ul>
        </section>
      )}

      {payload.proposalStore && payload.proposalStore.length > 0 && (
        <section className="wb-evidence-section">
          <h3 className="wb-evidence-section-title">Proposal Store</h3>
          <ul className="wb-evidence-list">
            {payload.proposalStore.map((entry) => (
              <li key={entry.id} className="wb-evidence-item wb-evidence-proposal">
                <div className="wb-evidence-row">
                  <span className="wb-evidence-id">{entry.id}</span>
                  <span className="wb-evidence-hash">{entry.sha256}</span>
                  <span className="wb-evidence-status">{entry.status}</span>
                  <span className="wb-evidence-time">{new Date(entry.createdAt).toLocaleString()}</span>
                </div>
              </li>
            ))}
          </ul>
        </section>
      )}
    </section>
  );
}