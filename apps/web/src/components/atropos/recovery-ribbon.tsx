'use client';

import { useEffect, useState } from 'react';
import { AlertTriangle, RotateCcw, X } from 'lucide-react';
import type { RecoveryReport } from '@/app/api/atropos/recovery/route';
import { useOptionalSessionState } from '@/lib/contexts/session-state-context';
import { governance } from '@/lib/governance/client';
import { ribbonLine, type RibbonLine } from '@/lib/recovery/ribbon-line';

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
  const [line, setLine] = useState<RibbonLine | null>(null);
  const [dismissed, setDismissed] = useState(false);
  const sessionState = useOptionalSessionState();
  const sessionRecovery = sessionState?.recovery ?? null;
  const acknowledgeRecovery = sessionState?.acknowledgeRecovery;

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

  // SUP.UX.RECOVERY-RIBBON: continuity, free space and authority in one line.
  // Read separately from the continuity probe above because the three answers
  // come from three owners, and a single combined endpoint would make one
  // unreachable source hide the other two.
  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const [storage, authority] = await Promise.all([
        governance.storage(),
        governance.authority(),
      ]);
      if (cancelled) return;
      setLine(
        ribbonLine({
          continuity: report
            ? { repaired: report.repaired, failed: Boolean(report.failure), notice: report.notice }
            : null,
          // Unreadable is null, not zero: a storage route that did not answer
          // has not told us there is room.
          storageFractionUsed: storage.ok ? storage.data.fractionUsed : null,
          authority: authority.ok
            ? { resolved: authority.data.resolved, source: authority.data.source }
            : null,
        }),
      );
    })();
    return () => {
      cancelled = true;
    };
  }, [report]);

  if (dismissed) return null;

  // The browser's own restore is reported here rather than in a second ribbon.
  // HOE-A09 pairs layout persistence with a recovery report, and two competing
  // recovery surfaces would leave the operator deciding which one to believe.
  // It is shown first because it describes the window they are looking at.
  if (sessionRecovery && !sessionRecovery.clean) {
    return (
      <div
        className="mx-4 mt-4 flex items-start gap-3 rounded-lg border border-sg-amber-200 bg-sg-amber-50 p-3 dark:border-sg-amber-800 dark:bg-sg-amber-900/10"
        role="status"
      >
        <RotateCcw className="mt-0.5 h-4 w-4 flex-shrink-0 text-sg-amber-600" aria-hidden="true" />
        <div className="flex-1 text-sm">
          <p className="font-medium text-sg-neutral-900 dark:text-sg-neutral-50">
            {sessionRecovery.restored ? 'Session partly restored' : 'Session could not be restored'}
          </p>
          <p className="text-sg-neutral-700 dark:text-sg-neutral-300">{sessionRecovery.message}</p>
        </div>
        <button
          type="button"
          onClick={acknowledgeRecovery}
          aria-label="Dismiss session recovery report"
          className="text-sg-neutral-400 hover:text-sg-neutral-600"
        >
          <X className="h-4 w-4" />
        </button>
      </div>
    );
  }

  // The one-line status stands on its own when continuity itself is clean: an
  // unmeasured free-space reading or an unresolved authority document is worth
  // saying even on a start that needed no repair.
  const cleanStart = report?.available && !report.repaired && !report.failure;
  if ((!report || cleanStart) && line && !line.silent) {
    return <StatusLine line={line} onDismiss={() => setDismissed(true)} />;
  }

  if (!report) return null;

  // A clean start with nothing else to report is not worth the attention.
  if (cleanStart) return null;

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
        {/* The same one line, alongside the continuity detail rather than
            instead of it: the operator deciding what to do about a restored
            run also needs to know whether it can write and under what
            authority. */}
        {line && (
          <p className="mt-1 text-xs text-sg-neutral-600 dark:text-sg-neutral-400">{line.text}</p>
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

/**
 * The one-line ribbon on its own.
 *
 * Rendered when continuity is clean but free space or authority is not — the
 * case `SUP.UX.RECOVERY-RIBBON` exists for and the one a crash-only dialog
 * never covers. `role` follows the state so an `attention` line interrupts a
 * screen reader and an `unknown` one does not.
 */
function StatusLine({ line, onDismiss }: { line: RibbonLine; onDismiss: () => void }) {
  const attention = line.state === 'attention';
  return (
    <div
      className={
        attention
          ? 'mx-4 mt-4 flex items-start gap-3 rounded-lg border border-sg-amber-200 bg-sg-amber-50 p-3 dark:border-sg-amber-800 dark:bg-sg-amber-900/10'
          : 'mx-4 mt-4 flex items-start gap-3 rounded-lg border border-sg-neutral-200 bg-sg-neutral-50 p-3 dark:border-sg-neutral-800 dark:bg-sg-neutral-900'
      }
      role={attention ? 'alert' : 'status'}
    >
      {attention ? (
        <AlertTriangle className="mt-0.5 h-4 w-4 flex-shrink-0 text-sg-amber-600" aria-hidden="true" />
      ) : (
        <RotateCcw className="mt-0.5 h-4 w-4 flex-shrink-0 text-sg-neutral-500" aria-hidden="true" />
      )}
      <p className="flex-1 text-sm text-sg-neutral-800 dark:text-sg-neutral-200">{line.text}</p>
      <button
        type="button"
        onClick={onDismiss}
        aria-label="Dismiss status line"
        className="text-sg-neutral-400 hover:text-sg-neutral-600"
      >
        <X className="h-4 w-4" />
      </button>
    </div>
  );
}
