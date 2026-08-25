/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import { useEffect, useState } from 'react';

type EvidenceListItem = {
  id: string;
  task: string;
  state: string;
  evidence: string | null;
  updatedAt: string | null;
};

type Load = { kind: 'loading' } | { kind: 'fault'; detail: string; remedy: string } | { kind: 'ready'; data: { items: any[]; surveyed: number; count: number } };

export function EvidenceLedger() {
  const [state, setState] = useState<{ kind: 'loading' } | { kind: 'fault'; detail: string; remedy: string } | { kind: 'ready'; data: { items: any[]; surveyed: number; count: number } }>({ kind: 'loading' });
  const [filter, setFilter] = useState('');

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const response = await fetch('/v1/evidence/list?limit=200');
      const result = await response.json();
      if (cancelled) return;
      if (!result.ok) {
        setState({ kind: 'fault', detail: result.detail, remedy: result.remedy });
      } else if (result.ok) {
        setState({ kind: 'ready', data: { items: result.data.items ?? [], surveyed: result.data.surveyed ?? 0, count: result.data.count ?? 0 } });
      } else {
        setState({ kind: 'fault', detail: 'Malformed evidence list', remedy: 'Check bridge /v1/evidence/list response' });
      }
    })();
    return () => { cancelled = true; };
  }, []);

  const filteredItems = state.kind === 'ready' ? state.data.items.filter((item: any) =>
    item.id.toLowerCase().includes(filter.toLowerCase()) ||
    item.task.toLowerCase().includes(filter.toLowerCase()) ||
    (item.evidence && item.evidence.toLowerCase().includes(filter.toLowerCase()))
  ) : [];

  if (state.kind === 'loading') {
    return <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">Reading evidence ledger…</p>;
  }

  if (state.kind === 'fault') {
    return (
      <div role="status" className="rounded-lg border border-sg-amber-300 bg-sg-amber-50 p-4 dark:border-sg-amber-900 dark:bg-sg-amber-900/20">
        <p className="wb-fault">{state.detail}</p>
        <p className="wb-pane-note">{state.remedy}</p>
      </div>
    );
  }

  if (state.kind === 'ready' && state.data.count === 0) {
    return (
      <div className="rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800">
        <p className="wb-pane-title">Evidence ledger</p>
        <p className="wb-pane-note">No evidence entries found in recent queue work.</p>
      </div>
    );
  }

  return (
    <section aria-label="Evidence ledger" className="space-y-4" data-testid="evidence-ledger">
      <div className="flex items-baseline justify-between gap-3">
        <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
          Evidence ledger
        </p>
        <div className="flex items-center gap-2">
          <input
            type="search"
            placeholder="Filter by id, task, or evidence…"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            className="wb-input"
            aria-label="Filter evidence entries"
          />
          <span className="wb-pane-note">
            {filteredItems.length} of {state.kind === 'ready' ? state.data.count : 0} entries
          </span>
        </div>
      </div>

      <div className="overflow-x-auto rounded-lg border border-sg-neutral-200 dark:border-sg-neutral-800">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-sg-neutral-50 dark:bg-sg-neutral-900 text-left">
              <th className="p-2 font-medium text-sg-neutral-700 dark:text-sg-neutral-300">Queue ID</th>
              <th className="p-2 font-medium text-sg-neutral-700 dark:text-sg-neutral-300">Task</th>
              <th className="p-2 font-medium text-sg-neutral-700 dark:text-sg-neutral-300">State</th>
              <th className="p-2 font-medium text-sg-neutral-700 dark:text-sg-neutral-300">Evidence</th>
              <th className="p-2 font-medium text-sg-neutral-700 dark:text-sg-neutral-300">Updated</th>
            </tr>
          </thead>
          <tbody>
            {filteredItems.map((item: any) => (
              <tr key={item.id} className="border-t border-sg-neutral-200 dark:border-sg-neutral-800 hover:bg-sg-neutral-50 dark:hover:bg-sg-neutral-800">
                <td className="p-2 font-mono text-xs text-sg-neutral-900 dark:text-sg-neutral-100">{item.id}</td>
                <td className="p-2 text-sg-neutral-700 dark:text-sg-neutral-300">{item.task}</td>
                <td className="p-2">
                  <span className={`wb-status wb-status-${item.state.toLowerCase()}`}>{item.state}</span></td>
                <td className="p-2 font-mono text-xs text-sg-neutral-600 dark:text-sg-neutral-400 max-w-xs truncate">
                  {item.evidence || '—'}
                </td>
                <td className="p-2 text-sg-neutral-500 dark:text-sg-neutral-400 whitespace-nowrap">
                  {item.updatedAt ? new Date(item.updatedAt).toLocaleString() : '—'}
                </td>
              </tr>
            ))}
            {filteredItems.length === 0 && state.kind === 'ready' && (
              <tr>
                <td colSpan={5} className="p-4 text-center text-sg-neutral-500">
                  No matching entries.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <p className="wb-pane-note">
        {state.kind === 'ready' ? `Surveyed {state.data.surveyed} queue entries · {state.data.count} with evidence` : ''}
      </p>
    </section>
  );
}

