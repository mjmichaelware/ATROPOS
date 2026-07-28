'use client';

import { useAppContext } from '@/lib/contexts/app-context';
import { AlertCircle, CheckCircle2, Info, AlertTriangle, Eye, Lightbulb, X } from 'lucide-react';
import { useEffect, useState } from 'react';

const notificationIcons = {
  information: Info,
  suggestion: Lightbulb,
  approval: Eye,
  warning: AlertTriangle,
  failure: AlertCircle,
  completion: CheckCircle2,
};

const notificationColors = {
  information: 'bg-sg-blue-50 dark:bg-sg-blue-900/20 border-sg-blue-200 dark:border-sg-blue-800 text-sg-blue-900 dark:text-sg-blue-100',
  suggestion: 'bg-sg-amber-50 dark:bg-sg-amber-900/20 border-sg-amber-200 dark:border-sg-amber-800 text-sg-amber-900 dark:text-sg-amber-100',
  approval:
    'bg-sg-purple-50 dark:bg-sg-purple-900/20 border-sg-purple-200 dark:border-sg-purple-800 text-sg-purple-900 dark:text-sg-purple-100',
  warning:
    'bg-sg-amber-50 dark:bg-sg-amber-900/20 border-sg-amber-200 dark:border-sg-amber-800 text-sg-amber-900 dark:text-sg-amber-100',
  failure: 'bg-sg-red-50 dark:bg-sg-red-900/20 border-sg-red-200 dark:border-sg-red-800 text-sg-red-900 dark:text-sg-red-100',
  completion:
    'bg-sg-green-50 dark:bg-sg-green-900/20 border-sg-green-200 dark:border-sg-green-800 text-sg-green-900 dark:text-sg-green-100',
};

export function NotificationDisplay() {
  const { notifications, errors, removeNotification, removeError } = useAppContext();
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  if (!mounted) return null;

  return (
    <div className="fixed bottom-4 right-4 z-50 space-y-2 max-w-md">
      {/* Notifications */}
      {notifications.map((notification) => {
        const IconComponent = notificationIcons[notification.type];
        const colorClass = notificationColors[notification.type];

        return (
          <div
            key={notification.id}
            className={`flex items-start gap-3 p-4 rounded-lg border animate-in slide-in-from-bottom-4 ${colorClass}`}
          >
            <IconComponent className="w-5 h-5 flex-shrink-0 mt-0.5" />
            <div className="flex-1 min-w-0">
              <p className="font-semibold text-sm">{notification.title}</p>
              <p className="text-sm opacity-90 mt-0.5">{notification.message}</p>
              {notification.action_url && notification.action_label && (
                <button
                  onClick={() => {
                    window.location.href = notification.action_url!;
                  }}
                  className="text-xs font-semibold mt-2 underline hover:opacity-75 transition-opacity"
                >
                  {notification.action_label}
                </button>
              )}
            </div>
            <button
              onClick={() => removeNotification(notification.id)}
              className="flex-shrink-0 opacity-50 hover:opacity-100 transition-opacity"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        );
      })}

      {/* Errors */}
      {errors.map((error) => (
        <div
          key={error.id}
          className={`flex items-start gap-3 p-4 rounded-lg border animate-in slide-in-from-bottom-4 bg-sg-red-50 dark:bg-sg-red-900/20 border-sg-red-200 dark:border-sg-red-800 text-sg-red-900 dark:text-sg-red-100`}
        >
          <AlertCircle className="w-5 h-5 flex-shrink-0 mt-0.5" />
          <div className="flex-1 min-w-0">
            <p className="font-semibold text-sm">{error.message}</p>
            {error.context && <p className="text-xs opacity-75 mt-0.5">{error.context}</p>}
            {error.suggested_repair && (
              <div className="mt-2 space-y-1">
                <p className="text-xs font-semibold">Suggested repair:</p>
                <p className="text-xs">{error.suggested_repair}</p>
              </div>
            )}
            {error.technical_details && (
              <details className="mt-2">
                <summary className="text-xs font-semibold cursor-pointer">Details</summary>
                <pre className="text-xs mt-1 p-2 bg-black/10 dark:bg-white/10 rounded overflow-auto max-h-32">
                  {error.technical_details}
                </pre>
              </details>
            )}
          </div>
          <button
            onClick={() => removeError(error.id)}
            className="flex-shrink-0 opacity-50 hover:opacity-100 transition-opacity"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      ))}
    </div>
  );
}
