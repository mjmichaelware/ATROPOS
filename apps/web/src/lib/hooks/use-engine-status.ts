'use client';

import { useCallback, useEffect, useState } from 'react';
import type { EngineStatus } from '@/app/api/atropos/status/route';

interface EngineStatusState {
  status: EngineStatus | null;
  loading: boolean;
  /** Set when the status route itself could not be reached. */
  error: string | null;
  refresh: () => void;
}

/**
 * Reports whether the ATROPOS engine is reachable.
 *
 * Deliberately has no optimistic default: until the probe answers, the status
 * is `null` and callers render a checking state. Assuming "online" and
 * discovering otherwise through empty pages is the failure mode §4.1 forbids.
 */
export function useEngineStatus(): EngineStatusState {
  const [status, setStatus] = useState<EngineStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [nonce, setNonce] = useState(0);

  const refresh = useCallback(() => setNonce((value) => value + 1), []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    fetch('/api/atropos/status')
      .then(async (response) => {
        if (!response.ok) {
          throw new Error(`status route returned ${response.status}`);
        }
        return (await response.json()) as EngineStatus;
      })
      .then((value) => {
        if (cancelled) return;
        setStatus(value);
        setLoading(false);
      })
      .catch((cause: Error) => {
        if (cancelled) return;
        setStatus(null);
        setError(cause.message);
        setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [nonce]);

  return { status, loading, error, refresh };
}
