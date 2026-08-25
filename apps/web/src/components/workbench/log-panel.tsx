/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * The bottom output panel, v1 (F-WEB-006): streaming logs only.
 *
 * The atom's scope line is explicit — "v1 logs only, PTY later" — so there is
 * no shell here and none may grow by accident. The panel subscribes to the
 * engine's event stream, which is the one ordered record of pipeline state
 * changes the engine already keeps; this component owns a viewport over it,
 * not a second event store.
 *
 * SSE rather than polling for v1: the bridge exposes `/v1/events/stream` as
 * a pushed stream. When SSE is unavailable the fetch swaps for polling and
 * nothing else in this file changes — the contract is "ordered ActivityEvent
 * list", however it arrives.
 */
'use client';

import { useEffect, useRef, useState } from 'react';
import { subscribeActivity, type ActivityEvent as EventActivityEvent } from '@/lib/events/client';

export function LogPanel() {
  const [events, setEvents] = useState<EventActivityEvent[]>([]);
  const [failure, setFailure] = useState<string | null>(null);
  const [open, setOpen] = useState(true);
  const [connection, setConnection] = useState<'idle' | 'open' | 'closed' | 'unsupported' | 'failed'>('idle');
  const scrollRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!open) return;
    let cancelled = false;

    if (typeof EventSource !== 'undefined') {
      // SSE path
      const url = new URL(`${process.env.NEXT_PUBLIC_ATROPOS_BRIDGE_URL ?? 'http://127.0.0.1:4317'}/v1/events/stream`);
      const eventSource = new EventSource(url.toString());

      eventSource.addEventListener('open', () => {
        if (!cancelled) setConnection('open');
      });

      eventSource.addEventListener('message', (event) => {
        if (cancelled) return;
        try {
          const parsed = JSON.parse(event.data);
          if (parsed.cursor !== undefined && parsed.type && parsed.timestamp) {
            // Map the event inline for the log panel
            const mapped = mapLogEvent(parsed);
            if (mapped) {
              setEvents((prev) => [...prev, mapped]);
            }
          }
        } catch {
          // Ignore malformed frames
        }
      });

      eventSource.addEventListener('error', () => {
        if (!cancelled) setConnection('failed');
        eventSource.close();
      });

      return () => {
        cancelled = true;
        eventSource.close();
      };
    } else {
      // Polling fallback
      setConnection('unsupported');
      let cancelled = false;
      async function poll() {
        if (cancelled) return;
        const response = await fetch(`${process.env.NEXT_PUBLIC_ATROPOS_BRIDGE_URL ?? 'http://127.0.0.1:4317'}/v1/events`, {
          cache: 'no-store',
        });
        if (cancelled) return;
        if (response.ok) {
          const body = await response.json();
          const events = (body.events ?? []).map(mapLogEvent).filter((e: unknown): e is EventActivityEvent => e !== null) as EventActivityEvent[];
          setEvents((prev) => {
            // Merge with existing, keeping unique by id
            const seen = new Set(prev.map((e) => e.id));
            const next = [...prev];
            for (const e of events) {
              if (!seen.has(e.id)) {
                next.push(e);
                seen.add(e.id);
              }
            }
            // Keep last 200
            if (next.length > 200) return next.slice(-200);
            return next;
          });
          setFailure(null);
        } else {
          setFailure(`Event stream failed: ${response.status}`);
        }
      }
      void poll();
      const timer = setInterval(poll, 4000);
      return () => {
        cancelled = true;
        clearInterval(timer);
      };
    }
  }, [open]);

  // Keep the newest event visible, the way a terminal tail does.
  useEffect(() => {
    const node = scrollRef.current;
    if (node && open) node.scrollTop = node.scrollHeight;
  }, [events, open]);

  return (
    <div className="wb-logpanel" data-testid="log-panel">
      <div className="wb-logpanel-header">
        <span className="wb-logpanel-title">Output</span>
        <span className="wb-logpanel-connection">
          {connection === 'open' && <span className="wb-stream-live" aria-hidden="true" />}
          {connection === 'open' && ' Live'}
          {connection === 'unsupported' && ' Polling'}
          {connection === 'failed' && ' Failed'}
          {connection === 'closed' && ' Closed'}
        </span>
        <button
          type="button"
          aria-expanded={open}
          aria-label={open ? 'Collapse output panel' : 'Expand output panel'}
          className="wb-logpanel-toggle"
          onClick={() => setOpen((value) => !value)}
        >
          {open ? '▾' : '▸'}
        </button>
      </div>
      {open && (
        <div ref={scrollRef} className="wb-logpanel-body" role="log" aria-live="polite">
          {failure != null ? (
            <p className="wb-log-fault">{failure}</p>
          ) : events.length === 0 ? (
            <p className="wb-log-empty">No pipeline activity yet.</p>
          ) : (
            events.map((event) => (
              <p key={event.id} className="wb-log-line">
                <span className="wb-log-time">
                  {new Date(event.at).toLocaleTimeString()}
                </span>{' '}
                <span className="wb-log-stage">{event.stage}</span>{' '}
                <span className="wb-log-outcome">{event.outcome}</span>{' '}
                <span className="wb-log-detail">{event.detail}</span>
              </p>
            ))
          )}
        </div>
      )}
    </div>
  );
}

