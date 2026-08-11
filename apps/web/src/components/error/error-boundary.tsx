'use client';

import { useEffect, ReactNode } from 'react';
import { AlertTriangle, RotateCcw, Book } from 'lucide-react';

export interface ErrorInfo {
  message: string;
  stack?: string;
  timestamp: number;
  context?: string;
  suggestedRepair?: string;
  repairAction?: () => void;
  retryable?: boolean;
}

interface ErrorBoundaryProps {
  error: ErrorInfo;
  onReset?: () => void;
  onRetry?: () => void;
  compact?: boolean;
}

export function ErrorDisplay({
  error,
  onReset,
  onRetry,
  compact = false,
}: ErrorBoundaryProps) {
  if (compact) {
    return (
      <div className="inline-flex items-center gap-2 px-3 py-2 bg-sg-red-50 dark:bg-sg-red-900/30 border border-sg-red-200 dark:border-sg-red-800 rounded-lg">
        <AlertTriangle className="w-4 h-4 text-sg-red-600" />
        <span className="text-sm font-semibold text-sg-red-900 dark:text-sg-red-100">
          {error.message}
        </span>
      </div>
    );
  }

  return (
    <div className="bg-sg-red-50 dark:bg-sg-red-900/20 border-l-4 border-sg-red-600 p-6 rounded-lg space-y-4">
      {/* Header */}
      <div className="flex items-start gap-3">
        <AlertTriangle className="w-6 h-6 text-sg-red-600 flex-shrink-0 mt-0.5" />
        <div className="flex-1">
          <h3 className="text-lg font-bold text-sg-red-900 dark:text-sg-red-100">
            Something went wrong
          </h3>
          <p className="text-sm text-sg-red-800 dark:text-sg-red-200 mt-1">{error.message}</p>
        </div>
      </div>

      {/* Details */}
      <div className="space-y-2">
        {error.context && (
          <div>
            <h4 className="text-xs font-semibold text-sg-neutral-700 dark:text-sg-neutral-300 uppercase">
              Context
            </h4>
            <p className="text-sm text-sg-neutral-700 dark:text-sg-neutral-300">{error.context}</p>
          </div>
        )}

        {error.stack && (
          <details className="cursor-pointer">
            <summary className="text-xs font-semibold text-sg-neutral-700 dark:text-sg-neutral-300 uppercase hover:text-sg-red-600">
              Technical Details
            </summary>
            <pre className="text-xs bg-white dark:bg-sg-neutral-900 rounded p-2 mt-2 overflow-x-auto text-sg-neutral-600 dark:text-sg-neutral-400">
              {error.stack}
            </pre>
          </details>
        )}

        {error.timestamp && (
          <p className="text-xs text-sg-neutral-600 dark:text-sg-neutral-400">
            Occurred at {new Date(error.timestamp).toLocaleTimeString()}
          </p>
        )}
      </div>

      {/* Suggested Repair */}
      {error.suggestedRepair && (
        <div className="bg-white dark:bg-sg-neutral-800 rounded p-3 space-y-2">
          <div className="flex items-center gap-2">
            <Book className="w-4 h-4 text-sg-blue-600" />
            <h4 className="text-sm font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
              How to fix this
            </h4>
          </div>
          <p className="text-sm text-sg-neutral-700 dark:text-sg-neutral-300">{error.suggestedRepair}</p>
        </div>
      )}

      {/* Actions */}
      <div className="flex items-center gap-3">
        {error.retryable && onRetry && (
          <button
            onClick={onRetry}
            className="inline-flex items-center gap-2 px-4 py-2 bg-sg-red-600 text-white rounded-lg hover:bg-sg-red-700 transition-colors font-semibold text-sm"
          >
            <RotateCcw className="w-4 h-4" />
            Retry
          </button>
        )}
        {error.repairAction && (
          <button
            onClick={error.repairAction}
            className="inline-flex items-center gap-2 px-4 py-2 border border-sg-red-300 dark:border-sg-red-700 text-sg-red-900 dark:text-sg-red-100 rounded-lg hover:bg-sg-red-100 dark:hover:bg-sg-red-900 transition-colors font-semibold text-sm"
          >
            {error.suggestedRepair ? 'Apply Fix' : 'Action'}
          </button>
        )}
        {onReset && (
          <button
            onClick={onReset}
            className="inline-flex items-center gap-2 px-4 py-2 border border-sg-neutral-300 dark:border-sg-neutral-700 text-sg-neutral-900 dark:text-sg-neutral-100 rounded-lg hover:bg-sg-neutral-100 dark:hover:bg-sg-neutral-900 transition-colors font-semibold text-sm ml-auto"
          >
            Reset
          </button>
        )}
      </div>

      {/* Evidence Link */}
      {error.context && (
        <a
          href="/history"
          className="inline-flex items-center gap-2 text-xs text-sg-red-600 hover:text-sg-red-700 dark:hover:text-sg-red-500 underline"
        >
          View error in history
        </a>
      )}
    </div>
  );
}

export function useErrorHandler() {
  const handleError = (error: Error | string, context?: string): ErrorInfo => {
    const message = typeof error === 'string' ? error : error.message;
    const stack = typeof error === 'string' ? undefined : error.stack;

    const errorInfo: ErrorInfo = {
      message,
      stack,
      timestamp: Date.now(),
      context,
    };

    // Log to console in development
    if (typeof window !== 'undefined' && process.env.NODE_ENV === 'development') {
      console.error('Error boundary caught:', errorInfo);
    }

    return errorInfo;
  };

  return { handleError };
}
