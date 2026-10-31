/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM 16.1: Demonstration Workspace Scaffolding and Visual Onboarding (F-WEB-001).
 *
 * The first-boot welcome.
 * `SUP.UX.FREE-PROVIDER-WELCOME`: deterministic, zero-cost after first view,
 * and the free-provider path is first-class rather than an afterthought below a sign-in form.
 *
 * Renders clear demonstration workspace artifacts on fresh installs before user projects exist,
 * labeled explicitly as sample demonstration data.
 *
 * ## Research: Localized Spotlight Cues for First-Time Developers
 * Researches interactive onboarding walkthrough overlays with localized spotlight cues
 * that guide first-time developers through key workspace panels.
 */
'use client';

import { useEffect, useState } from 'react';
import {
  hasSeen,
  markSeen,
  welcome as welcomeClient,
  type WelcomePayload,
  SEEN_STORAGE_KEY,
} from '@/lib/welcome/client';
import { DemonstrationWorkspaceScaffolder } from './DemonstrationWorkspaceScaffolder';

export { hasSeen, markSeen, welcomeClient, SEEN_STORAGE_KEY };
export type { WelcomePayload };

export function WelcomePanel() {
  const [payload, setPayload] = useState<WelcomePayload | null>(null);
  const [dismissed, setDismissed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const result = await welcomeClient.read();
      if (cancelled || !result.ok) return;
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
      className="mx-4 mt-4 space-y-4 rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800"
    >
      <pre className="overflow-x-auto whitespace-pre-wrap font-mono text-sm text-sg-neutral-800 dark:text-sg-neutral-200">
        {payload.body}
      </pre>

      <DemonstrationWorkspaceScaffolder />

      <div>
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
      </div>
    </section>
  );
}
