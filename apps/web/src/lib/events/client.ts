/* SPDX-License-Identifier: AGPL-3.0-only */

import { engineBaseUrl, type EngineFailure } from '@/lib/engine/client';

/**
 * Event stream client for `/v1/events/stream`.
 *
 * The bridge exposes a server-sent event stream at `/v1/events/stream` that
 * pushes frames for queue state changes, approvals, turns, and other engine
 * events. This client consumes that stream and provides the same ordered
 * ActivityEvent shape the activity monitor expects, mapped from the bridge's
 * event types.
 *
 * Event types from the bridge:
 * - queue_state_changed: queue entry state transitions
 * - approval_raised: new approval waiting on human
 * - turn_appended: conversation turn added
 * - mcp_judged: MCP tool judgment (mapped to tool stage)
 * - computer_use: computer-use action (mapped to tool stage)
 *
 * The client maps these into the ActivityEvent shape used by the monitor.
 */

export interface EventStreamEvent {
  cursor: number;
  type: string;
  timestamp: string;
  detail: string;
}

export interface EventStreamResult {
  ok: true;
  events: EventStreamEvent[];
}

export type EventStreamEventResult =
  | { ok: true; data: EventStreamEvent }
  | ({ ok: false } & Omit<EngineFailure, 'ok'>);

/**
 * Map bridge event types to activity monitor stages and outcomes.
 */
function mapEventToActivity(event: EventStreamEvent): ActivityEvent | null {
  const { type, detail, cursor, timestamp } = event;

  switch (type) {
    case 'queue_state_changed': {
      // detail: "id=<id> previous=<prev> current=<current>"
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
      // detail: "id=<id> proposal=<pid> actor=<actor> op=<op>"
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
      // detail: "session=<id> count=<n>"
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
      // detail: JSON string with id, proposal, judge, outcome, reason
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
      // detail: JSON string with id, action, target, status, result
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

export interface ActivityEvent {
  id: string;
  at: string;
  stage: string;
  subject: string;
  outcome: string;
  detail: string;
}

/**
 * Subscribe to the event stream and collect mapped activity events.
 * Returns an async generator that yields accumulated events.
 */
export async function* subscribeEventStream(
  signal?: AbortSignal,
  sessionId?: string
): AsyncGenerator<ActivityEvent[], void, unknown> {
  const url = new URL(`${engineBaseUrl()}/v1/events/stream`);
  if (sessionId) {
    url.searchParams.set('session', sessionId);
  }

  const response = await fetch(url.toString(), {
    cache: 'no-store',
    headers: { accept: 'text/event-stream' },
    signal,
  });

  if (!response.ok || !response.body) {
    throw new Error(`Event stream failed: ${response.status}`);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  const accumulated: ActivityEvent[] = [];

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      if (signal?.aborted) break;

      buffer += decoder.decode(value, { stream: true });

      const lines = buffer.split('\n');
      buffer = lines.pop() ?? '';

      for (const line of lines) {
        if (!line.startsWith('data: ')) continue;
        try {
          const parsed = JSON.parse(line.slice(6));
          if (parsed.cursor !== undefined && parsed.type && parsed.timestamp) {
            const mapped = mapEventToActivity({
              cursor: parsed.cursor,
              type: parsed.type,
              timestamp: parsed.timestamp,
              detail: parsed.detail,
            });
            if (mapped) {
              accumulated.push(mapped);
            }
          }
        } catch {
          // Ignore malformed frames
        }
      }

      if (accumulated.length > 0) {
        yield [...accumulated];
      }
    }
  } finally {
    reader.releaseLock();
  }
}

/**
 * Polling fallback for environments without SSE support.
 * Returns a single snapshot from /v1/events.
 */
export async function pollEvents(
  sessionId?: string
): Promise<ActivityEvent[]> {
  const url = new URL(`${engineBaseUrl()}/v1/events`);
  if (sessionId) {
    url.searchParams.set('session', sessionId);
  }

  const response = await fetch(url.toString(), { cache: 'no-store' });
  if (!response.ok) return [];

  const body = await response.json();
  const events: EventStreamEvent[] = body.events ?? [];
  return events.map(mapEventToActivity).filter((e): e is ActivityEvent => e !== null);
}

/**
 * Subscribe or poll depending on EventSource availability.
 * Returns an async generator yielding accumulated ActivityEvents.
 */
export async function* subscribeActivity(
  signal?: AbortSignal,
  sessionId?: string
): AsyncGenerator<ActivityEvent[], void, unknown> {
  // Try SSE first
  if (typeof EventSource !== 'undefined') {
    yield* subscribeEventStream(signal, sessionId);
    return;
  }

  // Fallback to polling
  while (!signal?.aborted) {
    const events = await pollEvents(sessionId);
    yield events;
    await new Promise((r) => setTimeout(r, 4000));
  }
}
