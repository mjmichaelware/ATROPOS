/* SPDX-License-Identifier: AGPL-3.0-only */

import { readEngine, type EngineFailure } from '@/lib/engine/client';

/**
 * Reads the deterministic first-boot welcome.
 *
 * `SUP.UX.FREE-PROVIDER-WELCOME`: onboarding is deterministic and zero-cost
 * after first view, and the free-provider path is first-class. The engine
 * builds and hashes the artifact; this surface only decides whether the
 * operator has seen *this* content.
 *
 * Seen-ness is keyed on the content id, never on a boolean. A flag records that
 * they saw *a* welcome — which stops being true the moment the welcome changes,
 * and the change would then be the one thing they never see.
 */

export interface WelcomePayload {
  ok: true;
  contentId: string;
  body: string;
}

export type WelcomeResult =
  | { ok: true; data: WelcomePayload }
  | ({ ok: false } & Omit<EngineFailure, 'ok'>);

export const welcome = {
  read: (): Promise<WelcomeResult> => readEngine<WelcomePayload>('/v1/welcome'),
};

export const SEEN_STORAGE_KEY = 'atropos.welcome.seen';

/**
 * Whether this exact welcome has been acknowledged.
 *
 * Unreadable storage returns false — showing the welcome again is a small cost,
 * and suppressing it because storage failed would hide onboarding from the
 * operator most likely to need it.
 */
export function hasSeen(contentId: string, storage?: Storage): boolean {
  const store = storage ?? safeStorage();
  if (!store) return false;
  try {
    return store.getItem(SEEN_STORAGE_KEY) === contentId;
  } catch {
    return false;
  }
}

export function markSeen(contentId: string, storage?: Storage): void {
  const store = storage ?? safeStorage();
  if (!store) return;
  try {
    store.setItem(SEEN_STORAGE_KEY, contentId);
  } catch {
    // Zero-cost after first view is a nicety; failing to record it must never
    // take down the surface that was trying to welcome them.
  }
}

function safeStorage(): Storage | null {
  try {
    return typeof window === 'undefined' ? null : window.localStorage;
  } catch {
    return null;
  }
}
