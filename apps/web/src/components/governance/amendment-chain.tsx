/* SPDX-License-Identifier: AGPL-3.0-only */

import { useEffect, useState } from 'react';
import { governance, type Amendment } from '@/lib/governance/client';

/**
 * ADD-W-022: Amendment hash chain + re-verify.
 *
 * Displays the chain of amendments with their hashes and re-verify action.
 * Only renders when the amendments API is available.
 */
export function AmendmentChain() {
  const [amendments, setAmendments] = useState<Amendment[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const result = await governance.amendments();
        if (cancelled) return;
        if (result.ok) {
          setAmendments(result.data.amendments);
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
    return <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">Loading amendments…</p>;
  }

  if (error) {
    return (
      <div role="status" className="rounded-lg border border-sg-amber-300 bg-sg-amber-50 p-4 dark:border-sg-amber-900 dark:bg-sg-amber-900/20">
        <p className="wb-fault">Amendments unavailable</p>
        <p className="wb-pane-note">{error}</p>
      </div>
    );
  }

  if (amendments.length === 0) {
    return (
      <div className="rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800">
        <p className="wb-pane-title">Amendment chain</p>
        <p className="wb-pane-note">No amendments recorded.</p>
      </div>
    );
  }

  return (
    <section className="space-y-3" data-testid="amendment-chain">
      <h3 className="wb-pane-title">Amendment chain</h3>
      <ol className="space-y-2">
        {amendments.map((amendment, idx) => (
          <li key={amendment.id} className="rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800">
            <div className="flex items-baseline justify-between gap-3">
              <span className="font-mono text-sm text-sg-neutral-900 dark:text-sg-neutral-100">
                {amendment.id}
              </span>
              <span className="text-xs text-sg-neutral-500">
                {new Date(amendment.acceptedAt).toLocaleString()}
              </span>
            </div>
            <div className="mt-2 grid grid-cols-2 gap-2 text-xs">
              <div>
                <span className="text-sg-neutral-500">Proposal: </span>
                <span className="font-mono">{amendment.proposalId}</span>
              </div>
              <div>
                <span className="text-sg-neutral-500">Supersedes: </span>
                <span className="font-mono">{amendment.supersedes.slice(0, 12)}…</span>
              </div>
              <div className="col-span-2">
                <span className="text-sg-neutral-500">Accepted by: </span>
                <span>{amendment.acceptedBy}</span>
              </div>
              <div className="col-span-2">
                <span className="text-sg-neutral-500">Evidence hashes: </span>
                <span className="font-mono text-xs">
                  {amendment.evidenceHashes.length > 0
                    ? amendment.evidenceHashes.map(h => h.slice(0, 12) + '…').join(', ')
                    : 'none'}
                </span>
              </div>
            </div>
            <div className="mt-3 flex gap-2">
              <button
                type="button"
                className="px-3 py-1 text-xs bg-sg-neutral-100 dark:bg-sg-neutral-800 rounded hover:bg-sg-neutral-200 dark:hover:bg-sg-neutral-700"
                disabled
              >
                Re-verify (todo)
              </button>
              <button
                type="button"
                className="px-3 py-1 text-xs bg-sg-neutral-100 dark:bg-sg-neutral-800 rounded hover:bg-sg-neutral-200 dark:hover:bg-sg-neutral-700"
                disabled
              >
                View evidence (todo)
              </button>
            </div>
          </li>
        ))}
      </ol>
      {amendments.length === 0 && (
        <p className="wb-pane-note">No amendments recorded.</p>
      )}
    </section>
  );
}