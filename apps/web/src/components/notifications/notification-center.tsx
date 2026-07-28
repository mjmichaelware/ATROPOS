'use client';

import { useState, useCallback, useEffect } from 'react';
import { X, AlertCircle, CheckCircle2, Info, AlertTriangle, Lightbulb, Eye } from 'lucide-react';

export type NotificationCategory = 'information' | 'suggestion' | 'approval' | 'warning' | 'failure' | 'completion';

export interface Notification {
  id: string;
  category: NotificationCategory;
  title: string;
  message: string;
  timestamp: number;
  actionLink?: string;
  actionLabel?: string;
  onAction?: () => void;
  dismissed?: boolean;
  projectId?: string;
  evidenceLink?: string;
}

interface NotificationCenterProps {
  maxVisible?: number;
  position?: 'top-right' | 'top-left' | 'bottom-right' | 'bottom-left';
  autoHideDuration?: number;
}

const CATEGORY_CONFIG: Record<NotificationCategory, { icon: any; bgColor: string; color: string }> = {
  information: {
    icon: Info,
    bgColor: 'bg-sg-blue-50 dark:bg-sg-blue-900',
    color: 'text-sg-blue-600 dark:text-sg-blue-400',
  },
  suggestion: {
    icon: Lightbulb,
    bgColor: 'bg-sg-amber-50 dark:bg-sg-amber-900',
    color: 'text-sg-amber-600 dark:text-sg-amber-400',
  },
  approval: {
    icon: Eye,
    bgColor: 'bg-sg-purple-50 dark:bg-sg-purple-900',
    color: 'text-sg-purple-600 dark:text-sg-purple-400',
  },
  warning: {
    icon: AlertTriangle,
    bgColor: 'bg-sg-amber-50 dark:bg-sg-amber-900',
    color: 'text-sg-amber-600 dark:text-sg-amber-400',
  },
  failure: {
    icon: AlertCircle,
    bgColor: 'bg-sg-red-50 dark:bg-sg-red-900',
    color: 'text-sg-red-600 dark:text-sg-red-400',
  },
  completion: {
    icon: CheckCircle2,
    bgColor: 'bg-sg-green-50 dark:bg-sg-green-900',
    color: 'text-sg-green-600 dark:text-sg-green-400',
  },
};

export function useNotifications() {
  const [notifications, setNotifications] = useState<Notification[]>([]);

  const addNotification = useCallback(
    (notification: Omit<Notification, 'id' | 'timestamp'>) => {
      const id = Math.random().toString(36).slice(2);
      const newNotification: Notification = {
        ...notification,
        id,
        timestamp: Date.now(),
      };
      setNotifications((prev) => [newNotification, ...prev]);
      return id;
    },
    []
  );

  const dismissNotification = useCallback((id: string) => {
    setNotifications((prev) => prev.map((n) => (n.id === id ? { ...n, dismissed: true } : n)));
  }, []);

  const clearAllNotifications = useCallback(() => {
    setNotifications([]);
  }, []);

  return {
    notifications: notifications.filter((n) => !n.dismissed),
    addNotification,
    dismissNotification,
    clearAllNotifications,
  };
}

interface NotificationItemProps {
  notification: Notification;
  onDismiss: () => void;
}

function NotificationItem({ notification, onDismiss }: NotificationItemProps) {
  const config = CATEGORY_CONFIG[notification.category];
  const Icon = config.icon;

  return (
    <div
      className={`${config.bgColor} border border-current/20 rounded-lg p-4 shadow-lg max-w-md animate-in slide-in-from-right`}
      role="alert"
    >
      <div className="flex items-start gap-3">
        <Icon className={`w-5 h-5 ${config.color} flex-shrink-0 mt-0.5`} aria-hidden="true" />
        <div className="flex-1 min-w-0">
          <h3 className={`font-semibold ${config.color} text-sm mb-1`}>{notification.title}</h3>
          <p className="text-sm text-sg-neutral-700 dark:text-sg-neutral-300">{notification.message}</p>
          {(notification.actionLink || notification.actionLabel) && (
            <button
              onClick={() => {
                notification.onAction?.();
                onDismiss();
              }}
              className="text-xs font-semibold text-sg-red-600 hover:text-sg-red-700 dark:hover:text-sg-red-500 mt-2 underline"
            >
              {notification.actionLabel || 'View'}
            </button>
          )}
          {notification.evidenceLink && (
            <a
              href={notification.evidenceLink}
              className="text-xs font-semibold text-sg-red-600 hover:text-sg-red-700 dark:hover:text-sg-red-500 mt-2 underline block"
            >
              View evidence
            </a>
          )}
          <time className="text-xs text-sg-neutral-500 dark:text-sg-neutral-500 mt-2 block">
            {new Date(notification.timestamp).toLocaleTimeString()}
          </time>
        </div>
        <button
          onClick={onDismiss}
          className="text-sg-neutral-500 hover:text-sg-neutral-700 dark:hover:text-sg-neutral-300 flex-shrink-0"
          aria-label="Dismiss notification"
        >
          <X className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
}

export function NotificationStack({ notifications, onDismiss }: {
  notifications: Notification[];
  onDismiss: (id: string) => void;
}) {
  return (
    <div className="fixed top-4 right-4 space-y-2 z-50 max-w-md">
      {notifications.slice(0, 5).map((notification) => (
        <NotificationItem
          key={notification.id}
          notification={notification}
          onDismiss={() => onDismiss(notification.id)}
        />
      ))}
    </div>
  );
}
