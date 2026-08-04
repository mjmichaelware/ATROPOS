/* SPDX-License-Identifier: AGPL-3.0-only */
import { describe, expect, it } from 'vitest';
import {
  DELTA_REGISTER,
  deltasForSurface,
  outstandingGaps,
  registerFindings,
  type Delta,
} from './delta-register';

const base: Delta = {
  id: 'x',
  capability: 'c',
  kind: 'expressed-differently',
  parityOn: ['cli'],
  divergesOn: ['web'],
  reason: 'because',
  owner: null,
};

describe('the register holds itself to its own rule', () => {
  it('the shipped register has no findings', () => {
    expect(registerFindings()).toEqual([]);
  });

  it('the shipped register is not empty', () => {
    // A register that passes because it records nothing is not a register.
    expect(DELTA_REGISTER.length).toBeGreaterThan(0);
  });

  it('an unjustified delta is a finding', () => {
    expect(registerFindings([{ ...base, reason: '   ' }])[0].detail).toMatch(/No reason/);
  });

  it('a delta that names no diverging surface is a finding', () => {
    expect(registerFindings([{ ...base, divergesOn: [] }])[0].detail).toMatch(/no diverging/);
  });

  it('a surface cannot be both at parity and diverging', () => {
    const findings = registerFindings([{ ...base, parityOn: ['web'], divergesOn: ['web'] }]);
    expect(findings.some((f) => f.detail.includes('both at parity'))).toBe(true);
  });
});

describe('gaps are kept distinct from impossibilities', () => {
  it('only not-yet-built entries count as outstanding work', () => {
    const gaps = outstandingGaps();
    expect(gaps.every((delta) => delta.kind === 'not-yet-built')).toBe(true);
    // A platform impossibility must never appear as a backlog item.
    expect(gaps.some((delta) => delta.id === 'pointer-target-size')).toBe(false);
  });

  it('the known gap is recorded rather than hidden', () => {
    expect(outstandingGaps().map((d) => d.id)).toContain('governance-ledger-writer');
  });
});

describe('per-surface view', () => {
  it('lists what diverges on the web surface', () => {
    const ids = deltasForSurface('web').map((d) => d.id);
    expect(ids).toContain('specgraph-tenancy');
    expect(ids).toContain('bridge-write-surface');
  });

  it('does not list a delta the surface is at parity on', () => {
    expect(deltasForSurface('cli').map((d) => d.id)).not.toContain('six-answers-push');
  });
});
