'use client';

import React, { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { restoreSession, type RecoveryReport } from '@/lib/session/restore';

export interface SessionState {
  activeProjectId: string | null;
  openTabs: string[];
  viewportState: Record<string, any>;
  simpleModeEnabled: boolean;
  /** §2.10: Developer Tools are hidden until the operator opts in. */
  developerToolsEnabled: boolean;
  /**
   * §5.0 disclosure level, 1 Simple to 4 Internal.
   *
   * Persisted because §8.0 requires a screen to preserve state until
   * intentionally reset; a level that resets on reload is a preference the
   * operator has to re-state every visit.
   */
  informationLevel: 1 | 2 | 3 | 4;
  lastActivityTime: number;
}

interface SessionStateContextType {
  session: SessionState;
  /** What the last restore actually recovered. Null until the load runs. */
  recovery: RecoveryReport | null;
  /** Dismisses the recovery ribbon once the operator has seen it. */
  acknowledgeRecovery: () => void;
  setActiveProject: (projectId: string | null) => void;
  addTab: (projectId: string) => void;
  removeTab: (projectId: string) => void;
  setViewportState: (projectId: string, state: any) => void;
  setSimpleMode: (enabled: boolean) => void;
  setDeveloperTools: (enabled: boolean) => void;
  setInformationLevel: (level: 1 | 2 | 3 | 4) => void;
  clearSession: () => void;
}

const SessionStateContext = createContext<SessionStateContextType | undefined>(undefined);

const DEFAULT_SESSION: SessionState = {
  activeProjectId: null,
  openTabs: [],
  viewportState: {},
  simpleModeEnabled: true,
  developerToolsEnabled: false,
  informationLevel: 2,
  lastActivityTime: Date.now(),
};

const STORAGE_KEY = 'atropos-session-state';

export function SessionStateProvider({ children }: { children: React.ReactNode }) {
  const [session, setSession] = useState<SessionState>(DEFAULT_SESSION);
  const [isLoaded, setIsLoaded] = useState(false);
  const [recovery, setRecovery] = useState<RecoveryReport | null>(null);

  // Load from localStorage on mount
  useEffect(() => {
    const stored = (() => {
      try {
        return localStorage.getItem(STORAGE_KEY);
      } catch {
        return undefined;
      }
    })();

    const result = restoreSession(stored, DEFAULT_SESSION);
    // localStorage is exactly the "external system" an effect exists to
    // synchronize with, and the read cannot move into a lazy initializer: the
    // server renders DEFAULT_SESSION, so restoring during first client render
    // would change the markup React is hydrating.
    // HOE-A09 requires a recovery report and HOE-E06 forbids a silent resume.
    // This previously logged the failure to the console and restored defaults,
    // so an operator whose tabs and disclosure level had just been discarded
    // saw a clean-looking empty workspace and no reason for it.
    // Only apply a restore that actually recovered something. Writing the
    // defaults back unconditionally would overwrite state a child set during
    // mount — child effects run before this one — so a fresh workspace would
    // silently discard the first thing the operator's UI did.
    if (result.report.restored) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- one-shot hydration-safe restore from localStorage
      setSession(result.session);
    }
    // eslint-disable-next-line react-hooks/set-state-in-effect -- reports what that restore actually recovered
    setRecovery(result.report);
    // eslint-disable-next-line react-hooks/set-state-in-effect -- gates the persist effect until the restore has run
    setIsLoaded(true);
  }, []);

  // Persist to localStorage whenever session changes
  useEffect(() => {
    if (!isLoaded) return;
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    } catch (error) {
      // A failed write means the next reload silently loses this session, so
      // it is reported on the same channel as a failed read.
      setRecovery({
        restored: false,
        dropped: 0,
        clean: false,
        message: `Session could not be saved: ${
          error instanceof Error ? error.message : 'storage unavailable'
        }. Changes will not survive a reload.`,
      });
    }
  }, [session, isLoaded]);

  const setActiveProject = useCallback((projectId: string | null) => {
    setSession((prev) => ({
      ...prev,
      activeProjectId: projectId,
      lastActivityTime: Date.now(),
    }));
  }, []);

  const addTab = useCallback((projectId: string) => {
    setSession((prev) => ({
      ...prev,
      openTabs: prev.openTabs.includes(projectId) ? prev.openTabs : [...prev.openTabs, projectId],
      activeProjectId: projectId,
      lastActivityTime: Date.now(),
    }));
  }, []);

  const removeTab = useCallback((projectId: string) => {
    setSession((prev) => {
      const newTabs = prev.openTabs.filter((id) => id !== projectId);
      return {
        ...prev,
        openTabs: newTabs,
        activeProjectId:
          prev.activeProjectId === projectId ? newTabs[newTabs.length - 1] || null : prev.activeProjectId,
        lastActivityTime: Date.now(),
      };
    });
  }, []);

  const setViewportState = useCallback((projectId: string, state: any) => {
    setSession((prev) => ({
      ...prev,
      viewportState: {
        ...prev.viewportState,
        [projectId]: state,
      },
      lastActivityTime: Date.now(),
    }));
  }, []);

  const setSimpleMode = useCallback((enabled: boolean) => {
    setSession((prev) => ({
      ...prev,
      simpleModeEnabled: enabled,
      lastActivityTime: Date.now(),
    }));
  }, []);

  const setDeveloperTools = useCallback((enabled: boolean) => {
    setSession((prev) => ({
      ...prev,
      developerToolsEnabled: enabled,
      lastActivityTime: Date.now(),
    }));
  }, []);

  const setInformationLevel = useCallback((level: 1 | 2 | 3 | 4) => {
    setSession((prev) => ({
      ...prev,
      informationLevel: level,
      lastActivityTime: Date.now(),
    }));
  }, []);

  const acknowledgeRecovery = useCallback(() => setRecovery(null), []);

  const clearSession = useCallback(() => {
    setSession(DEFAULT_SESSION);
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch (error) {
      console.error('Failed to clear session state:', error);
    }
  }, []);

  return (
    <SessionStateContext.Provider
      value={{
        session,
        recovery,
        acknowledgeRecovery,
        setActiveProject,
        setDeveloperTools,
        setInformationLevel,
        addTab,
        removeTab,
        setViewportState,
        setSimpleMode,
        clearSession,
      }}
    >
      {children}
    </SessionStateContext.Provider>
  );
}

/**
 * Session state when a provider is present, `undefined` otherwise.
 *
 * Navigation needs one preference (whether Developer Tools are revealed) and
 * must still render without a provider — in isolation, in tests, and on any
 * surface mounted outside the app tree. Those callers take the safe default
 * rather than crashing the chrome.
 */
export function useOptionalSessionState() {
  return useContext(SessionStateContext);
}

export function useSessionState() {
  const context = useContext(SessionStateContext);
  if (!context) {
    throw new Error('useSessionState must be used within SessionStateProvider');
  }
  return context;
}
