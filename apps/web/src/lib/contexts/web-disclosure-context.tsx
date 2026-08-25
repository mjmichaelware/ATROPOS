/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * ADD-W-004: the web surface's own verbosity channel.
 *
 * `HOE-E04` gives every surface its disclosure state; this provider owns the
 * web's. It composes `surface-channel` — the canonical per-surface store —
 * with a React binding and localStorage persistence, so components read one
 * level and never touch the CLI's or Android's.
 *
 * The scope claim is structural, not commented: nothing in this file imports
 * or addresses another surface's key, and [setLevel] writes only the 'web'
 * channel of the shared store.
 */
'use client';

import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import {
  defaultChannels,
  levelFor,
  restoreChannels,
  setSurfaceLevel,
  type SurfaceChannels,
} from '@/lib/disclosure/surface-channel';
import { coerceLevel, type DisclosureLevel } from '@/lib/disclosure/levels';

const STORAGE_KEY = 'atropos.disclosure.web';

function readStored(): DisclosureLevel {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    // Stored values are strings ("3"); Number() before coercion so a real
    // level survives the round-trip and garbage ("abc", "") falls to default.
    return coerceLevel(raw === null ? undefined : Number(raw));
  } catch {
    return levelFor(defaultChannels(), 'web');
  }
}

interface WebDisclosureValue {
  readonly level: DisclosureLevel;
  setLevel: (level: DisclosureLevel) => void;
}

const WebDisclosureContext = createContext<WebDisclosureValue | null>(null);

export function WebDisclosureProvider({ children }: { children: ReactNode }) {
  // Lazy initializer reads localStorage during the first client render, so
  // the restored level is present before paint and no effect-driven
  // re-render cascade exists. Server renders take the default.
  const [channels, setChannels] = useState<SurfaceChannels>(() => {
    if (typeof window === 'undefined') return defaultChannels();
    return restoreChannels({ web: readStored() });
  });

  const setLevel = useCallback((level: DisclosureLevel) => {
    setChannels((current) => {
      const next = setSurfaceLevel(current, 'web', level);
      try {
        window.localStorage.setItem(STORAGE_KEY, String(next.web));
      } catch {
        // Private mode: the choice lives for this page view only.
      }
      return next;
    });
  }, []);

  const value = useMemo<WebDisclosureValue>(
    () => ({ level: channels.web, setLevel }),
    [channels.web, setLevel],
  );

  return (
    <WebDisclosureContext.Provider value={value}>
      {children}
    </WebDisclosureContext.Provider>
  );
}

export function useWebDisclosure(): WebDisclosureValue {
  const value = useContext(WebDisclosureContext);
  if (value == null) {
    throw new Error('useWebDisclosure requires WebDisclosureProvider');
  }
  return value;
}

/**
 * For components that render inside and outside the provider (pages, drawers).
 * Null means "no web channel mounted" — callers keep their own default rather
 * than crashing chrome over a missing preference.
 */
export function useOptionalWebDisclosure(): WebDisclosureValue | null {
  return useContext(WebDisclosureContext);
}
