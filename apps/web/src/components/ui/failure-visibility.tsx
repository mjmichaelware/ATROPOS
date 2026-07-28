import { AlertTriangle, Info, TrendingDown, Lightbulb } from 'lucide-react';

export interface FailureInfo {
  title: string;
  reason: string;
  nextAction: string;
  evidence?: {
    link: string;
    label: string;
  };
  recoveryOptions?: string[];
  severity?: 'error' | 'warning' | 'info';
}

export interface FailureVisibilityProps {
  failures: FailureInfo[];
  onAction?: (recovery: string) => void;
  className?: string;
}

/**
 * FailureVisibility component for ATROPOS HOE.
 * Shows why things failed, what to do next, and evidence for debugging.
 *
 * Implements Section E of Source Document 4:
 * "Failure must be visible, intelligible, and actionable."
 */
export function FailureVisibility({ failures, onAction, className = '' }: FailureVisibilityProps) {
  if (failures.length === 0) {
    return null;
  }

  return (
    <div className={`space-y-3 ${className}`}>
      {failures.map((failure, idx) => (
        <div
          key={idx}
          className={`p-4 border rounded-lg space-y-3 ${
            failure.severity === 'warning'
              ? 'bg-sg-amber-50 dark:bg-sg-amber-900/10 border-sg-amber-200 dark:border-sg-amber-800'
              : failure.severity === 'info'
                ? 'bg-sg-blue-50 dark:bg-sg-blue-900/10 border-sg-blue-200 dark:border-sg-blue-800'
                : 'bg-sg-red-50 dark:bg-sg-red-900/10 border-sg-red-200 dark:border-sg-red-800'
          }`}
        >
          {/* Header */}
          <div className="flex items-start gap-3">
            {failure.severity === 'warning' && (
              <AlertTriangle className="w-5 h-5 text-sg-amber-600 mt-0.5 flex-shrink-0" />
            )}
            {failure.severity === 'info' && (
              <Info className="w-5 h-5 text-sg-blue-600 mt-0.5 flex-shrink-0" />
            )}
            {(!failure.severity || failure.severity === 'error') && (
              <AlertTriangle className="w-5 h-5 text-sg-red-600 mt-0.5 flex-shrink-0" />
            )}
            <div className="flex-1">
              <h3 className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
                {failure.title}
              </h3>
            </div>
          </div>

          {/* Reason */}
          <div className="space-y-1">
            <div className="flex items-center gap-2 text-xs font-semibold text-sg-neutral-600 dark:text-sg-neutral-400">
              <TrendingDown className="w-4 h-4" />
              Reason
            </div>
            <p className="text-sm text-sg-neutral-700 dark:text-sg-neutral-300 ml-6">
              {failure.reason}
            </p>
          </div>

          {/* Next Action */}
          <div className="space-y-2">
            <div className="flex items-center gap-2 text-xs font-semibold text-sg-neutral-600 dark:text-sg-neutral-400">
              <Lightbulb className="w-4 h-4" />
              Next Action
            </div>
            <p className="text-sm text-sg-neutral-700 dark:text-sg-neutral-300 ml-6">
              {failure.nextAction}
            </p>
          </div>

          {/* Recovery Options */}
          {failure.recoveryOptions && failure.recoveryOptions.length > 0 && (
            <div className="space-y-2">
              <p className="text-xs font-semibold text-sg-neutral-600 dark:text-sg-neutral-400">
                Recovery Options
              </p>
              <div className="flex flex-wrap gap-2 ml-6">
                {failure.recoveryOptions.map((option, optIdx) => (
                  <button
                    key={optIdx}
                    onClick={() => onAction?.(option)}
                    className={`text-sm px-3 py-1 rounded font-medium transition-colors ${
                      failure.severity === 'warning'
                        ? 'bg-sg-amber-200 dark:bg-sg-amber-800 text-sg-amber-900 dark:text-sg-amber-50 hover:bg-sg-amber-300 dark:hover:bg-sg-amber-700'
                        : failure.severity === 'info'
                          ? 'bg-sg-blue-200 dark:bg-sg-blue-800 text-sg-blue-900 dark:text-sg-blue-50 hover:bg-sg-blue-300 dark:hover:bg-sg-blue-700'
                          : 'bg-sg-red-200 dark:bg-sg-red-800 text-sg-red-900 dark:text-sg-red-50 hover:bg-sg-red-300 dark:hover:bg-sg-red-700'
                    }`}
                  >
                    {option}
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* Evidence Link */}
          {failure.evidence && (
            <div className="pt-2 border-t border-current border-opacity-10">
              <a
                href={failure.evidence.link}
                className="text-xs text-sg-blue-600 dark:text-sg-blue-400 hover:underline"
              >
                {failure.evidence.label}
              </a>
            </div>
          )}
        </div>
      ))}
    </div>
  );
}
