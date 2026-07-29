'use client';

import React, { createContext, useCallback, useContext, useEffect, useState } from 'react';

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

  // Load from localStorage on mount
  useEffect(() => {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored) {
        const parsed = JSON.parse(stored) as Partial<SessionState>;
        // Stored state written by an earlier build lacks fields added since;
        // spreading it over the defaults keeps those defined.
        setSession({ ...DEFAULT_SESSION, ...parsed });
      }
    } catch (error) {
      console.error('Failed to load session state:', error);
    }
    setIsLoaded(true);
  }, []);

  // Persist to localStorage whenever session changes
  useEffect(() => {
    if (isLoaded) {
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
      } catch (error) {
        console.error('Failed to save session state:', error);
      }
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
