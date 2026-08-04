/* SPDX-License-Identifier: AGPL-3.0-only */
import { describe, expect, it } from 'vitest';
import {
  availableZones,
  canExport,
  defaultZone,
  unavailableZones,
  type ExportPayload,
} from './client';

const repoZone = {
  id: 'repository',
  available: true as const,
  directory: '/workspace/p/.atropos/exports',
  zone: 'repository',
};

const noDownloads = {
  id: 'downloads',
  available: false as const,
  detail: 'This platform exposes no downloads directory.',
  remedy: 'Choose an explicit landing path, or export to the repository root.',
};

const payload = (
  zones: ExportPayload['zones'],
  grantedTerritory: string[] = ['/workspace/p']
): ExportPayload => ({ ok: true, grantedTerritory, zones });

describe('SUP.ART.ROOT-OR-DOWNLOADS', () => {
  it('separates usable zones from refused ones', () => {
    const p = payload([repoZone, noDownloads]);
    expect(availableZones(p).map((z) => z.id)).toEqual(['repository']);
    expect(unavailableZones(p).map((z) => z.id)).toEqual(['downloads']);
  });

  it('keeps the refusal reason so the operator learns why', () => {
    expect(unavailableZones(payload([repoZone, noDownloads]))[0].detail).toMatch(/no downloads/);
  });

  it('pre-selects an available zone', () => {
    expect(defaultZone(payload([repoZone, noDownloads]))?.id).toBe('repository');
  });

  it('never falls back to somewhere unresolved', () => {
    // No available zone means no default. A surface that invented one would
    // write into whichever directory the process happened to be in.
    expect(defaultZone(payload([noDownloads]))).toBeNull();
  });
});

describe('SUP.ART.HANDOFF-EXPORT territory bound', () => {
  it('refuses to offer an export with no granted territory', () => {
    expect(canExport(payload([repoZone], []))).toBe(false);
  });

  it('refuses to offer an export with nowhere to land', () => {
    expect(canExport(payload([noDownloads]))).toBe(false);
  });

  it('offers the export only when both hold', () => {
    expect(canExport(payload([repoZone]))).toBe(true);
  });
});
