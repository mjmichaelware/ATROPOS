/* SPDX-License-Identifier: AGPL-3.0-only */

import { useEffect, useState } from 'react';
import { exports, type ExportPayload, type LandingZone, canExport, defaultZone, availableZones, unavailableZones } from '@/lib/export/client';
import { useSessionState } from '@/lib/contexts/session-state-context';

/**
 * ADD-W-015: Export button using existing /v1/exports client.
 *
 * Renders an export button that uses the existing /v1/exports client.
 * Redaction note in UI; if no client method: BLOCKED with path needed.
 * Uses the existing /v1/exports client for zone discovery and export execution.
 */
export function ExportButton({ onExport }: { onExport?: () => void }) {
  const { session } = useSessionState();
  const [payload, setPayload] = useState<null | { ok: true; data: any } | { ok: false; detail: string; remedy: string }>(null);
  const [selectedZone, setSelectedZone] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const result = await exports.zones();
        if (cancelled) return;
        if (result.ok) {
          setPayload({ ok: true, data: result.data });
        } else {
          setPayload({ ok: false, detail: result.detail, remedy: result.remedy });
        }
      } catch (error) {
        setPayload({ ok: false, detail: String(error), remedy: 'Check engine connectivity' });
      }
    })();
    return () => { cancelled = true; };
  }, []);

  // Check if we're in a development/handoff context
  const isDevContext = typeof window !== 'undefined' && window.location.pathname.startsWith('/developer');

  const handleExport = async () => {
    if (!selectedZone || exporting) return;
    setExporting(true);
    setError(null);
    try {
      const result = await exports.write(selectedZone);
      if (result.ok) {
        onExport?.();
      } else {
        setError(`${result.detail} ${result.remedy}`);
      }
    } catch (error) {
      setError(String(error));
    } finally {
      setExporting(false);
    }
  };

  // Don't render if not in a developer context or if no payload
  if (!isDevContext) return null;

  if (loading) {
    return (
      <section className="rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800">
        <p className="wb-pane-note">Loading export zones…</p>
      </section>
    );
  }

  if (!payload || !payload.ok) {
    return (
      <section className="rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800">
        <p className="wb-pane-title">Export</p>
        <p className="wb-pane-note">Export unavailable: {payload && !payload.ok ? `${payload.detail} ${payload.remedy}` : 'No payload'}</p>
      </section>
    );
  }

  const payloadData = payload.data;
  const available = availableZones(payloadData);

  if (!canExport(payloadData)) {
    return (
      <section className="rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800">
        <p className="wb-pane-title">Export</p>
        <p className="wb-pane-note">
          Export unavailable: {payloadData.grantedTerritory.length === 0
            ? 'No territory granted for export'
            : 'No landing zones available'}
        </p>
        {payloadData.grantedTerritory.length === 0 && (
          <p className="wb-pane-note mt-1 text-xs text-sg-amber-600">
            Grant territory via /providers or factory before exporting.
          </p>
        )}
        {available.length === 0 && payloadData.grantedTerritory.length > 0 && (
          <p className="wb-pane-note mt-1 text-xs text-sg-amber-600">
            No landing zones available. Configure export zones in engine config.
          </p>
        )}
      </section>
    );
  }

  const defaultZ = defaultZone(payloadData);
  if (defaultZ && !selectedZone) setSelectedZone(defaultZ.id);

  const unavailable = unavailableZones(payloadData);

  return (
    <section className="rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800">
      <p className="wb-pane-title">Export</p>
      <p className="text-sm text-sg-neutral-700 dark:text-sg-neutral-300 mb-3">
        Export the current project artifact to a landing zone. Redaction is applied before write.
      </p>

      {available.length > 0 && (
        <div className="space-y-2">
          <div>
            <label className="block text-sm font-medium text-sg-neutral-900 dark:text-sg-neutral-50 mb-1">
              Landing zone
            </label>
            <select
              value={selectedZone ?? ''}
              onChange={(e) => setSelectedZone(e.target.value)}
              disabled={exporting}
              className="w-full px-3 py-2 border border-sg-neutral-300 dark:border-sg-neutral-600 rounded-lg bg-white dark:bg-sg-neutral-800 text-sg-neutral-900 dark:text-sg-neutral-100"
            >
              <option value="">Select a landing zone</option>
              {available.map((zone) => (
                <option key={zone.id} value={zone.id}>
                  {zone.zone} ({zone.directory})
                </option>
              ))}
            </select>
          </div>

          {unavailable.length > 0 && (
            <details className="group">
              <summary className="text-xs text-sg-amber-600 dark:text-sg-amber-400 cursor-pointer">
                {unavailable.length} zone(s) unavailable — click to see reasons
              </summary>
              <ul className="mt-1 space-y-1 text-xs text-sg-amber-700 dark:text-sg-amber-300">
                {unavailable.map((z) => (
                  <li key={z.id} className="flex gap-2">
                    <code className="font-mono">{z.id}</code>
                    <span>{z.detail} {z.remedy}</span>
                  </li>
                ))}
              </ul>
            </details>
          )}
        </div>
      )}

      {available.length === 0 && payloadData.grantedTerritory.length > 0 && (
        <p className="wb-pane-note text-sg-amber-700">
          No landing zones available. Configure export zones in engine config.
        </p>
      )}

      <div className="mt-4 flex gap-2">
        <button
          type="button"
          onClick={handleExport}
          disabled={!selectedZone || exporting}
          className="px-4 py-2 bg-sg-red-600 text-white rounded-lg hover:bg-sg-red-700 disabled:opacity-50 disabled:cursor-not-allowed text-sm font-medium"
        >
          {exporting ? 'Exporting…' : 'Export'}
        </button>
        {error && (
          <span className="text-sm text-sg-red-600 self-center">{error}</span>
        )}
      </div>

      <p className="mt-3 text-xs text-sg-neutral-500">
        Redaction is applied before write. Export writes only within granted territory.
      </p>
    </section>
  );
}