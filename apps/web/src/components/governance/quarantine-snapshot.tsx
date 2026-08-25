/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import { useEffect, useState } from 'react';

interface QuarantineData {
  ok: true;
  count: number;
  observationCount: number;
  items?: Array<{ id: string; title: string; summary: string; state: string; createdAt: string }>;
  observation?: Array<{ subsystem: string; startedAt: string; durationSeconds: number }>;
}

type Load = { kind: 'loading' } | { kind: 'fault'; detail: string; remedy: string } | { kind: 'ready'; data: { count: number; observationCount: number; items?: any[]; observation?: any[] } };

export function QuarantineSnapshot() {
  const [state, setState] = useState<{ kind: 'loading' } | { kind: 'fault'; detail: string; remedy: string } | { kind: 'ready'; data: { count: number; observationCount: number; items?: any[]; observation?: any[] } }>({ kind: 'loading' });

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const response = await fetch('/v1/quarantine');
      const result = await response.json();
      if (cancelled) return;
      if (!result.ok) {
        setState({ kind: 'fault', detail: result.detail, remedy: result.remedy });
      } else if (result.ok) {
        setState({ kind: 'ready', data: { count: result.data.count, observationCount: result.data.observationCount, items: result.data.items, observation: result.data.observation } });
      } else {
        setState({ kind: 'fault', detail: 'Malformed quarantine payload', remedy: 'Check bridge /v1/quarantine response' });
      }
    })();
    return () => { cancelled = true; };
  }, []);

  if (state.kind === 'loading') {
    return <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">Reading quarantine…</p>;
  }

  if (state.kind === 'fault') {
    return (
      <div role="status" className="rounded-lg border border-sg-amber-300 bg-sg-amber-50 p-4 dark:border-sg-amber-900 dark:bg-sg-amber-900/20">
        <p className="font-semibold text-sg-amber-900 dark:text-sg-amber-100">Quarantine unavailable</p>
        <p className="text-sm text-sg-amber-800 dark:text-sg-amber-200">{state.detail} {state.remedy}</p>
      </div>
    );
  }

  const data = state.data;

  if (data.count === 0 && data.observationCount === 0) {
    return (
      <div className="rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800">
        <p className="wb-pane-title">Quarantine</p>
        <p className="wb-pane-note">No proposals are currently quarantined.</p>
        <p className="wb-pane-note">No observation periods active.</p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {state.data.items && state.data.items.length > 0 && (
        <div className="rounded-lg border border-sg-amber-300 bg-sg-amber-50 p-4 dark:border-sg-amber-900 dark:bg-sg-amber-900/20">
          <p className="font-semibold text-sg-amber-900 dark:text-sg-amber-100">
            Quarantined proposals ({state.data.count})
          </p>
          <ul className="space-y-2 mt-2">
            {state.data.items.map((item: any) => (
              <li key={item.id} className="space-y-1 rounded-lg border border-sg-amber-400 bg-white p-3 dark:border-sg-amber-800 dark:bg-sg-amber-900/20">
                <p className="font-medium text-sg-amber-900 dark:text-sg-amber-100">{item.title}</p>
                <p className="text-sm text-sg-amber-800 dark:text-sg-amber-200">{item.summary}</p>
                <div className="flex items-center gap-2 text-xs">
                  <span className="px-2 py-0.5 rounded bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-100">
                    {item.state}
                  </span>
                  <span className="text-sg-amber-700 dark:text-sg-amber-300">
                    Created {new Date(item.createdAt).toLocaleString()}
                  </span>
                </div>
              </li>
            ))}
          </ul>
        </div>
      )}

      {state.data.observation && state.data.observation.length > 0 && (
        <div className="rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800">
          <p className="wb-pane-title">Observation periods ({state.data.observationCount})</p>
          <ul className="space-y-2 mt-2">
            {state.data.observation.map((obs: any) => (
              <li key={obs.subsystem} className="wb-auth-row">
                <span className="wb-auth-state" data-auth="observation">OBSERVING</span>
                <span className="wb-auth-path">{obs.subsystem}</span>
                <span className="wb-auth-hash">
                  {Math.floor(obs.durationSeconds / 3600)}h {Math.floor((obs.durationSeconds % 3600) / 60)}m remaining
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {(state.data.count === 0 && state.data.observationCount === 0) && (
        <div className="rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800">
          <p className="wb-pane-title">Quarantine</p>
          <p className="wb-pane-note">No proposals are currently quarantined.</p>
          <p className="wb-pane-note">No observation periods active.</p>
        </div>
      )}
    </div>
  );
}
