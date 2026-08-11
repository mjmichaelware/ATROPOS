'use client';

import React, { createContext, useContext, useState, useCallback } from 'react';
import { Notification, AppError } from '../api-atropos/types';

interface AppContextType {
  notifications: Notification[];
  errors: AppError[];
  addNotification: (notification: Omit<Notification, 'id' | 'timestamp' | 'read'>) => void;
  removeNotification: (id: string) => void;
  addError: (error: Omit<AppError, 'id' | 'timestamp'>) => void;
  removeError: (id: string) => void;
  clearAll: () => void;
}

const AppContext = createContext<AppContextType | undefined>(undefined);

export function AppProvider({ children }: { children: React.ReactNode }) {
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [errors, setErrors] = useState<AppError[]>([]);

  const addNotification = useCallback(
    (notification: Omit<Notification, 'id' | 'timestamp' | 'read'>) => {
      const id = `notif-${Date.now()}-${Math.random()}`;
      const newNotification: Notification = {
        ...notification,
        id,
        timestamp: new Date().toISOString(),
        read: false,
      };
      setNotifications((prev) => [newNotification, ...prev]);

      // Auto-dismiss info notifications after 5 seconds
      if (notification.type === 'information') {
        setTimeout(() => {
          removeNotification(id);
        }, 5000);
      }
    },
    []
  );

  const removeNotification = useCallback((id: string) => {
    setNotifications((prev) => prev.filter((n) => n.id !== id));
  }, []);

  const addError = useCallback((error: Omit<AppError, 'id' | 'timestamp'>) => {
    const id = `err-${Date.now()}-${Math.random()}`;
    const newError: AppError = {
      ...error,
      id,
      timestamp: new Date().toISOString(),
    };
    setErrors((prev) => [newError, ...prev]);
  }, []);

  const removeError = useCallback((id: string) => {
    setErrors((prev) => prev.filter((e) => e.id !== id));
  }, []);

  const clearAll = useCallback(() => {
    setNotifications([]);
    setErrors([]);
  }, []);

  return (
    <AppContext.Provider
      value={{
        notifications,
        errors,
        addNotification,
        removeNotification,
        addError,
        removeError,
        clearAll,
      }}
    >
      {children}
    </AppContext.Provider>
  );
}

export function useAppContext() {
  const context = useContext(AppContext);
  if (!context) {
    throw new Error('useAppContext must be used within AppProvider');
  }
  return context;
}
