/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import { useEffect, useState } from 'react';
import { hasSeen, markSeen, welcome as welcomeClient, type WelcomePayload } from '@/lib/welcome/client';

/**
 * The first-boot welcome.
 *
 * `SUP.UX.FREE-PROVIDER-WELCOME`: deterministic, zero-cost after first view,
 * and the free-provider path is first-class rather than an afterthought below a
 * sign-in form.
 *
 * The engine's text is rendered verbatim. The artifact is content-addressed, so
 * a surface that reassembled the prose from parts would be showing something
 * whose hash no longer matches what it claims — and the hash is what decides
 * whether this operator has already read it.
 *
 * Silence when already seen, and silence when the engine cannot be reached: the
 * engine banner already reports an unreachable engine, and a second alarm
 * saying onboarding is unavailable would add nothing an operator could act on.
 */
export function WelcomePanel() {
  const [payload, setPayload] = useState<WelcomePayload | null>(null);
  const [dismissed, setDismissed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const result = await welcomeClient.read();
      if (cancelled || !result.ok) return;
      // Seen-ness is checked against this exact content, so a changed welcome
      // surfaces again rather than being suppressed by an old acknowledgement.
      if (hasSeen(result.data.contentId)) return;
      setPayload(result.data);
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  if (!payload || dismissed) return null;

  return (
    <section
      aria-label="Welcome to ATROPOS"
      className="mx-4 mt-4 space-y-3 rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800"
    >
      {/* The engine's own words, unmodified: the content id addresses this text. */}
      <pre className="overflow-x-auto whitespace-pre-wrap font-mono text-sm text-sg-neutral-800 dark:text-sg-neutral-200">
        {payload.body}
      </pre>
      <button
        type="button"
        onClick={() => {
          markSeen(payload.contentId);
          setDismissed(true);
        }}
        className="rounded-lg bg-sg-neutral-900 px-4 py-2 text-sm font-medium text-white dark:bg-sg-neutral-50 dark:text-sg-neutral-900"
      >
        Got it
      </button>
    </section>
  );
}
