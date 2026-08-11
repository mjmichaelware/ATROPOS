/* SPDX-License-Identifier: AGPL-3.0-only */

import { readEngine, type EngineFailure } from '@/lib/engine/client';

/**
 * Reads the landing zones an export may actually use.
 *
 * `SUP.ART.ROOT-OR-DOWNLOADS` gives the operator that choice, and
 * `SUP.ART.HANDOFF-EXPORT` bounds it: an export leaves through a
 * territory-bounded channel or it does not leave. Both decisions are the
 * engine's; this client's contribution is to keep the surface from making them
 * again with a hard-coded list of directories.
 *
 * An unavailable zone arrives with its reason attached and is kept, not
 * filtered. Dropping it would teach the operator that this platform simply has
 * one export location, which is a different and untrue statement.
 */

export interface AvailableZone {
  id: string;
  available: true;
  directory: string;
  zone: string;
}

export interface UnavailableZone {
  id: string;
  available: false;
  detail: string;
  remedy: string;
}

export type LandingZone = AvailableZone | UnavailableZone;

export interface ExportPayload {
  ok: true;
  grantedTerritory: string[];
  zones: LandingZone[];
}

export type ExportResult =
  | { ok: true; data: ExportPayload }
  | ({ ok: false } & Omit<EngineFailure, 'ok'>);

export const exports = {
  zones: (): Promise<ExportResult> => readEngine<ExportPayload>('/v1/exports'),
};

export function availableZones(payload: ExportPayload): AvailableZone[] {
  return payload.zones.filter((zone): zone is AvailableZone => zone.available);
}

export function unavailableZones(payload: ExportPayload): UnavailableZone[] {
  return payload.zones.filter((zone): zone is UnavailableZone => !zone.available);
}

/**
 * The zone a picker may pre-select.
 *
 * Null when nothing is available, so the surface renders "no landing zone"
 * rather than an export button bound to nowhere. There is no fallback to the
 * working directory: a surprise write into whatever directory the process
 * happens to be in is precisely what this atom removes.
 */
export function defaultZone(payload: ExportPayload): AvailableZone | null {
  return availableZones(payload)[0] ?? null;
}

/**
 * Whether an export can be offered at all.
 *
 * Requires both a granted territory and somewhere to land. Territory alone is
 * not enough, and neither is a resolvable directory — an export outside a grant
 * is the leak `SUP.ART.HANDOFF-EXPORT` exists to prevent.
 */
export function canExport(payload: ExportPayload): boolean {
  return payload.grantedTerritory.length > 0 && availableZones(payload).length > 0;
}
