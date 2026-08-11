/* SPDX-License-Identifier: AGPL-3.0-only */
import { describe, expect, it } from 'vitest';
import { STATUS_TERMS } from '@atropos/web-contracts';
import { accentForStatus, materialFor, territoryStanding } from './territory-material';

describe('HOE-E01 territory as material', () => {
  it('a path inside the grant holds accent', () => {
    expect(territoryStanding('src/main/a.kt', ['src/main'])).toBe('in-territory');
    expect(materialFor('in-territory', 'attested').emphasis).toBe('accent');
  });

  it('a path outside the grant recedes and says why', () => {
    expect(territoryStanding('src/other/a.kt', ['src/main'])).toBe('out-of-territory');
    const tokens = materialFor('out-of-territory', 'attested');
    expect(tokens.emphasis).toBe('receded');
    // §E: never colour alone.
    expect(tokens.note).toMatch(/Outside the granted territory/);
  });

  it('an empty grant is unknown, never in-territory', () => {
    expect(territoryStanding('anything', [])).toBe('unknown');
    expect(materialFor('unknown', 'attested').emphasis).toBe('receded');
    expect(materialFor('unknown', 'attested').note).toMatch(/not cleared/);
  });

  it('a path that merely shares a prefix string is outside', () => {
    expect(territoryStanding('src/mainline/a.kt', ['src/main'])).toBe('out-of-territory');
  });

  it('territory outranks attestation — a well-attested stray still recedes', () => {
    const stray = materialFor('out-of-territory', 'attested');
    const inside = materialFor('in-territory', 'unattested');
    expect(stray.emphasis).toBe('receded');
    expect(inside.emphasis).not.toBe('receded');
  });
});

describe('HOE-E02 attestation as optical focus', () => {
  it('a valid envelope sharpens', () => {
    expect(materialFor('in-territory', 'attested').focus).toBe('sharp');
  });

  it('drift softens and states itself without a number', () => {
    const tokens = materialFor('in-territory', 'drifted');
    expect(tokens.focus).toBe('soft');
    expect(tokens.note).toMatch(/drifted/);
    expect(tokens.note).not.toMatch(/\d/);
  });

  it('unattested is neither sharpened nor punished', () => {
    expect(materialFor('in-territory', 'unattested').focus).toBe('normal');
  });
});

describe('HOE-E07 retheme from the status vocabulary only', () => {
  it('every canonical status term maps to an accent', () => {
    STATUS_TERMS.forEach((term) => {
      expect(accentForStatus(term)).not.toBe('neutral');
    });
  });

  it('the working states are distinguishable from the finished ones', () => {
    expect(accentForStatus('working')).not.toBe(accentForStatus('completed'));
    expect(accentForStatus('blocked')).not.toBe(accentForStatus('working'));
  });

  it('an unknown status does not retheme', () => {
    // A surface that changed appearance for a reason it could not explain
    // would be decorative, which the atom forbids.
    expect(accentForStatus('sparkly')).toBe('neutral');
    expect(accentForStatus('')).toBe('neutral');
  });
});
