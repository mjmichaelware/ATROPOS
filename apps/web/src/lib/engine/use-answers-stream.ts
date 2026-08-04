/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import { useEffect, useRef, useState } from 'react';
import { engine, engineBaseUrl, type EngineAnswersPayload, type EngineFailure } from './client';

/**
 * Subscribes to the engine's pushed answers.
 *
 * `HOE-C05` requires the web surface to consume the engine's event stream
 * rather than poll it, and Source Doc 4 calls the six answers *continuous*. A
 * poll shows a snapshot whose age the operator cannot see; a stream carries the
 * answer at the moment the engine computed it.
 *
 * Falls back to a single fetch when `EventSource` is unavailable — during SSR,
 * and in the jsdom environment the component tests run in. The fallback is a
 * degraded mode, not a silent equivalent: `streaming` reports which one is
 * actually in use so a surface can say so rather than implying live data it is
 * not receiving.
 */
export interface AnswersStreamState {
  payload: EngineAnswersPayload | null;
  failure: EngineFailure | null;
  loading: boolean;
  /** True only while frames are arriving over an open stream. */
  streaming: boolean;
}

export function useAnswersStream(): AnswersStreamState {
  const [state, setState] = useState<AnswersStreamState>({
    payload: null,
    failure: null,
    loading: true,
    streaming: false,
  });
  const sourceRef = useRef<EventSource | null>(null);

  useEffect(() => {
    let cancelled = false;

    // No EventSource: read once so the surface still shows true state.
    if (typeof EventSource === 'undefined') {
      void (async () => {
        const result = await engine.answers();
        if (cancelled) return;
        setState({
          payload: result.ok ? result.data : null,
          failure: result.ok ? null : result,
          loading: false,
          streaming: false,
        });
      })();
      return () => {
        cancelled = true;
      };
    }

    const source = new EventSource(`${engineBaseUrl()}/v1/answers/stream`);
    sourceRef.current = source;

    source.addEventListener('answers', (event) => {
      if (cancelled) return;
      try {
        const payload = JSON.parse((event as MessageEvent).data) as EngineAnswersPayload;
        setState({ payload, failure: null, loading: false, streaming: true });
      } catch {
        // A frame that will not parse is a fault, not an absence of news: the
        // previous payload is kept but the surface stops claiming to be live.
        setState((prev) => ({ ...prev, loading: false, streaming: false }));
      }
    });

    source.onerror = () => {
      if (cancelled) return;
      setState((prev) => ({
        ...prev,
        loading: false,
        streaming: false,
        // Keep the last payload rather than blanking the cockpit, but say the
        // stream is gone so nothing on screen reads as current.
        failure: prev.payload
          ? prev.failure
          : {
              ok: false,
              reason: 'bridge-unreachable',
              detail: 'The engine answer stream could not be opened.',
              remedy: 'Start the engine with ATROPOS_BRIDGE_PORT set (for example 4317).',
            },
      }));
    };

    return () => {
      cancelled = true;
      source.close();
      sourceRef.current = null;
    };
  }, []);

  return state;
}
