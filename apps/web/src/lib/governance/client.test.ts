/* SPDX-License-Identifier: AGPL-3.0-only */
import { describe, expect, it } from 'vitest';
import { formatBytes, formatRate } from './client';

describe('governance formatting', () => {
  it('an unmeasured rate says so rather than showing zero', () => {
    expect(formatRate(null)).toBe('not measured');
    expect(formatRate(null)).not.toContain('0');
  });

  it('a measured rate is shown as a percentage', () => {
    expect(formatRate(0.02)).toBe('2.0%');
    expect(formatRate(0)).toBe('0.0%');
  });

  it('a real zero is distinguishable from an unmeasured value', () => {
    // The distinction P20-S04 depends on: 0% observed is a result,
    // 0% from no observations is not.
    expect(formatRate(0)).not.toBe(formatRate(null));
  });

  it('the unmeasured word survives being read aloud', () => {
    // A dash and 0% are equally silent to a screen reader.
    expect(formatRate(null)).toMatch(/[a-z]/i);
  });

  it('formats bytes across units', () => {
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(2048)).toBe('2.0 KB');
    expect(formatBytes(5 * 1024 * 1024)).toBe('5.0 MB');
  });
});
