/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import { useEffect, useState } from 'react';
import {
  exports as exportsClient,
  availableZones,
  canExport,
  defaultZone,
  unavailableZones,
  type ExportPayload,
} from '@/lib/export/client';

/**
 * The artifact export panel.
 *
 * `SUP.ART.ROOT-OR-DOWNLOADS` and `SUP.ART.HANDOFF-EXPORT`. The operator picks
 * where the artifact lands from zones the engine resolved; zones it refused are
 * shown with their reason instead of being omitted, so the absence of Downloads
 * is explained rather than merely observed.
 *
 * The granted territory is displayed alongside the picker. An export is a
 * departure from the system, and the operator deciding to make one should be
 * able to see the bound it is happening inside without opening settings.
 */
export function ExportPanel() {
  const [payload, setPayload] = useState<ExportPayload | null>(null);
  const [failure, setFailure] = useState<{ detail: string; remedy: string } | null>(null);
  const [selected, setSelected] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const result = await exportsClient.zones();
      if (cancelled) return;
      if (result.ok) {
        setPayload(result.data);
        setSelected(defaultZone(result.data)?.id ?? null);
      } else {
        setFailure({ detail: result.detail, remedy: result.remedy });
      }
      setLoading(false);
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  if (loading) {
    return (
      <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">
        Resolving landing zones…
      </p>
    );
  }

  if (failure || !payload) {
    return (
      <div
        role="status"
        className="rounded-lg border border-sg-amber-300 bg-sg-amber-50 p-4 dark:border-sg-amber-900 dark:bg-sg-amber-900/20"
      >
        <p className="font-semibold text-sg-amber-900 dark:text-sg-amber-100">
          No landing zone could be resolved
        </p>
        <p className="text-sm text-sg-amber-800 dark:text-sg-amber-200">
          {failure?.detail} {failure?.remedy}
        </p>
      </div>
    );
  }

  const usable = availableZones(payload);
  const refused = unavailableZones(payload);

  return (
    <section aria-label="Export artifact" className="space-y-4">
      <div>
        <h2 className="text-lg font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
          Where this lands
        </h2>
        <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">
          Granted territory:{' '}
          <span className="font-mono">
            {payload.grantedTerritory.join(', ') || 'none — no export is permitted'}
          </span>
        </p>
      </div>

      {usable.length === 0 ? (
        <p className="text-sm text-sg-neutral-700 dark:text-sg-neutral-300">
          No landing zone is available on this platform. Nothing will be written to a default
          location.
        </p>
      ) : (
        <fieldset className="space-y-2">
          <legend className="sr-only">Landing zone</legend>
          {usable.map((zone) => (
            <label key={zone.id} className="flex items-baseline gap-2 text-sm">
              <input
                type="radio"
                name="landing-zone"
                value={zone.id}
                checked={selected === zone.id}
                onChange={() => setSelected(zone.id)}
              />
              <span className="text-sg-neutral-900 dark:text-sg-neutral-50">{zone.zone}</span>
              <span className="font-mono text-xs text-sg-neutral-600 dark:text-sg-neutral-400">
                {zone.directory}
              </span>
            </label>
          ))}
        </fieldset>
      )}

      {refused.length > 0 && (
        <ul className="space-y-1">
          {refused.map((zone) => (
            <li key={zone.id} className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">
              {/* Shown, not hidden: the operator asked about this zone by
                  knowing it exists, and silence would read as "unsupported". */}
              <span className="font-medium">{zone.id}</span> unavailable — {zone.detail}{' '}
              {zone.remedy}
            </li>
          ))}
        </ul>
      )}

      <button
        type="button"
        disabled={!canExport(payload) || selected === null}
        className="rounded-lg bg-sg-neutral-900 px-4 py-2 font-medium text-white disabled:opacity-50 dark:bg-sg-neutral-50 dark:text-sg-neutral-900"
      >
        Export to {usable.find((z) => z.id === selected)?.zone ?? 'nowhere available'}
      </button>
    </section>
  );
}
