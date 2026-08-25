/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * Client-side owner of the layout-theme choice (F-WEB-003 / F-WEB-012).
 *
 * The shell must not flash the wrong layout on refresh, so the initial value
 * is read synchronously from storage at mount and applied to <html> as a data
 * attribute; every later change goes through [setLayout], which persists and
 * applies together so the two can never disagree.
 */
'use client';

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from 'react';
import {
  applyLayoutTheme,
  DEFAULT_LAYOUT,
  readLayoutTheme,
  toggleLayout,
  writeLayoutTheme,
  type LayoutTheme,
} from '@/lib/layout/storage';

interface LayoutThemeContextValue {
  readonly layout: LayoutTheme;
  setLayout: (layout: LayoutTheme) => void;
  toggle: () => void;
}

const LayoutThemeContext = createContext<LayoutThemeContextValue | null>(null);

export function LayoutThemeProvider({ children }: { children: ReactNode }) {
  // Lazy init reads localStorage during first client render — before paint —
  // which is what prevents the default-layout flash on a workbench user's
  // refresh.
  const [layout, setLayoutState] = useState<LayoutTheme>(() => {
    if (typeof window === 'undefined') return DEFAULT_LAYOUT;
    return readLayoutTheme(window.localStorage);
  });

  useEffect(() => {
    applyLayoutTheme(document.documentElement, layout);
  }, [layout]);

  const setLayout = useCallback((next: LayoutTheme) => {
    writeLayoutTheme(window.localStorage, next);
    setLayoutState(next);
  }, []);

  const toggle = useCallback(() => {
    setLayoutState((current) => {
      const next = toggleLayout(current);
      writeLayoutTheme(window.localStorage, next);
      return next;
    });
  }, []);

  return (
    <LayoutThemeContext.Provider value={{ layout, setLayout, toggle }}>
      {children}
    </LayoutThemeContext.Provider>
  );
}

export function useLayoutTheme(): LayoutThemeContextValue {
  const value = useContext(LayoutThemeContext);
  if (value == null) {
    throw new Error('useLayoutTheme requires LayoutThemeProvider');
  }
  return value;
}
