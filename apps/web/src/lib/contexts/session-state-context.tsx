'use client';

import React, { createContext, useContext, useEffect, useState } from 'react';

export interface SessionState {
  activeProjectId: string | null;
  openTabs: string[];
  viewportState: Record<string, any>;
  simpleModeEnabled: boolean;
  lastActivityTime: number;
}

interface SessionStateContextType {
  session: SessionState;
  setActiveProject: (projectId: string | null) => void;
  addTab: (projectId: string) => void;
  removeTab: (projectId: string) => void;
  setViewportState: (projectId: string, state: any) => void;
  setSimpleMode: (enabled: boolean) => void;
  clearSession: () => void;
}

const SessionStateContext = createContext<SessionStateContextType | undefined>(undefined);

const DEFAULT_SESSION: SessionState = {
  activeProjectId: null,
  openTabs: [],
  viewportState: {},
  simpleModeEnabled: true,
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
        const parsed = JSON.parse(stored) as SessionState;
        setSession(parsed);
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

  const setActiveProject = (projectId: string | null) => {
    setSession((prev) => ({
      ...prev,
      activeProjectId: projectId,
      lastActivityTime: Date.now(),
    }));
  };

  const addTab = (projectId: string) => {
    setSession((prev) => ({
      ...prev,
      openTabs: prev.openTabs.includes(projectId) ? prev.openTabs : [...prev.openTabs, projectId],
      activeProjectId: projectId,
      lastActivityTime: Date.now(),
    }));
  };

  const removeTab = (projectId: string) => {
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
  };

  const setViewportState = (projectId: string, state: any) => {
    setSession((prev) => ({
      ...prev,
      viewportState: {
        ...prev.viewportState,
        [projectId]: state,
      },
      lastActivityTime: Date.now(),
    }));
  };

  const setSimpleMode = (enabled: boolean) => {
    setSession((prev) => ({
      ...prev,
      simpleModeEnabled: enabled,
      lastActivityTime: Date.now(),
    }));
  };

  const clearSession = () => {
    setSession(DEFAULT_SESSION);
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch (error) {
      console.error('Failed to clear session state:', error);
    }
  };

  return (
    <SessionStateContext.Provider
      value={{
        session,
        setActiveProject,
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

export function useSessionState() {
  const context = useContext(SessionStateContext);
  if (!context) {
    throw new Error('useSessionState must be used within SessionStateProvider');
  }
  return context;
}
