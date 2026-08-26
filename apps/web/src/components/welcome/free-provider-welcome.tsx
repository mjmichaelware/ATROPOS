/* SPDX-License-Identifier: AGPL-3.0-only */

import { useEffect, useState } from 'react';
import { quota, type QuotaPayload, type QuotaProvider } from '@/lib/quota/client';

const SEEN_STORAGE_KEY = 'atropos.free-provider-welcome.seen';

interface FreeProviderWelcomePayload {
  show: boolean;
  reason: string;
}

/**
 * ADD-W-009: Free-provider first-boot welcome.
 *
 * Shows a welcome branch when zero paid providers are configured/healthy.
 * The branch is hash-stable copy (same copy every time) and only appears
 * when zero paid providers are healthy/configured.
 *
 * The engine's quota payload tells us which providers are paid and healthy.
 * If zero paid providers are healthy, we show the welcome branch.
 */
export function FreeProviderWelcome({ onDismiss }: { onDismiss?: () => void }) {
  const [payload, setPayload] = useState<FreeProviderWelcomePayload>({ show: false, reason: '' });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        // Check if already seen this exact welcome content
        const contentId = 'free-provider-welcome-v1';
        if (typeof window !== 'undefined') {
          const seen = localStorage.getItem('atropos.free-provider-welcome.seen') === contentId;
          if (seen) {
            if (!cancelled) setPayload({ show: false, reason: 'already-seen' });
            setLoading(false);
            return;
          }
        }

        const result = await quota.read();
        if (cancelled) return;

        if (!result.ok) {
          if (!cancelled) {
            setPayload({ show: false, reason: `quota-read-failed: ${result.detail} ${result.remedy}` });
            setLoading(false);
          }
          return;
        }

        const payloadData = result.data;

        // Check for zero paid healthy providers
        // Paid providers have costMode !== 'free' && costMode !== 'local'
        // Healthy means state === 'ready'
        const paidHealthy = payloadData.providers.filter((p) => {
          const isPaid = p.costMode !== 'free' && p.costMode !== 'local';
          const isHealthy = p.state === 'ready';
          return isPaid && isHealthy;
        });

        const hasPaidHealthy = paidHealthy.length > 0;

        if (!cancelled) {
          if (!hasPaidHealthy) {
            // No paid healthy providers → show welcome
            setPayload({ show: true, reason: 'zero-paid-healthy' });
          } else {
            setPayload({ show: false, reason: 'paid-healthy-exists' });
          }
        }
      } catch (error) {
        if (!cancelled) {
          setPayload({ show: false, reason: `error: ${String(error)}` });
        }
      } finally {
        if (!cancelled) setLoading(false);
      }

      return () => { cancelled = true; };
    })();
  }, []);

  if (loading) return null;
  if (!payload.show) return null;

  const contentId = 'free-provider-welcome-v1';

  const handleDismiss = () => {
    if (typeof window !== 'undefined') {
      localStorage.setItem('atropos.free-provider-welcome.seen', contentId);
    }
    onDismiss?.();
  };

  return (
    <div
      className="mx-4 mt-4 flex items-start gap-3 rounded-lg border border-sg-amber-200 bg-sg-amber-50 p-4 dark:border-sg-amber-800 dark:bg-sg-amber-900/10"
      role="status"
      data-testid="free-provider-welcome"
    >
      <div className="flex-1">
        <h3 className="font-semibold text-sg-amber-900 dark:text-sg-amber-100">
          No paid providers configured
        </h3>
        <p className="mt-1 text-sm text-sg-amber-800 dark:text-sg-amber-200">
          ATROPOS can run entirely on free providers. No paid keys are currently
          configured or healthy. You can:
        </p>
        <ul className="mt-2 space-y-1 text-sm text-sg-amber-800 dark:text-sg-amber-200">
          <li>• Add a free provider key in <kbd className="px-1.5 py-0.5 bg-sg-amber-100 dark:bg-sg-amber-900/30 rounded text-xs">/providers connect</kbd></li>
          <li>• Use the free-first cascade (local → free → paid) automatically</li>
          <li>• Continue without paid providers — free tier handles most tasks</li>
        </ul>
        <p className="mt-2 text-xs text-sg-amber-700 dark:text-sg-amber-300">
          This notice appears once per welcome version. Dismiss to hide until the next version.
        </p>
      </div>
      <button
        type="button"
        onClick={handleDismiss}
        aria-label="Dismiss free provider welcome"
        className="flex-shrink-0 text-sg-amber-400 hover:text-sg-amber-600"
      >
        <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
        </svg>
      </button>
    </div>
  );
}

function isContentSeen(contentId: string): boolean {
  if (typeof window === 'undefined') return false;
  try {
    return localStorage.getItem('atropos.free-provider-welcome.seen') === contentId;
  } catch {
    return false;
  }
}