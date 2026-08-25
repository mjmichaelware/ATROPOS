/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * Shared owner of the workbench tab store (F-WEB-005).
 *
 * The explorer opens tabs; the center pane renders them. Neither owns the
 * other, so the store lives in one context here and both consume it. The
 * transitions themselves are pure functions in `lib/editor/tabs` — this file
 * only binds them to React.
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
  closeTab,
  EMPTY_STORE,
  markClean,
  openTab,
  setContent,
  type TabStore,
} from '@/lib/editor/tabs';

interface WorkbenchTabsValue {
  readonly store: TabStore;
  /** Opens (or focuses) a tab. Content fills later when a reader exists. */
  open: (path: string) => void;
  /** Focuses an already-open tab without opening a new one. */
  focus: (path: string) => void;
  edit: (path: string, content: string) => void;
  clean: (path: string) => void;
  close: (path: string) => void;
}

const WorkbenchTabsContext = createContext<WorkbenchTabsValue | null>(null);

export function WorkbenchTabsProvider({ children }: { children: ReactNode }) {
  const [store, setStore] = useState<TabStore>(EMPTY_STORE);

  const open = useCallback((path: string) => {
    setStore((current) => openTab(current, path));
  }, []);
  const focus = useCallback((path: string) => {
    // openTab focuses existing paths rather than duplicating, which is
    // exactly the focus semantics; the name differs at call sites so intent
    // reads clearly.
    setStore((current) => openTab(current, path));
  }, []);
  const edit = useCallback((path: string, content: string) => {
    setStore((current) => setContent(current, path, content));
  }, []);
  const clean = useCallback((path: string) => {
    setStore((current) => markClean(current, path));
  }, []);
  const close = useCallback((path: string) => {
    setStore((current) => closeTab(current, path));
  }, []);

  const value = useMemo(
    () => ({ store, open, focus, edit, clean, close }),
    [store, open, focus, edit, clean, close]
  );
  return (
    <WorkbenchTabsContext.Provider value={value}>
      {children}
    </WorkbenchTabsContext.Provider>
  );
}

export function useWorkbenchTabs(): WorkbenchTabsValue {
  const value = useContext(WorkbenchTabsContext);
  if (value == null) {
    throw new Error('useWorkbenchTabs requires WorkbenchTabsProvider');
  }
  return value;
}
