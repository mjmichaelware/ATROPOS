/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * The cascade precedence snapshot (ADD-W-014).
 *
 * Renders the authority cascade from the bridge's `/v1/cascade` endpoint.
 * Shows resolved keys, violations, and undefined keys with their holders.
 */
'use client';

import { useEffect, useState } from 'react';
import { isCascadePayload } from '@atropos/web-contracts';

type CascadeData = {
  count?: number;
  resolvedCount?: number;
  violationCount?: number;
  undefinedCount?: number;
  keys?: readonly { key: string; value: string; heldBy: string; final: boolean; state: string }[];
  violations?: readonly { key: string; heldBy: string; attemptedBy: readonly string[]; reason: string }[];
  undefined?: readonly { key: string }[];
};

type Load = { kind: 'loading' } | { kind: 'fault'; detail: string; remedy: string } | { kind: 'ready'; data: { count?: number; resolvedCount?: number; violationCount?: number; undefinedCount?: number; keys?: readonly { key: string; value: string; heldBy: string; final: boolean; state: string }[]; violations?: readonly { key: string; heldBy: string; attemptedBy: readonly string[]; reason: string }[]; undefined?: readonly { key: string }[] } };

export function CascadeSnapshot() {
  const [state, setState] = useState<{ kind: 'loading' } | { kind: 'fault'; detail: string; remedy: string } | { kind: 'ready'; data: { count?: number; resolvedCount?: number; violationCount?: number; undefinedCount?: number; keys?: readonly { key: string; value: string; heldBy: string; final: boolean; state: string }[]; violations?: readonly { key: string; heldBy: string; attemptedBy: readonly string[]; reason: string }[]; undefined?: readonly { key: string }[] } }>({ kind: 'loading' });

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const response = await fetch('/v1/cascade');
      const result = await response.json();
      if (cancelled) return;
      if (!result.ok) {
        setState({ kind: 'fault', detail: result.detail, remedy: result.remedy });
      } else if (isCascadePayload(result.data)) {
        setState({ kind: 'ready', data: result.data });
      } else {
        setState({ kind: 'fault', detail: 'Malformed cascade payload', remedy: 'Check bridge /v1/cascade response' });
      }
    })();
    return () => { cancelled = true; };
  }, []);

  if (state.kind === 'loading') {
    return <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">Reading cascade…</p>;
  }

  if (state.kind === 'fault') {
    return (
      <div role="status" className="rounded-lg border border-sg-amber-300 bg-sg-amber-50 p-4 dark:border-sg-amber-900 dark:bg-sg-amber-900/20">
        <p className="font-semibold text-sg-amber-900 dark:text-sg-amber-100">Cascade unavailable</p>
        <p className="text-sm text-sg-amber-800 dark:text-sg-amber-200">{state.detail} {state.remedy}</p>
      </div>
    );
  }

  const data = state.data;
  const keys = data.keys ?? [];
  const violations = data.violations ?? [];
  const undefinedKeys = data.undefined ?? [];

  return (
    <section aria-label="Cascade precedence" className="space-y-3 rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800" data-testid="cascade-snapshot">
      <div className="flex items-baseline justify-between gap-3">
        <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">Cascade precedence</p>
        <span className="text-xs text-sg-neutral-600 dark:text-sg-neutral-400">
          {data.count ?? keys.length} key(s)
        </span>
      </div>

      <div className="space-y-2">
        {keys.length > 0 && (
          <div>
            <p className="wb-pane-title">Resolved ({data.resolvedCount ?? 0})</p>
            <ul className="space-y-1">
              {keys.filter((k): k is typeof k & { state: "resolved" } => k.state === 'resolved').map((k) => (
                <li key={k.key} className="wb-cascade-row wb-cascade-resolved">
                  <span className="wb-cascade-key">{k.key}</span>
                  <span className="wb-cascade-value">{k.value}</span>
                  <span className="wb-cascade-source">← {k.heldBy}</span>
                  {k.final && <span className="wb-cascade-final" title="Non-overridable">●</span>}
                </li>
              ))}
            </ul>
          </div>
        )}

        {data.violations?.length && (
          <div>
            <p className="wb-pane-title">Violations ({data.violationCount ?? 0})</p>
            <ul className="space-y-1">
              {data.violations.map((v) => (
                <li key={v.key} className="wb-cascade-row wb-cascade-violation">
                  <span className="wb-cascade-key">{v.key}</span>
                  <span className="wb-cascade-violation-reason">{v.reason}</span>
                  <span className="wb-cascade-held-by">held by {v.heldBy}</span>
                  <span className="wb-cascade-attempted">attempted by {v.attemptedBy?.join(', ') ?? '—'}</span>
                </li>
              ))}
            </ul>
          </div>
        )}

        {data.undefined?.length && (
          <div>
            <p className="wb-pane-title">Undefined ({data.undefinedCount ?? 0})</p>
            <ul className="space-y-1">
              {data.undefined.map((u) => (
                <li key={u.key} className="wb-cascade-row wb-cascade-undefined">
                  <span className="wb-cascade-key">{u.key}</span>
                  <span className="wb-cascade-undefined-note">No layer claims this key</span>
                </li>
              ))}
            </ul>
          </div>
        )}

        {data.count === 0 && (
          <p className="wb-pane-note">No cascade keys present.</p>
        )}
      </div>
    </section>
  );
}
