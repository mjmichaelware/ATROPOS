/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * The engine's conversation list for the session-first home (F-WEB-002).
 *
 * The home page is "sessions or stream" per the atom; this renders the list
 * half. Empty, unreachable and faulted stay three different states: a bridge
 * that is down must not look like an operator with no conversations.
 */
'use client';

import { useEffect, useState } from 'react';
import { mostRecentFirst, sessions, type BridgeSession } from '@/lib/sessions/client';

type ListState =
  | { kind: 'loading' }
  | { kind: 'fault'; detail: string; remedy: string }
  | { kind: 'empty' }
  | { kind: 'ready'; items: BridgeSession[] };

export function SessionList({ limit = 8 }: { limit?: number }) {
  const [state, setState] = useState<ListState>({ kind: 'loading' });

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const result = await sessions.read();
      if (cancelled) return;
      if (!result.ok) {
        setState({ kind: 'fault', detail: result.detail, remedy: result.remedy });
      } else if (result.data.length === 0) {
        setState({ kind: 'empty' });
      } else {
        setState({ kind: 'ready', items: mostRecentFirst(result.data).slice(0, limit) });
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [limit]);

  return (
    <section aria-label="Conversations" data-testid="session-list">
      {state.kind === 'loading' && (
        <p className="wb-pane-note">Reading conversations…</p>
      )}
      {state.kind === 'fault' && (
        <div role="status" className="wb-fault-block">
          <p className="wb-fault">{state.detail}</p>
          <p className="wb-pane-note">{state.remedy}</p>
        </div>
      )}
      {state.kind === 'empty' && (
        <p className="wb-pane-note">
          No conversations yet — send the first message from any project.
        </p>
      )}
      {state.kind === 'ready' && (
        <ul className="wb-session-list">
          {state.items.map((session) => (
            <li key={session.id}>
              <article className="wb-session-row" title={session.id}>
                <span className="wb-session-title">{session.title}</span>
                <span className="wb-session-meta">
                  {session.turnCount} turn{session.turnCount === 1 ? '' : 's'} ·{' '}
                  {new Date(session.updatedAt).toLocaleString()}
                </span>
              </article>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