function mapLogEvent(parsed: { cursor: number; type: string; timestamp: string; detail: string }): EventActivityEvent | null {
  const { type, detail, cursor, timestamp } = parsed;

  switch (type) {
    case 'queue_state_changed': {
      const idMatch = detail.match(/id=([^\s]+)/);
      const prevMatch = detail.match(/previous=([^\s]+)/);
      const currentMatch = detail.match(/current=([^\s]+)/);
      return {
        id: `queue-${cursor}`,
        at: timestamp,
        stage: 'queue',
        subject: idMatch?.[1] ?? 'queue-entry',
        outcome: currentMatch?.[1] ?? 'changed',
        detail: `Queue state: ${prevMatch?.[1] ?? '?'} → ${currentMatch?.[1] ?? '?'}`,
      };
    }
    case 'approval_raised': {
      const idMatch = detail.match(/id=([^\s]+)/);
      const actorMatch = detail.match(/actor=([^\s]+)/);
      const opMatch = detail.match(/op=([^\s]+)/);
      return {
        id: `approval-${cursor}`,
        at: timestamp,
        stage: 'approval',
        subject: idMatch?.[1] ?? 'approval',
        outcome: 'raised',
        detail: `${actorMatch?.[1] ?? '?'} requested ${opMatch?.[1] ?? '?'} (proposal: ${idMatch?.[1] ?? '?'})`,
      };
    }
    case 'turn_appended': {
      const sessionMatch = detail.match(/session=([^\s]+)/);
      const countMatch = detail.match(/count=([^\s]+)/);
      return {
        id: `turn-${cursor}`,
        at: timestamp,
        stage: 'conversation',
        subject: sessionMatch?.[1] ?? 'session',
        outcome: 'turn',
        detail: `Turn ${countMatch?.[1] ?? '?'} appended`,
      };
    }
    case 'mcp_judged': {
      try {
        const parsed = JSON.parse(detail);
        return {
          id: `mcp-${cursor}`,
          at: timestamp,
          stage: 'tool',
          subject: `mcp:${parsed.proposal ?? 'unknown'}`,
          outcome: parsed.outcome ?? 'judged',
          detail: `Judge: ${parsed.judge ?? '?'}. Reason: ${parsed.reason ?? '?'}`,
        };
      } catch {
        return null;
      }
    }
    case 'computer_use': {
      try {
        const parsed = JSON.parse(detail);
        return {
          id: `computer-${cursor}`,
          at: timestamp,
          stage: 'tool',
          subject: `computer:${parsed.action ?? 'unknown'}`,
          outcome: parsed.status ?? 'pending',
          detail: `Target: ${parsed.target ?? '?'}${parsed.result ? ` → ${parsed.result}` : ''}`,
        };
      } catch {
        return null;
      }
    }
    default:
      return null;
  }
}
