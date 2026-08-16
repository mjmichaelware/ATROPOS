import { describe, expect, it } from 'vitest';
import { boundedVisibleRows, isWithinUpdateBudget, PERFORMANCE_REQUIREMENTS } from './rendering-contract';

describe('performance rendering contract', () => {
  it('bounds long lists instead of rendering unbounded rows', () => {
    const rows = Array.from({ length: PERFORMANCE_REQUIREMENTS.maxVisibleRows + 20 }, (_, i) => i);
    expect(boundedVisibleRows(rows)).toHaveLength(PERFORMANCE_REQUIREMENTS.maxVisibleRows);
    expect(boundedVisibleRows(rows, 3)).toEqual([0, 1, 2]);
  });

  it('rejects invalid or over-budget background updates', () => {
    expect(isWithinUpdateBudget(10)).toBe(true);
    expect(isWithinUpdateBudget(51)).toBe(false);
    expect(isWithinUpdateBudget(-1)).toBe(false);
    expect(isWithinUpdateBudget(Number.NaN)).toBe(false);
  });
});
