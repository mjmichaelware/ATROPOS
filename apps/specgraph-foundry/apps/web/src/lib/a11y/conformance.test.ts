/* SPDX-License-Identifier: AGPL-3.0-only */
import { describe, expect, it } from 'vitest';
import {
  AA_LARGE_TEXT,
  AA_NORMAL_TEXT,
  colourOnlyStates,
  contrastRatio,
  meetsContrast,
  undersizedTargets,
  verdict,
} from './conformance';
import { STATUS_DEFINITIONS } from '@/lib/status-system';

describe('WCAG contrast arithmetic', () => {
  it('black on white is the maximum ratio', () => {
    expect(contrastRatio('#000000', '#ffffff')).toBeCloseTo(21, 1);
  });

  it('a colour against itself has no contrast', () => {
    expect(contrastRatio('#4a90d9', '#4a90d9')).toBeCloseTo(1, 5);
  });

  it('accepts shorthand hex', () => {
    expect(contrastRatio('#000', '#fff')).toBeCloseTo(21, 1);
  });

  it('an unparseable colour is unknown, not perfect', () => {
    // A default of 21 would let a typo report as ideal contrast.
    expect(contrastRatio('not-a-colour', '#fff')).toBeNull();
    expect(meetsContrast('rgb(0,0,0)', '#fff')).toBe('unknown');
  });

  it('applies the AA floors', () => {
    // #767676 on white is the canonical AA boundary for normal text.
    expect(meetsContrast('#767676', '#ffffff', AA_NORMAL_TEXT)).toBe('pass');
    expect(meetsContrast('#949494', '#ffffff', AA_NORMAL_TEXT)).toBe('fail');
    expect(meetsContrast('#949494', '#ffffff', AA_LARGE_TEXT)).toBe('pass');
  });
});

describe('Source Doc 3 §E — colour is never the only channel', () => {
  it('flags a state carrying meaning in colour alone', () => {
    const findings = colourOnlyStates([{ id: 'blocked', label: '', icon: null }]);
    expect(findings).toHaveLength(1);
    expect(findings[0].detail).toMatch(/colour alone/);
  });

  it('a label alone is enough', () => {
    expect(colourOnlyStates([{ id: 'blocked', label: 'Blocked' }])).toEqual([]);
  });

  it('an icon alone is enough', () => {
    expect(colourOnlyStates([{ id: 'blocked', icon: 'AlertTriangle' }])).toEqual([]);
  });

  it('every canonical status carries a label and an icon', () => {
    // The real table, not a fixture: this is the assertion that would fail if
    // someone shipped a colour-only status badge.
    const states = Object.values(STATUS_DEFINITIONS).map((definition) => ({
      id: definition.status,
      label: definition.label,
      icon: definition.icon,
    }));
    expect(colourOnlyStates(states)).toEqual([]);
    expect(states.length).toBeGreaterThan(0);
  });
});

describe('WCAG 2.2 §2.5.8 target size', () => {
  it('measures the smaller side', () => {
    const findings = undersizedTargets([{ id: 'close', width: 44, height: 16 }]);
    expect(findings).toHaveLength(1);
    expect(findings[0].detail).toContain('44×16px');
  });

  it('passes a target at the floor', () => {
    expect(undersizedTargets([{ id: 'ok', width: 24, height: 24 }])).toEqual([]);
  });
});

describe('the verdict cannot pass vacuously', () => {
  it('checking nothing is not conformance', () => {
    const result = verdict(0, []);
    expect(result.conformant).toBe(false);
    expect(result.reason).toMatch(/Nothing was checked/);
  });

  it('checked items with no findings conform', () => {
    expect(verdict(9, []).conformant).toBe(true);
  });

  it('any finding fails the verdict', () => {
    const result = verdict(9, [{ id: 'x', rule: 'r', detail: 'd' }]);
    expect(result.conformant).toBe(false);
    expect(result.findings).toHaveLength(1);
  });
});
