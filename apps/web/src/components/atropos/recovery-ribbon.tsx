'use client';

import { useEffect, useState } from 'react';
import { AlertTriangle, RotateCcw, X } from 'lucide-react';
import type { RecoveryReport } from '@/app/api/atropos/recovery/route';

/**
 * §11.2: "The user always knows what was restored and what requires attention."
 *
 * Three distinct outcomes, none of them collapsed into another:
 *  - recovery repaired something  -> say what, and stay until dismissed
 *  - recovery could not run       -> a fault, shown as a fault
 *  - nothing needed repair        -> silence, because a clean start is not news
 *
 * Unavailable is not silence: if the engine could not be asked, the operator
 * is told that recovery state is unknown rather than being left to assume a
 * clean restore.
 */
export function RecoveryRibbon() {
  const [report, setReport] = useState<RecoveryReport | null>(null);
  const [dismissed, setDismissed] = useState(false);

  useEffect(() => {
    let cancelled = false;

    fetch('/api/atropos/recovery')
      .then(async (response) => {
        if (!response.ok) throw new Error(`recovery route returned ${response.status}`);
        return (await response.json()) as RecoveryReport;
      })
      .then((value) => {
        if (!cancelled) setReport(value);
      })
      .catch(() => {
        // The engine banner already reports unreachable engines; this ribbon
        // stays quiet rather than duplicating that alarm.
        if (!cancelled) setReport(null);
      });

    return () => {
      cancelled = true;
    };
  }, []);

  if (!report || dismissed) return null;

  // A clean start is not worth the operator's attention.
  if (report.available && !report.repaired && !report.failure) return null;

  // The engine could not be asked. The engine banner explains why it is
  // unreachable; this only records that recovery state is therefore unknown.
  if (!report.available) {
    return (
      <div
        className="mx-4 mt-4 flex items-start gap-3 rounded-lg border border-sg-neutral-200 bg-sg-neutral-50 p-3 dark:border-sg-neutral-800 dark:bg-sg-neutral-900"
        role="status"
      >
        <RotateCcw className="mt-0.5 h-4 w-4 flex-shrink-0 text-sg-neutral-500" aria-hidden="true" />
        <div className="flex-1 text-sm">
          <p className="font-medium text-sg-neutral-900 dark:text-sg-neutral-50">
            Recovery state unavailable
          </p>
          <p className="text-sg-neutral-600 dark:text-sg-neutral-400">
            {report.detail ?? 'The engine could not be asked what was restored.'}
          </p>
          {report.remedy && (
            <p className="mt-1 text-sg-neutral-600 dark:text-sg-neutral-400">{report.remedy}</p>
          )}
        </div>
      </div>
    );
  }

  const isFailure = Boolean(report.failure);

  return (
    <div
      className={
        isFailure
          ? 'mx-4 mt-4 flex items-start gap-3 rounded-lg border border-sg-red-200 bg-sg-red-50 p-3 dark:border-sg-red-800 dark:bg-sg-red-900/10'
          : 'mx-4 mt-4 flex items-start gap-3 rounded-lg border border-sg-amber-200 bg-sg-amber-50 p-3 dark:border-sg-amber-800 dark:bg-sg-amber-900/10'
      }
      role={isFailure ? 'alert' : 'status'}
    >
      {isFailure ? (
        <AlertTriangle className="mt-0.5 h-4 w-4 flex-shrink-0 text-sg-red-600" aria-hidden="true" />
      ) : (
        <RotateCcw className="mt-0.5 h-4 w-4 flex-shrink-0 text-sg-amber-600" aria-hidden="true" />
      )}

      <div className="flex-1 text-sm">
        <p className="font-medium text-sg-neutral-900 dark:text-sg-neutral-50">
          {isFailure ? 'Crash recovery did not run' : 'Work was restored on startup'}
        </p>
        {/* The engine's own words. This surface does not paraphrase a report
            it did not produce. */}
        <p className="font-mono text-xs text-sg-neutral-700 dark:text-sg-neutral-300">
          {report.notice}
        </p>
        {report.remedy && (
          <p className="mt-1 text-sg-neutral-600 dark:text-sg-neutral-400">{report.remedy}</p>
        )}
      </div>

      <button
        type="button"
        onClick={() => setDismissed(true)}
        aria-label="Dismiss recovery report"
        className="text-sg-neutral-400 hover:text-sg-neutral-600"
      >
        <X className="h-4 w-4" />
      </button>
    </div>
  );
}
