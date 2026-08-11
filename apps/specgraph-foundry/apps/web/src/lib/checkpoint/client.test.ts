/* SPDX-License-Identifier: AGPL-3.0-only */
import { describe, expect, it } from 'vitest';
import { formatAge, primaryOf, secondaryOf, type CheckpointAction } from './client';

const actions: CheckpointAction[] = [
  { id: 'resume', label: 'Resume', primary: true },
  { id: 'inspect', label: 'Inspect why this cannot resume', primary: false },
];

describe('HOE-C04 checkpoint age', () => {
  it('reads as elapsed time rather than a timestamp', () => {
    expect(formatAge(0)).toBe('just now');
    expect(formatAge(12)).toBe('12m ago');
    expect(formatAge(90)).toBe('1h ago');
    expect(formatAge(60 * 49)).toBe('2d ago');
  });
});

describe('HOE-B04 the primary action is the engine’s decision', () => {
  it('takes the flagged action, not one matched by name', () => {
    const flipped: CheckpointAction[] = [
      { id: 'resume', label: 'Resume', primary: false },
      { id: 'inspect', label: 'Inspect', primary: true },
    ];
    expect(primaryOf(flipped)?.id).toBe('inspect');
  });

  it('finds the primary in the ordinary case', () => {
    expect(primaryOf(actions)?.id).toBe('resume');
  });

  it('returns null rather than defaulting when nothing is primary', () => {
    // A surface that defaulted here would invent a prominent action the
    // engine declined to offer.
    expect(primaryOf([{ id: 'inspect', label: 'Inspect', primary: false }])).toBeNull();
  });

  it('leaves every non-primary action as secondary', () => {
    expect(secondaryOf(actions).map((a) => a.id)).toEqual(['inspect']);
  });

  it('no action offered is a new run', () => {
    expect(actions.some((a) => /new|restart|start over/i.test(a.id + a.label))).toBe(false);
  });
});
